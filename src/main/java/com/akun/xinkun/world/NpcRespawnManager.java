package com.akun.xinkun.world;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.entity.GenshinNpcEntity;
import com.akun.xinkun.fx.NpcFx;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers characters that died so they always come back, even if nobody is
 * near their home when the respawn timer expires or the server restarts in
 * between. Pending respawns are world save data, not a static list: the old
 * in-memory queue silently lost every pending character on shutdown.
 */
public final class NpcRespawnManager extends SavedData {
    private record Pending(String dimension, String type, String id, long pos, long due) {
        private static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Pending::dimension),
                Codec.STRING.fieldOf("type").forGetter(Pending::type),
                Codec.STRING.optionalFieldOf("id", "").forGetter(Pending::id),
                Codec.LONG.fieldOf("pos").forGetter(Pending::pos),
                Codec.LONG.fieldOf("due").forGetter(Pending::due)
        ).apply(instance, Pending::new));

        /** Pending entries from older saves carry no id; fall back to home + type. */
        private boolean isSameCharacter(Pending other) {
            if (!this.id.isEmpty() && !other.id.isEmpty()) {
                return this.id.equals(other.id);
            }
            return this.dimension.equals(other.dimension) && this.type.equals(other.type)
                    && this.pos == other.pos;
        }
    }

    private static final Codec<NpcRespawnManager> CODEC = Pending.CODEC.listOf()
            .xmap(NpcRespawnManager::new, data -> List.copyOf(data.pending));

    public static final SavedDataType<NpcRespawnManager> TYPE = new SavedDataType<>(
            XinkunGenshinMod.id("npc_respawns"),
            NpcRespawnManager::new,
            CODEC,
            DataFixTypes.SAVED_DATA_RAIDS);

    private final List<Pending> pending;

    private NpcRespawnManager() {
        this.pending = new ArrayList<>();
    }

    private NpcRespawnManager(List<Pending> pending) {
        this.pending = new ArrayList<>(pending);
    }

    public static NpcRespawnManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    @SuppressWarnings("unchecked")
    public static void schedule(GenshinNpcEntity npc) {
        if (npc.isRespawnScheduled()) {
            return;
        }
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        npc.markRespawnScheduled();
        NpcRespawnManager data = get(server);
        String dimension = level.dimension().identifier().toString();
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()).toString();
        // The player can choose a new home with a shift-click; the character
        // must come back to that doorstep, not to an outdated spawn point.
        BlockPos home = npc.getHomePoint();
        long due = level.getGameTime() + 100L;
        Pending entry = new Pending(dimension, type, npc.getUUID().toString(), home.asLong(), due);
        for (Pending existing : data.pending) {
            if (existing.isSameCharacter(entry)) {
                return;
            }
        }
        data.pending.add(entry);
        data.setDirty();
        XinkunGenshinMod.LOGGER.info("Scheduled respawn: {} at {} in {}", type, home, level.dimension().identifier());
    }

    public static void tick(MinecraftServer server) {
        get(server).tickPending(server);
    }

    @SuppressWarnings("unchecked")
    private void tickPending(MinecraftServer server) {
        if (this.pending.isEmpty()) {
            return;
        }
        boolean changed = false;
        Iterator<Pending> iterator = this.pending.iterator();
        while (iterator.hasNext()) {
            Pending entry = iterator.next();
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(entry.dimension()));
            ServerLevel level = server.getLevel(dimensionKey);
            if (level == null) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (level.getGameTime() < entry.due()) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.pos());
            // Do not force the plot: a respawn simply waits until the home
            // chunk is loaded again, then happens on the first tick after.
            if (!level.isLoaded(pos)) {
                continue;
            }
            EntityType<?> raw = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(entry.type()));
            if (raw == null) {
                iterator.remove();
                changed = true;
                continue;
            }
            // 存档里可能留着已删除/改名的条目（例如测试用的临时角色类型）。
            // 先确认这个类型真的能造出角色实体，避免把猪之类的原版生物强行
            // 当成角色强转，导致整个服务端在加载存档时崩溃。
            Entity probe = raw.create(level, EntitySpawnReason.EVENT);
            if (probe != null) {
                probe.discard();
            }
            if (!(probe instanceof GenshinNpcEntity)) {
                XinkunGenshinMod.LOGGER.warn("Dropping respawn entry for unsupported type {}",
                        entry.type());
                iterator.remove();
                changed = true;
                continue;
            }
            EntityType<? extends GenshinNpcEntity> type =
                    (EntityType<? extends GenshinNpcEntity>) raw;
            // The death itself scheduled exactly one entry per character, so a
            // living neighbour is not evidence of a duplicate: two characters
            // may share one home, and blocking on that neighbour would keep
            // the dead one from ever returning.
            Entity spawned = type.spawn(level, pos, EntitySpawnReason.EVENT);
            if (!(spawned instanceof GenshinNpcEntity npc)) {
                if (spawned != null) {
                    spawned.discard();
                }
                iterator.remove();
                changed = true;
                continue;
            }
            npc.setNpcSpawnPoint(pos);
            npc.setHomePoint(pos);
            npc.setPersistenceRequired();
            NpcFx.respawnBurst(level, Vec3.atBottomCenterOf(pos));
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.3F);
            XinkunGenshinMod.LOGGER.info("Respawned character {} at {}", entry.type(), pos);
            iterator.remove();
            changed = true;
        }
        if (changed) {
            this.setDirty();
        }
    }
}
