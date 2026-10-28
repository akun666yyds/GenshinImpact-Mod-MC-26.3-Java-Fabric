package com.akun.xinkun.effect;

import com.akun.xinkun.XinkunGenshinMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import com.akun.xinkun.particle.GlowDustOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * 苍白月光：被月影波及的敌人浑身泛白，只要仍站在夜色下的月光里，
 * 就会持续受到高额溺水伤害。躲进有顶的地方可以暂时逃过一劫，
 * 但苍白的光会一直跟着他，直到效果结束。
 */
public class MoonPaleEffect extends MobEffect {
    /** 每秒的溺水伤害；四颗心足以让普通怪物在几秒内倒下。 */
    public static final float DROWN_DAMAGE = 4.0F;
    public static final int DURATION = 300;

    public MoonPaleEffect() {
        super(MobEffectCategory.HARMFUL, 0xEDE8FF, new GlowDustOptions(0xF3EEFF, 1.3F));
    }

    public static Holder<MobEffect> register() {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                XinkunGenshinMod.id("moon_pale"), new MoonPaleEffect());
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        boolean moonlight = isInMoonlight(level, entity);
        // 苍白的轮廓：即使躲进屋里，身上也持续泛着月光。
        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
        level.sendParticles(new GlowDustOptions(0xF4F0FF, 1.5F),
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.55, entity.getZ(),
                3, 0.26, 0.34, 0.26, 0.005);
        level.sendParticles(ParticleTypes.WHITE_ASH,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                3, 0.3, 0.4, 0.3, 0.01);
        if (!moonlight) {
            return true;
        }
        entity.hurtServer(level, level.damageSources().drown(), DROWN_DAMAGE + amplifier * 0.75F);
        level.sendParticles(ParticleTypes.END_ROD,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                4, 0.24, 0.32, 0.24, 0.015);
        level.sendParticles(ParticleTypes.WHITE_SMOKE,
                entity.getX(), entity.getY() + 0.15, entity.getZ(),
                2, 0.24, 0.12, 0.24, 0.0);
        return true;
    }

    /** 只有夜晚且头顶没有遮挡时，月光才会真正灼伤目标。 */
    public static boolean isInMoonlight(ServerLevel level, LivingEntity entity) {
        long time = level.getDefaultClockTime() % 24000L;
        if (time < 13000L || time >= 23000L) {
            return false;
        }
        if (!level.canSeeSky(entity.blockPosition())) {
            return false;
        }
        return !level.isRaining();
    }

    @Override
    public boolean isInstantaneous() {
        return false;
    }
}
