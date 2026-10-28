package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.FurinaEntity;
import com.akun.xinkun.entity.WaterSpiritEntity;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModSounds;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Furina's ranged attack. She keeps the target at casting distance, fires a
 * stream of water every couple of seconds and only hands over to the generic
 * melee goal once a monster is right on top of her.
 */
public class FurinaWaterMagicGoal extends Goal {
    private static final double CAST_RANGE_SQR = 32.0 * 32.0;
    private static final double MAX_RANGE_SQR = 24.0 * 24.0;
    /** 对空：射程只看水平距离，飞行目标额外允许 40 格的垂直差。 */
    private static final double VERTICAL_REACH = 40.0;
    /** 只有水平贴脸且高度相近才把输出交给近战；头顶/脚下的敌人继续施法。 */
    private static final double MELEE_HANDOFF_RADIUS = 3.0;
    private static final double MELEE_HANDOFF_HEIGHT = 2.0;
    private static final double CLOSE_RANGE_SQR = 7.0 * 7.0;
    private static final int CAST_INTERVAL_MIN = 24;
    private static final int CAST_INTERVAL_MAX = 38;

    private final FurinaEntity npc;
    private int cooldown = 20;
    private int backstepCooldown;

    public FurinaWaterMagicGoal(FurinaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.hasCastableTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return this.hasCastableTarget();
    }

    private boolean hasCastableTarget() {
        if (!(this.npc.getTarget() instanceof LivingEntity target)) {
            return false;
        }
        if (!(target instanceof Enemy) || target instanceof Creeper || target instanceof WaterSpiritEntity) {
            return false;
        }
        if (!target.isAlive()) {
            return false;
        }
        // 凋零这类目标高悬头顶时三维距离会被高度差撑爆，改看水平距离。
        double distanceSqr = NpcAim.horizontalDistanceSqr(this.npc, target);
        return distanceSqr <= CAST_RANGE_SQR
                && Math.abs(target.getY() - this.npc.getY()) <= VERTICAL_REACH
                && !NpcAim.withinMeleeReach(this.npc, target, MELEE_HANDOFF_RADIUS, MELEE_HANDOFF_HEIGHT);
    }

    @Override
    public void start() {
        this.cooldown = Math.min(this.cooldown, 15);
        this.backstepCooldown = 0;
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(this.npc.getTarget() instanceof LivingEntity target)) {
            return;
        }
        this.npc.getLookControl().setLookAt(target, 40.0F, 40.0F);
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.backstepCooldown > 0) {
            this.backstepCooldown--;
        }

        double distanceSqr = NpcAim.horizontalDistanceSqr(this.npc, target);
        if (distanceSqr > MAX_RANGE_SQR) {
            // 高空目标只追水平位置，避免寻路一直往天上够。
            this.npc.getNavigation().moveTo(target.getX(), this.npc.getY(), target.getZ(), 1.15);
            return;
        }
        if (distanceSqr < CLOSE_RANGE_SQR && this.backstepCooldown <= 0
                && !NpcAim.isHighAbove(this.npc, target)) {
            this.stepBack(target);
            this.backstepCooldown = 28;
        } else if (this.npc.getNavigation().isDone()) {
            this.npc.getNavigation().stop();
        }
        if (this.cooldown > 0) {
            return;
        }
        Vec3 aim = NpcAim.clearAim(this.npc, target);
        if (aim == null) {
            return;
        }
        this.cast(target, aim);
    }

    /** A short hop away keeps monsters outside melee reach without stopping the show. */
    private void stepBack(LivingEntity target) {
        Vec3 away = new Vec3(this.npc.getX() - target.getX(), 0.0, this.npc.getZ() - target.getZ());
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 destination = this.npc.position().add(away.normalize().scale(5.0));
        this.npc.getNavigation().moveTo(destination.x, this.npc.getY(), destination.z, 1.25);
    }

    private void cast(LivingEntity target, Vec3 aim) {
        if (!(this.npc.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 from = this.npc.getEyePosition().add(0.0, -0.15, 0.0);
        Vec3 to = aim;
        this.npc.getLookControl().setLookAt(target, 40.0F, 40.0F);
        this.npc.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
        NpcFx.waterCast(serverLevel, from, to);
        Vec3 body = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        // 地面目标在脚下铺环；飞在空中的目标则在身上绽开，不把水花丢到地面。
        NpcFx.waterRing(serverLevel, !target.onGround() && target.getY() > target.getBlockY() + 1.0
                ? body
                : Vec3.atBottomCenterOf(target.blockPosition()));
        serverLevel.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(), ModSounds.WATER_CAST,
                SoundSource.NEUTRAL, 0.85F, 0.98F + this.npc.getRandom().nextFloat() * 0.06F);
        target.hurtServer(serverLevel, serverLevel.damageSources().magic(), 7.5F);
        this.npc.summonWaterSpiritTrio(null, null, target);
        if (this.npc.isSpecialVoiceReady()) {
            this.npc.speak(VoiceContext.FIRE);
            this.npc.markSpecialVoice(2400);
        }
        this.cooldown = CAST_INTERVAL_MIN + this.npc.getRandom().nextInt(CAST_INTERVAL_MAX - CAST_INTERVAL_MIN + 1);
    }
}
