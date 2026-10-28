package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.WaterSpiritEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Runs down whatever Furina is currently fighting, and keeps fighting after
 * that enemy falls: before drifting back to its owner, a spirit looks for the
 * next hostile within {@link #NEARBY_SCAN_RADIUS}. Only when the area is clear
 * does it hand control to the hover goal.
 */
public class SpiritCombatGoal extends Goal {
    private static final double CHASE_RANGE_SQR = 48.0 * 48.0;
    private static final double STRIKE_RANGE_SQR = 6.25;
    private static final double NEARBY_SCAN_RADIUS = 24.0;

    private final WaterSpiritEntity spirit;
    private int attackCooldown;

    public SpiritCombatGoal(WaterSpiritEntity spirit) {
        this.spirit = spirit;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.adoptOwnerTarget();
        LivingEntity target = this.spirit.getTarget();
        if (target == null || !this.isValidTarget(target)) {
            // Never keep a stale target: it would also suppress the hover goal.
            this.spirit.setTarget(null);
            this.acquireNearbyTarget();
            target = this.spirit.getTarget();
            if (target == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.attackCooldown = 6;
        this.spirit.setIsCharging(true);
    }

    @Override
    public void stop() {
        this.spirit.setIsCharging(false);
    }

    @Override
    public void tick() {
        LivingEntity target = this.spirit.getTarget();
        if (target == null) {
            return;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        this.spirit.getLookControl().setLookAt(target, 35.0F, 35.0F);
        double distance = this.spirit.distanceToSqr(target);
        if (distance > STRIKE_RANGE_SQR) {
            this.spirit.getMoveControl().setWantedPosition(target.getX(), target.getY() + target.getBbHeight() * 0.6,
                    target.getZ(), 1.5);
            this.spirit.setIsCharging(true);
            return;
        }
        this.spirit.setIsCharging(false);
        if (this.attackCooldown <= 0 && this.spirit.level() instanceof ServerLevel serverLevel) {
            this.spirit.doHurtTarget(serverLevel, target);
            this.attackCooldown = 18;
        }
    }

    private void adoptOwnerTarget() {
        LivingEntity owner = this.spirit.getOwner();
        if (!(owner instanceof net.minecraft.world.entity.Mob mob) || !mob.isAlive()) {
            return;
        }
        LivingEntity ownerTarget = mob.getTarget();
        if (ownerTarget != null && ownerTarget.isAlive()
                && this.isValidTarget(ownerTarget)
                && this.spirit.distanceToSqr(ownerTarget) <= CHASE_RANGE_SQR) {
            this.spirit.setTarget(ownerTarget);
        }
    }

    /** 打完一个目标后继续找附近的敌人，找不到才允许回到主人身边。 */
    private void acquireNearbyTarget() {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : this.spirit.level().getEntitiesOfClass(
                LivingEntity.class,
                this.spirit.getBoundingBox().inflate(NEARBY_SCAN_RADIUS),
                this::isValidTarget)) {
            double score = this.spirit.distanceToSqr(candidate);
            if (score < best) {
                best = score;
                nearest = candidate;
            }
        }
        if (nearest != null) {
            this.spirit.setTarget(nearest);
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        return target instanceof Enemy
                && !(target instanceof WaterSpiritEntity)
                && !(target instanceof WitherBoss)
                && !(target instanceof Creeper)
                && target.isAlive()
                && this.spirit.distanceToSqr(target) <= CHASE_RANGE_SQR;
    }
}
