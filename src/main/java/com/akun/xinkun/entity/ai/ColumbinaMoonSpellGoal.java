package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.ColumbinaEntity;
import com.akun.xinkun.entity.MoonPhantomEntity;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModSounds;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * 哥伦比娅的月影术。入夜后锁定敌人，在敌人身后升起一轮与当晚月相
 * 完全一致的巨大月亮幻影；月盘每两秒落下一道月光脉冲，把范围内的
 * 敌人染成苍白，并让他们在露天月色中持续溺水。
 */
public class ColumbinaMoonSpellGoal extends Goal {
    private static final double CAST_RANGE_SQR = 48.0 * 48.0;
    /** 月盘直径：足够大，才能让整片战场都落在月华里。 */
    private static final float PHANTOM_SCALE = 26.0F;
    /** 领域存续 24 秒：月兔与脉冲都有充足的时间发挥作用。 */
    private static final int PHANTOM_LIFETIME = 480;
    private static final int CAST_COOLDOWN = 170;

    private final ColumbinaEntity npc;
    private int cooldown = 30;

    public ColumbinaMoonSpellGoal(ColumbinaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (!this.npc.canFightNow() || !this.npc.isNightTime()) {
            return false;
        }
        if (!(this.npc.getTarget() instanceof LivingEntity target) || !target.isAlive()) {
            return false;
        }
        if (!(target instanceof Enemy)) {
            return false;
        }
        return this.npc.distanceToSqr(target) <= CAST_RANGE_SQR;
    }

    @Override
    public void start() {
        this.npc.ensureWeapon();
        if (!(this.npc.getTarget() instanceof LivingEntity target)
                || !(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 aim = target.position().subtract(this.npc.position());
        if (aim.lengthSqr() < 0.01) {
            aim = this.npc.getLookAngle();
        }
        aim = aim.normalize();
        // 月亮落在敌人身后：玩家从正面看过去，敌人正好站在月盘中央。
        // 月盘直径 26 格，中心抬到半径略高处，下缘刚好悬在地平线上。
        double centerY = Math.max(target.getY() + PHANTOM_SCALE * 0.5 + 1.0,
                this.npc.getY() + PHANTOM_SCALE * 0.5);
        Vec3 center = new Vec3(target.getX(), centerY, target.getZ()).add(aim.scale(10.0));
        int phase = (int) ((level.getDefaultClockTime() / 24000L) % 8L);
        MoonPhantomEntity phantom = new MoonPhantomEntity(
                com.akun.xinkun.registry.ModEntities.MOON_PHANTOM, level);
        phantom.configure(phase, PHANTOM_SCALE, PHANTOM_LIFETIME, (float) target.getY());
        phantom.setOwner(this.npc.getUUID());
        phantom.snapTo(center.x, center.y, center.z, 0.0F, 0.0F);
        level.addFreshEntity(phantom);
        NpcFx.moonSummon(level, center, PHANTOM_SCALE);
        level.playSound(null, center.x, center.y, center.z, ModSounds.MOON_SUMMON,
                SoundSource.AMBIENT, 2.2F, 0.92F);
        level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                ModSounds.MOON_SUMMON, SoundSource.NEUTRAL, 0.65F, 1.24F);
        this.npc.getLookControl().setLookAt(target, 40.0F, 40.0F);
        this.npc.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (this.npc.isSpecialVoiceReady()) {
            this.npc.speak(VoiceContext.MOON_ATTACK);
            this.npc.markSpecialVoice(600);
        }
        this.cooldown = CAST_COOLDOWN + this.npc.getRandom().nextInt(50);
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void stop() {
        // 施法不占用移动，月影会自行完成后续脉冲。
    }
}
