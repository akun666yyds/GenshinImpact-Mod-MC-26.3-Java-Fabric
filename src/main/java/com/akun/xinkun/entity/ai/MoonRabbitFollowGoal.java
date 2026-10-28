package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.MoonRabbitEntity;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 月兔跟着主人走。兔子原版的移动控制器只会跳，长距离寻路很容易在栅栏、
 * 台阶或水边打转；因此跟丢超过一段距离时直接回到主人脚边，而不是卡在
 * 某个角落直到领域结束。
 */
public class MoonRabbitFollowGoal extends Goal {
    private static final double START_DISTANCE = 6.0;
    private static final double STOP_DISTANCE = 3.0;
    private static final double TELEPORT_DISTANCE = 24.0;
    private static final int STUCK_LIMIT = 60;

    private final MoonRabbitEntity rabbit;
    private LivingEntity owner;
    private int stuckTicks;
    private double lastX;
    private double lastZ;
    private int recalcCooldown;

    public MoonRabbitFollowGoal(MoonRabbitEntity rabbit) {
        this.rabbit = rabbit;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity found = this.findOwner();
        if (found == null || !found.isAlive()) {
            return false;
        }
        if (this.rabbit.getTarget() != null && this.rabbit.getTarget().isAlive()) {
            return false;
        }
        if (this.rabbit.distanceToSqr(found) < START_DISTANCE * START_DISTANCE) {
            return false;
        }
        this.owner = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.owner == null || !this.owner.isAlive()) {
            return false;
        }
        if (this.rabbit.getTarget() != null && this.rabbit.getTarget().isAlive()) {
            return false;
        }
        return this.rabbit.distanceToSqr(this.owner) > STOP_DISTANCE * STOP_DISTANCE
                && this.owner.level() == this.rabbit.level();
    }

    @Override
    public void start() {
        this.stuckTicks = 0;
        this.lastX = this.rabbit.getX();
        this.lastZ = this.rabbit.getZ();
        this.recalcCooldown = 0;
        this.moveToOwner();
    }

    @Override
    public void tick() {
        if (this.owner == null) {
            return;
        }
        this.rabbit.getLookControl().setLookAt(this.owner, 30.0F, 30.0F);
        double distance = this.rabbit.distanceTo(this.owner);
        if (distance >= TELEPORT_DISTANCE) {
            this.rabbit.teleportNear(this.owner, this.rabbit.getRandom().nextInt(3) - 1);
            this.stuckTicks = 0;
            return;
        }
        if (--this.recalcCooldown <= 0) {
            this.recalcCooldown = 20;
            this.moveToOwner();
        }
        this.checkStuck();
    }

    @Override
    public void stop() {
        this.owner = null;
        this.rabbit.getNavigation().stop();
    }

    private void moveToOwner() {
        if (this.owner != null) {
            this.rabbit.getNavigation().moveTo(this.owner, 1.45);
        }
    }

    private void checkStuck() {
        if (this.rabbit.tickCount % 20 != 0) {
            return;
        }
        double moved = Math.abs(this.rabbit.getX() - this.lastX) + Math.abs(this.rabbit.getZ() - this.lastZ);
        this.lastX = this.rabbit.getX();
        this.lastZ = this.rabbit.getZ();
        if (moved < 0.35) {
            this.stuckTicks += 20;
        } else {
            this.stuckTicks = 0;
        }
        if (this.stuckTicks >= STUCK_LIMIT) {
            this.rabbit.teleportNear(this.owner, 1);
            this.stuckTicks = 0;
        }
    }

    private LivingEntity findOwner() {
        UUID ownerId = this.rabbit.getOwner();
        if (ownerId == null || !(this.rabbit.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity found = level.getEntityInAnyDimension(ownerId);
        return found instanceof LivingEntity living ? living : null;
    }

    /** A dry, standable cell next to the entity, or the entity's own feet. */
    public static BlockPos safeSpotNear(ServerLevel level, LivingEntity target, int side) {
        BlockPos base = target.blockPosition();
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos feet = base.offset(dx, dy, dz);
                        if (dx * side < 0) {
                            continue;
                        }
                        if (isFree(level, feet) && isFree(level, feet.above())) {
                            return feet;
                        }
                    }
                }
            }
        }
        return base;
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
