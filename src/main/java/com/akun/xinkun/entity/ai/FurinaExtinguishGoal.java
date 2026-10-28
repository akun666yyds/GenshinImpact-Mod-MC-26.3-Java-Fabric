package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.FurinaEntity;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Furina never fights fires herself. She scries for a burning friendly/neutral
 * creature or a surface flame and sends her water spirits to put it out. When a
 * creature is standing in a surface flame, both jobs are handed over at once so
 * the spirits douse the block first and only then the creature.
 */
public class FurinaExtinguishGoal extends Goal {
    private static final int FIRST_SCAN_DELAY = 10;
    private static final int RETRY_DELAY_MIN = 40;
    private static final int RETRY_DELAY_SPREAD = 40;

    private final FurinaEntity npc;
    private LivingEntity burningTarget;
    private BlockPos firePos;
    private int nextStartTick = FIRST_SCAN_DELAY;

    public FurinaExtinguishGoal(FurinaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        }
        // Fire response outranks combat and chores: a blaze is re-checked
        // within a couple of seconds instead of waiting for a long idle roll.
        this.nextStartTick = RETRY_DELAY_MIN + this.npc.getRandom().nextInt(RETRY_DELAY_SPREAD);
        this.burningTarget = this.findBurningFriendly();
        this.firePos = null;
        if (this.burningTarget != null) {
            // Extinguish the floor flame before touching the creature, or it
            // would immediately catch fire again.
            this.firePos = this.findFireAtFeet(this.burningTarget);
            return true;
        }
        this.firePos = this.findSurfaceFire();
        return this.firePos != null;
    }

    private LivingEntity findBurningFriendly() {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : this.npc.level().getEntitiesOfClass(LivingEntity.class,
                this.npc.getBoundingBox().inflate(16.0, 8.0, 16.0),
                candidate -> candidate != this.npc && candidate.isAlive() && candidate.isOnFire()
                        && !(candidate instanceof Enemy))) {
            if (!this.npc.getSensing().hasLineOfSight(entity)) {
                continue;
            }
            double distance = this.npc.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private BlockPos findFireAtFeet(LivingEntity entity) {
        BlockPos feet = entity.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    if (!this.npc.level().getBlockState(pos).is(BlockTags.FIRE)) {
                        continue;
                    }
                    double distance = entity.position().distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private BlockPos findSurfaceFire() {
        BlockPos origin = this.npc.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, -4, -12), origin.offset(12, 4, 12))) {
            if (!this.npc.level().getBlockState(pos).is(BlockTags.FIRE)) {
                continue;
            }
            BlockPos below = pos.below();
            if (!this.npc.level().getBlockState(below).isFaceSturdy(this.npc.level(), below, Direction.UP)) {
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

    @Override
    public void start() {
        this.npc.summonWaterSpiritTrio(this.firePos, this.burningTarget, null);
        if (this.npc.level() instanceof ServerLevel serverLevel) {
            NpcFx.waterRing(serverLevel, this.npc.position());
        }
        if (this.npc.isSpecialVoiceReady()) {
            this.npc.speak(VoiceContext.FIRE);
            this.npc.markSpecialVoice(2400);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
