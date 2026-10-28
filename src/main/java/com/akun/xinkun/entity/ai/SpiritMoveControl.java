package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.WaterSpiritEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * The vanilla vex controller keeps adding velocity toward its target without any
 * damping, so an orbit that updates every tick slowly accelerates the spirit
 * across the world. This control steers with a capped, damped velocity instead.
 */
public class SpiritMoveControl extends MoveControl<WaterSpiritEntity> {
    // Fast enough to keep formation with a walking or sprinting Furina. The old
    // 0.32 cap made every spirit trail far behind once she moved.
    private static final double MAX_SPEED = 0.72;

    public SpiritMoveControl(WaterSpiritEntity spirit) {
        super(spirit);
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.84));
            return;
        }
        this.operation = Operation.WAIT;
        Vec3 delta = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(),
                this.wantedZ - this.mob.getZ());
        double distance = delta.length();
        if (distance < 0.05) {
            this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.5));
            return;
        }
        double speed = Math.min(MAX_SPEED * this.speedModifier, distance * 0.45);
        Vec3 desired = delta.scale(speed / distance);
        Vec3 blended = this.mob.getDeltaMovement().scale(0.35).add(desired.scale(0.65));
        if (blended.lengthSqr() > MAX_SPEED * MAX_SPEED) {
            blended = blended.normalize().scale(MAX_SPEED);
        }
        this.mob.setDeltaMovement(blended);
        float yaw;
        if (this.mob.getTarget() != null) {
            double tx = this.mob.getTarget().getX() - this.mob.getX();
            double tz = this.mob.getTarget().getZ() - this.mob.getZ();
            yaw = -((float) Mth.atan2(tx, tz)) * (180.0F / (float) Math.PI);
        } else {
            yaw = -((float) Mth.atan2(blended.x, blended.z)) * (180.0F / (float) Math.PI);
        }
        this.mob.setYRot(yaw);
        this.mob.yBodyRot = yaw;
    }
}
