package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.HuTaoEntity;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModSounds;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Hu Tao's ranged attack. Fire magic is her default answer to any hostile mob;
 * the shared melee goal only takes over when something closes to arm's length.
 */
public class HuTaoFireMagicGoal extends Goal {
    private static final double CAST_RANGE_SQR = 32.0 * 32.0;
    private static final double MAX_RANGE_SQR = 24.0 * 24.0;
    /** 对空：射程只看水平距离，飞行目标额外允许 40 格的垂直差。 */
    private static final double VERTICAL_REACH = 40.0;
    /** 只有水平贴脸且高度相近才把输出交给近战；头顶/脚下的敌人继续施法。 */
    private static final double MELEE_HANDOFF_RADIUS = 3.0;
    private static final double MELEE_HANDOFF_HEIGHT = 2.0;
    private static final double CLOSE_RANGE_SQR = 7.0 * 7.0;
    private static final int CAST_INTERVAL_MIN = 13;
    private static final int CAST_INTERVAL_MAX = 21;

    private final HuTaoEntity npc;
    private int cooldown = 10;
    private int backstepCooldown;

    public HuTaoFireMagicGoal(HuTaoEntity npc) {
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
        if (!(target instanceof Enemy) || target instanceof Creeper || !target.isAlive()) {
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
        this.cooldown = Math.min(this.cooldown, 8);
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
            this.npc.getNavigation().moveTo(target.getX(), this.npc.getY(), target.getZ(), 1.18);
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
        Vec3 start = this.npc.getEyePosition();
        Vec3 end = aim;
        this.npc.getLookControl().setLookAt(target, 40.0F, 40.0F);
        NpcFx.fireCast(serverLevel, start, end);
        serverLevel.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(), ModSounds.FIRE_CAST,
                SoundSource.NEUTRAL, 0.8F, 0.98F + this.npc.getRandom().nextFloat() * 0.06F);
        serverLevel.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(), SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, 0.7F, 1.1F);
        target.hurtServer(serverLevel, serverLevel.damageSources().magic(), 5.0F);
        target.igniteForSeconds(5.0F);
        if (this.npc.isSpecialVoiceReady()) {
            this.npc.speak(VoiceContext.FIRE_MAGIC);
            this.npc.markSpecialVoice(1200);
        }
        this.npc.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
        this.cooldown = CAST_INTERVAL_MIN + this.npc.getRandom().nextInt(CAST_INTERVAL_MAX - CAST_INTERVAL_MIN + 1);
    }
}
