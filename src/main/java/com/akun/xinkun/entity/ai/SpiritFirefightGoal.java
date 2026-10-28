package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.WaterSpiritEntity;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Extinguishes burning creatures and loose flames. The task may be handed over
 * by Furina, but a spirit also looks for fire on its own every second or so, so
 * a blaze is never ignored just because nobody pointed at it. Fire on hostile
 * monsters is deliberately left burning.
 */
public class SpiritFirefightGoal extends Goal {
    private static final double MOB_SCAN_RADIUS = 16.0;
    private static final int BLOCK_SCAN_RADIUS = 12;
    private static final int BLOCK_SCAN_HEIGHT = 3;

    private final WaterSpiritEntity spirit;
    private int scanCooldown = 10;

    public SpiritFirefightGoal(WaterSpiritEntity spirit) {
        this.spirit = spirit;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.spirit.hasFirefightTask()) {
            return this.spirit.isFirefightTaskValid();
        }
        if (--this.scanCooldown > 0) {
            return false;
        }
        this.scanCooldown = 12 + this.spirit.getRandom().nextInt(10);
        return this.scanForFire();
    }

    @Override
    public boolean canContinueToUse() {
        return this.spirit.hasFirefightTask() && this.spirit.isFirefightTaskValid();
    }

    @Override
    public void start() {
        LivingEntity burning = this.spirit.getBurningTarget();
        this.spirit.setIsCharging(burning != null || this.spirit.getFirePos() != null);
    }

    @Override
    public void stop() {
        this.spirit.setIsCharging(false);
    }

    @Override
    public void tick() {
        Vec3 point = this.targetPoint();
        if (point == null) {
            this.spirit.finishFirefightTask();
            return;
        }
        this.spirit.getLookControl().setLookAt(point.x, point.y, point.z, 30.0F, 30.0F);
        if (this.spirit.distanceToSqr(point) > 6.0) {
            // Reach the fire fast; the spirit is an emergency responder.
            this.spirit.getMoveControl().setWantedPosition(point.x, point.y, point.z, 1.6);
            this.spirit.setIsCharging(true);
            return;
        }
        this.spirit.setIsCharging(false);
        if (!(this.spirit.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos fire = this.spirit.getFirePos();
        if (fire != null) {
            // Block fire always comes first: a creature standing in flames would
            // simply catch fire again if we splashed it while the floor burned.
            this.douseFireCluster(serverLevel, fire);
            NpcFx.waterBurst(serverLevel, Vec3.atCenterOf(fire));
            serverLevel.playSound(null, this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(),
                    ModSounds.WATER_EXTINGUISH, SoundSource.NEUTRAL, 1.0F, 1.0F);
            this.spirit.clearFirePos();
            if (!this.spirit.isFirefightTaskValid()) {
                this.spirit.finishFirefightTask();
            }
            return;
        }
        LivingEntity burning = this.spirit.getBurningTarget();
        if (burning != null) {
            burning.clearFire();
            NpcFx.waterBurst(serverLevel, burning.position().add(0.0, burning.getBbHeight() * 0.6, 0.0));
            serverLevel.playSound(null, this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(),
                    ModSounds.WATER_EXTINGUISH, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        this.spirit.finishFirefightTask();
    }

    /** A single splash puts out the target flame and the embers around it. */
    private void douseFireCluster(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
            if (level.getBlockState(pos).is(BlockTags.FIRE)) {
                level.removeBlock(pos, false);
            }
        }
    }

    private Vec3 targetPoint() {
        BlockPos fire = this.spirit.getFirePos();
        if (fire != null) {
            // The floor flame is addressed before the creature standing in it.
            return Vec3.atCenterOf(fire);
        }
        LivingEntity burning = this.spirit.getBurningTarget();
        if (burning != null && burning.isAlive() && burning.isOnFire()) {
            return burning.position().add(0.0, burning.getBbHeight() * 0.6, 0.0);
        }
        return null;
    }

    private boolean scanForFire() {
        if (!(this.spirit.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity burning = this.findBurningFriendly(level);
        if (burning != null) {
            // If the creature is standing in a surface flame, hand over both
            // jobs in order: block first, creature second.
            this.spirit.setFirefightTarget(this.findFireAtFeet(level, burning), burning);
            return true;
        }
        BlockPos fire = this.findNearbyFire(level);
        if (fire != null) {
            this.spirit.setFirefightTarget(fire, null);
            return true;
        }
        return false;
    }

    private BlockPos findFireAtFeet(ServerLevel level, LivingEntity entity) {
        BlockPos feet = entity.blockPosition();
        BlockPos best = null;
        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(BlockTags.FIRE)) {
                        continue;
                    }
                    double distance = entity.position().distanceToSqr(Vec3.atCenterOf(pos));
                    if (best == null || distance < entity.position().distanceToSqr(Vec3.atCenterOf(best))) {
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private LivingEntity findBurningFriendly(ServerLevel level) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB area = this.spirit.getBoundingBox().inflate(MOB_SCAN_RADIUS, MOB_SCAN_RADIUS * 0.5, MOB_SCAN_RADIUS);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != this.spirit && entity.isAlive() && entity.isOnFire()
                        && !(entity instanceof Enemy) && !(entity instanceof WaterSpiritEntity))) {
            double distance = this.spirit.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private BlockPos findNearbyFire(ServerLevel level) {
        BlockPos origin = this.spirit.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-BLOCK_SCAN_RADIUS, -BLOCK_SCAN_HEIGHT, -BLOCK_SCAN_RADIUS),
                origin.offset(BLOCK_SCAN_RADIUS, BLOCK_SCAN_HEIGHT, BLOCK_SCAN_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.FIRE)) {
                continue;
            }
            double distance = origin.distSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }
}
