package com.akun.xinkun.world;

import com.akun.xinkun.entity.HuTaoEntity;
import com.akun.xinkun.voice.VoiceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public final class HuTaoReturnManager {
    private static final long RETURN_TIMEOUT_TICKS = 24000L;

    private record Pending(ResourceKey<Level> dimension, Vec3 deathPos, List<ItemStack> items, long createdAt) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private HuTaoReturnManager() {
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }
        List<HuTaoEntity> helpers = level.getEntitiesOfClass(HuTaoEntity.class,
                player.getBoundingBox().inflate(48.0), LivingEntity::isAlive);
        if (helpers.isEmpty()) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(8.0),
                item -> item.isAlive() && item.tickCount <= 2 && item.getOwner() == null
                        && !item.getItem().isEmpty())) {
            ItemStack stack = itemEntity.getItem();
            items.add(stack.copy());
            itemEntity.discard();
        }
        if (items.isEmpty()) {
            return;
        }
        PENDING.merge(player.getUUID(),
                new Pending(level.dimension(), player.position(), items, level.getGameTime()),
                (oldValue, newValue) -> {
                    List<ItemStack> merged = new ArrayList<>(oldValue.items());
                    merged.addAll(newValue.items());
                    return new Pending(newValue.dimension(), newValue.deathPos(), merged, newValue.createdAt());
                });
        HuTaoEntity helper = nearestHelper(helpers, player);
        helper.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(), 1.3);
        helper.getLookControl().setLookAt(player, 30.0F, 30.0F);
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Pending>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Pending> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.isDeadOrDying()) {
                continue;
            }
            Pending pending = entry.getValue();
            long age = now - pending.createdAt();
            ServerLevel level = (ServerLevel) player.level();
            if (age > RETURN_TIMEOUT_TICKS) {
                // Never let a stranded helper keep the returned inventory in limbo.
                deliver(player, pending.items());
                iterator.remove();
                continue;
            }
            if (level.dimension() != pending.dimension()) {
                continue;
            }
            List<HuTaoEntity> helpers = level.getEntitiesOfClass(HuTaoEntity.class,
                    player.getBoundingBox().inflate(64.0), LivingEntity::isAlive);
            if (helpers.isEmpty()) {
                continue;
            }
            HuTaoEntity helper = nearestHelper(helpers, player);
            if (helper.distanceToSqr(player) > 36.0) {
                if (helper.tickCount % 40 == 0) {
                    helper.getNavigation().moveTo(player, 1.25);
                }
                continue;
            }
            deliver(player, pending.items());
            helper.speak(VoiceContext.RETURN);
            iterator.remove();
        }
    }

    /**
     * Best-effort handover during shutdown. Online players receive their items
     * directly; otherwise they are placed back where the player died so a saved
     * world never loses them together with the in-memory queue.
     */
    public static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive() && !player.isDeadOrDying()) {
                deliver(player, pending.items());
                continue;
            }
            ServerLevel level = server.getLevel(pending.dimension());
            if (level != null && level.isLoaded(BlockPos.containing(pending.deathPos()))) {
                dropAt(level, pending.deathPos(), pending.items());
            } else if (player != null && player.level() instanceof ServerLevel playerLevel) {
                dropAt(playerLevel, player.position(), pending.items());
            }
        }
        PENDING.clear();
    }

    private static HuTaoEntity nearestHelper(List<HuTaoEntity> helpers, ServerPlayer player) {
        HuTaoEntity nearest = helpers.get(0);
        for (HuTaoEntity candidate : helpers) {
            if (candidate.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static void deliver(ServerPlayer player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            ItemStack copy = stack.copy();
            player.getInventory().add(copy);
            if (!copy.isEmpty()) {
                player.drop(copy, false, Prediction.SERVER_ONLY);
            }
        }
    }

    private static void dropAt(ServerLevel level, Vec3 pos, List<ItemStack> items) {
        for (ItemStack stack : items) {
            ItemEntity entity = new ItemEntity(level, pos.x, pos.y + 0.25, pos.z, stack.copy());
            entity.setPickUpDelay(20);
            level.addFreshEntity(entity);
        }
    }
}
