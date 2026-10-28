package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.ColumbinaEntity;
import com.akun.xinkun.entity.GenshinNpcEntity;
import com.akun.xinkun.entity.WaterSpiritEntity;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModSounds;
import com.akun.xinkun.voice.VoiceContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 无论白天还是夜里，哥伦比娅都会随时照看身边的人：玩家、其他原神角色，
 * 以及没有敌意的生物。她不会治疗怪物，也不会打断自己正在进行的战斗。
 */
public class ColumbinaHealGoal extends Goal {
    private static final double HEAL_RADIUS = 10.0;
    private static final int HEAL_INTERVAL = 90;
    private static final float HEAL_AMOUNT = 5.0F;

    private final ColumbinaEntity npc;
    private final List<LivingEntity> patients = new ArrayList<>();
    private int cooldown;

    public ColumbinaHealGoal(ColumbinaEntity npc) {
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level.getGameTime() % 20L != 0L) {
            return false;
        }
        this.patients.clear();
        AABB area = this.npc.getBoundingBox().inflate(HEAL_RADIUS);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (candidate == this.npc || !candidate.isAlive() || this.isHostile(candidate)) {
                continue;
            }
            if (!this.isFriendly(candidate)) {
                continue;
            }
            if (candidate.getHealth() >= candidate.getMaxHealth()) {
                continue;
            }
            if (this.npc.distanceToSqr(candidate) > HEAL_RADIUS * HEAL_RADIUS) {
                continue;
            }
            this.patients.add(candidate);
            if (this.patients.size() >= 6) {
                break;
            }
        }
        return !this.patients.isEmpty();
    }

    private boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy;
    }

    private boolean isFriendly(LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.isSpectator();
        }
        if (entity instanceof GenshinNpcEntity) {
            return true;
        }
        return entity instanceof Mob && !(entity instanceof WaterSpiritEntity) && !(entity instanceof Enemy);
    }

    @Override
    public void start() {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        for (LivingEntity patient : this.patients) {
            patient.heal(HEAL_AMOUNT);
            patient.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, false, true, true));
            NpcFx.moonHeal(level, patient.position());
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    patient.getX(), patient.getY() + patient.getBbHeight() * 0.6, patient.getZ(),
                    4, 0.25, 0.3, 0.25, 0.0);
        }
        Vec3 pos = this.npc.position();
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.MOON_HEAL,
                SoundSource.NEUTRAL, 0.8F, 1.0F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 0.45F, 1.35F);
        if (this.npc.isSpecialVoiceReady()) {
            this.npc.speak(VoiceContext.HEAL);
            this.npc.markSpecialVoice(1100);
        }
        this.cooldown = HEAL_INTERVAL;
        this.patients.clear();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
