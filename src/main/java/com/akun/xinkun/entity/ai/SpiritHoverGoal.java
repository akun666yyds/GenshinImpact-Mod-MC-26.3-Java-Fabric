package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.WaterSpiritEntity;
import com.akun.xinkun.fx.NpcFx;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a summoned spirit with its owner like a pet: a loose formation around
 * Furina while she roams, a direct chase when separated, and a small blink
 * back into formation if the owner teleports too far away.
 */
public class SpiritHoverGoal extends Goal {
    private final WaterSpiritEntity spirit;
    private double angle;

    public SpiritHoverGoal(WaterSpiritEntity spirit) {
        this.spirit = spirit;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.angle = spirit.getRandom().nextDouble() * Math.PI * 2.0;
    }

    @Override
    public boolean canUse() {
        return !this.spirit.hasFirefightTask() && this.spirit.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void tick() {
        this.spirit.setIsCharging(false);
        LivingEntity owner = this.spirit.getOwner();
        if (owner != null && owner.isAlive()) {
            this.followOwner(owner);
            return;
        }
        BlockPos origin = this.spirit.getBoundOrigin();
        if (origin == null) {
            origin = this.spirit.blockPosition();
        }
        this.orbit(origin.getX() + 0.5, origin.getY() + 1.4, origin.getZ() + 0.5, 1.9, 0.02, 0.8);
    }

    private void followOwner(LivingEntity owner) {
        double distanceSqr = this.spirit.distanceToSqr(owner);
        if (distanceSqr > 400.0) {
            this.blinkToOwner(owner);
            return;
        }
        int slot = this.spirit.getKind().ordinal();
        double radius = 2.6 + slot * 0.3;
        double height = 1.1 + slot * 0.35;
        double speed = distanceSqr > 36.0 ? 1.35 : 0.9;
        double targetRadius = distanceSqr > 36.0 ? 1.6 : radius;
        this.orbit(owner.getX(), owner.getY() + height, owner.getZ(), targetRadius, 0.02, speed);
        if (this.spirit.tickCount % 20 == 0) {
            this.spirit.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        }
    }

    private void orbit(double centerX, double centerY, double centerZ, double radius, double angularSpeed,
                       double speed) {
        this.angle += angularSpeed;
        double x = centerX + Math.cos(this.angle) * radius;
        double z = centerZ + Math.sin(this.angle) * radius;
        double y = centerY + 0.25 + Math.sin((this.spirit.tickCount + this.angle * 8.0) * 0.06) * 0.22;
        this.spirit.getMoveControl().setWantedPosition(x, y, z, speed);
    }

    private void blinkToOwner(LivingEntity owner) {
        double x = owner.getX() + Math.cos(this.angle) * 2.2;
        double z = owner.getZ() + Math.sin(this.angle) * 2.2;
        double y = owner.getY() + 1.2;
        this.spirit.snapTo(x, y, z, this.spirit.getYRot(), this.spirit.getXRot());
        this.spirit.setDeltaMovement(Vec3.ZERO);
        if (this.spirit.level() instanceof ServerLevel serverLevel) {
            NpcFx.spiritSummon(serverLevel, new Vec3(x, y + 0.3, z));
        }
    }
}
