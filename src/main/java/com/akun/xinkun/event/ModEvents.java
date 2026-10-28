package com.akun.xinkun.event;

import com.akun.xinkun.building.BuildingPlacementManager;
import com.akun.xinkun.entity.GenshinNpcEntity;
import com.akun.xinkun.entity.HuTaoEntity;
import com.akun.xinkun.entity.ai.YoimiyaBowGoal;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.world.HuTaoReturnManager;
import com.akun.xinkun.world.NpcRespawnManager;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;

public final class ModEvents {
    private static final float NEAR_DEATH_HEALTH = 6.0F;
    private static final double CONDOLENCE_RADIUS = 16.0;

    private ModEvents() {
    }

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register(ModEvents::afterDeath);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(ModEvents::afterDamage);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            NpcRespawnManager.tick(server);
            HuTaoReturnManager.tick(server);
            BuildingPlacementManager.tick(server);
            trailFlameArrows(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HuTaoReturnManager.flush(server);
            BuildingPlacementManager.flush(server);
        });
    }

    private static void trailFlameArrows(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Arrow arrow : level.getEntities(EntityTypes.ARROW,
                    a -> a.entityTags().contains(YoimiyaBowGoal.FLAME_ARROW_TAG)
                            && a.isAlive() && a.getDeltaMovement().lengthSqr() > 0.0025)) {
                NpcFx.fieryArrowTrail(level, arrow.position());
            }
        }
    }

    private static void afterDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof GenshinNpcEntity npc) {
            // Every death is temporary, including a hit from a player. The
            // pending respawn is saved with the world, so a shutdown between
            // death and the respawn cannot erase the character for good.
            NpcRespawnManager.schedule(npc);
            return;
        }
        if (entity instanceof ServerPlayer player) {
            HuTaoReturnManager.onPlayerDeath(player);
        }
    }

    private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damage, boolean blocked) {
        if (blocked || damage <= 0.0F) {
            return;
        }
        if (!(entity instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        if (player.getHealth() > NEAR_DEATH_HEALTH || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<HuTaoEntity> helpers = level.getEntitiesOfClass(HuTaoEntity.class,
                player.getBoundingBox().inflate(CONDOLENCE_RADIUS), LivingEntity::isAlive);
        if (helpers.isEmpty()) {
            return;
        }
        HuTaoEntity helper = helpers.get(0);
        for (HuTaoEntity candidate : helpers) {
            if (candidate.distanceToSqr(player) < helper.distanceToSqr(player)) {
                helper = candidate;
            }
        }
        helper.reactToDyingPlayer(player);
    }
}
