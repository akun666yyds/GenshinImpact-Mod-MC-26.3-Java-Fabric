package com.akun.xinkun.building;

import com.akun.xinkun.XinkunGenshinMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 建筑道具的落成过程：按楼层从下往上逐层放置，每 tick 限量，
 * 保证哪怕是几万块的高楼也不会在同一 tick 里卡住服务器。
 */
public final class BuildingPlacementManager {
    private static final int BLOCKS_PER_TICK = 900;
    private static final int START_DELAY = 4;
    private static final List<Job> JOBS = new ArrayList<>();

    private BuildingPlacementManager() {
    }

    public static void start(ServerLevel level, BuildingType type, BuildBlueprint blueprint,
                             BlockPos origin, int turns, Player player) {
        JOBS.add(new Job(level, type, blueprint, origin, turns, player.getUUID()));
        player.sendOverlayMessage(Component.translatable(
                "item.xinkun_genshin.building.started", Component.translatable(type.nameKey()))
                .withStyle(ChatFormatting.AQUA));
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        Iterator<Job> iterator = JOBS.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (job.tick()) {
                iterator.remove();
            }
        }
    }

    /** 服务器关闭前把未完成的建筑一次补齐，避免存档里留下半栋楼。 */
    public static void flush(MinecraftServer server) {
        for (Job job : JOBS) {
            job.finishNow();
        }
        JOBS.clear();
    }

    private static final class Job {
        private final ServerLevel level;
        private final BuildingType type;
        private final int turns;
        private final java.util.UUID owner;
        private final BlockPos origin;
        private final List<List<Placement>> layers = new ArrayList<>();
        private final List<Placement> fluids = new ArrayList<>();
        private final List<List<Placement>> allLayers = new ArrayList<>();
        private int layerIndex;
        private int blockIndex;
        private int fluidIndex;
        private int repairLayer;
        private int repairIndex;
        private int repairFluidIndex;
        private boolean repairing;
        private int delay = START_DELAY;
        private int placed;
        private int lastSoundLayer = -3;

        private Job(ServerLevel level, BuildingType type, BuildBlueprint blueprint,
                    BlockPos origin, int turns, java.util.UUID owner) {
            this.level = level;
            this.type = type;
            this.turns = turns;
            this.owner = owner;
            this.origin = origin;
            Rotation rotation = rotation(this.turns);
            for (Map.Entry<BlockPos, BlockState> entry : blueprint.sorted()) {
                BlockPos local = entry.getKey();
                BlockState state = entry.getValue().rotate(rotation);
                Placement placement = new Placement(this.world(local, blueprint), state);
                if (!state.getFluidState().isEmpty()) {
                    this.fluids.add(placement);
                    continue;
                }
                int y = local.getY();
                while (this.layers.size() <= y) {
                    this.layers.add(new ArrayList<>());
                }
                this.layers.get(y).add(placement);
            }
            // 修复阶段按同样的自下而上顺序再看一遍，此时四周方块都已就位，
            // 火把、藤蔓、栅栏这类需要依附的装饰就不会在建造途中掉下来。
            this.allLayers.addAll(this.layers);
        }

        private static Rotation rotation(int turns) {
            return switch (turns & 3) {
                case 1 -> Rotation.CLOCKWISE_90;
                case 2 -> Rotation.CLOCKWISE_180;
                case 3 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
        }

        private BlockPos world(BlockPos local, BuildBlueprint blueprint) {
            int x = local.getX();
            int z = local.getZ();
            int wx;
            int wz;
            switch (this.turns & 3) {
                case 1 -> {
                    wx = blueprint.depth() - 1 - z;
                    wz = x;
                }
                case 2 -> {
                    wx = blueprint.width() - 1 - x;
                    wz = blueprint.depth() - 1 - z;
                }
                case 3 -> {
                    wx = z;
                    wz = blueprint.width() - 1 - x;
                }
                default -> {
                    wx = x;
                    wz = z;
                }
            }
            return new BlockPos(
                    this.origin.getX() + wx,
                    this.origin.getY() + local.getY(),
                    this.origin.getZ() + wz);
        }

        private boolean tick() {
            if (this.delay > 0) {
                this.delay--;
                return false;
            }
            int budget = BLOCKS_PER_TICK;
            while (budget > 0 && this.layerIndex < this.layers.size()) {
                List<Placement> layer = this.layers.get(this.layerIndex);
                while (budget > 0 && this.blockIndex < layer.size()) {
                    this.placeRaw(layer.get(this.blockIndex++));
                    budget--;
                }
                if (this.blockIndex >= layer.size()) {
                    this.layerDone(this.layerIndex);
                    this.layerIndex++;
                    this.blockIndex = 0;
                }
            }
            if (!this.repairing && this.layerIndex >= this.layers.size()) {
                while (budget > 0 && this.fluidIndex < this.fluids.size()) {
                    this.placeRaw(this.fluids.get(this.fluidIndex++));
                    budget--;
                }
                if (this.fluidIndex >= this.fluids.size()) {
                    this.repairing = true;
                }
            }
            if (this.repairing) {
                while (budget > 0 && this.repairLayer < this.allLayers.size()) {
                    List<Placement> layer = this.allLayers.get(this.repairLayer);
                    while (budget > 0 && this.repairIndex < layer.size()) {
                        this.place(layer.get(this.repairIndex++));
                        budget--;
                    }
                    if (this.repairIndex >= layer.size()) {
                        this.repairLayer++;
                        this.repairIndex = 0;
                    }
                }
                while (budget > 0 && this.repairFluidIndex < this.fluids.size()) {
                    this.place(this.fluids.get(this.repairFluidIndex++));
                    budget--;
                }
                if (this.repairLayer >= this.allLayers.size()
                        && this.repairFluidIndex >= this.fluids.size()) {
                    this.complete();
                    return true;
                }
            }
            return false;
        }

        private void layerDone(int layer) {
            if (layer - this.lastSoundLayer < 3) {
                return;
            }
            this.lastSoundLayer = layer;
            float pitch = 0.7F + Math.min(0.6F, layer * 0.02F);
            this.level.playSound(null, this.origin.getX() + 0.5, this.origin.getY() + layer,
                    this.origin.getZ() + 0.5,
                    SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.9F, pitch);
            this.level.sendParticles(ParticleTypes.CLOUD,
                    this.origin.getX() + 0.5, this.origin.getY() + layer + 0.2,
                    this.origin.getZ() + 0.5,
                    8, 2.0, 0.15, 2.0, 0.01);
        }

        /** 第一遍只写方块与客户端，不惊动邻居，避免依附类装饰提前掉落。 */
        private void placeRaw(Placement placement) {
            if (this.level.isOutsideBuildHeight(placement.pos())) {
                return;
            }
            if (!this.level.setBlock(placement.pos(), placement.state(), 2)) {
                return;
            }
            this.placed++;
            this.liftEntities(placement.pos(), placement.state());
        }

        /** 修复阶段：四周已就位，再以完整更新写一次，让依附与流体表现自然。 */
        private void place(Placement placement) {
            if (this.level.isOutsideBuildHeight(placement.pos())) {
                return;
            }
            this.level.setBlockAndUpdate(placement.pos(), placement.state());
            this.liftEntities(placement.pos(), placement.state());
        }

        /**
         * 强制施工：方块照常落下，但把正好被压在方块里的生物顶到方块上方，
         * 避免玩家或角色被新墙活埋。这里从不跳过方块，也不检查建筑范围内有谁。
         */
        private void liftEntities(BlockPos pos, BlockState state) {
            if (state.getCollisionShape(this.level, pos).isEmpty()) {
                return;
            }
            AABB block = new AABB(pos);
            for (Entity entity : this.level.getEntitiesOfClass(Entity.class, block,
                    e -> e.isAlive() && !e.isSpectator())) {
                double top = pos.getY() + 1.0D;
                if (entity.getY() < top) {
                    entity.teleportTo(entity.getX(), top, entity.getZ());
                }
            }
        }

        private void complete() {
            MinecraftServer server = this.level.getServer();
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(this.owner);
            if (player != null) {
                player.sendOverlayMessage(Component.translatable(
                        "item.xinkun_genshin.building.finished", Component.translatable(this.type.nameKey()))
                        .withStyle(ChatFormatting.AQUA));
            }
            this.level.playSound(null, this.origin.getX() + 0.5, this.origin.getY() + 1.0,
                    this.origin.getZ() + 0.5,
                    SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.45F, 1.4F);
            XinkunGenshinMod.LOGGER.debug("Placed building {} ({} blocks)", this.type.id(), this.placed);
        }

        private void finishNow() {
            for (int i = this.layerIndex; i < this.layers.size(); i++) {
                List<Placement> layer = this.layers.get(i);
                int start = i == this.layerIndex ? this.blockIndex : 0;
                for (int j = start; j < layer.size(); j++) {
                    this.placeRaw(layer.get(j));
                }
            }
            for (int i = this.fluidIndex; i < this.fluids.size(); i++) {
                this.placeRaw(this.fluids.get(i));
            }
            for (List<Placement> layer : this.allLayers) {
                for (Placement placement : layer) {
                    this.place(placement);
                }
            }
            for (Placement fluid : this.fluids) {
                this.place(fluid);
            }
        }
    }

    private record Placement(BlockPos pos, BlockState state) {
    }
}
