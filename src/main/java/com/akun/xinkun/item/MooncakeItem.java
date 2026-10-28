package com.akun.xinkun.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;

/**
 * 「少女」的月饼。吃下后立刻回满生命，并获得长达十五分钟的一整套增益。
 *
 * <p>增益写在 {@link Consumable} 组件里，而不是只靠 {@code finishUsingItem}：
 * 26.3 的食用流程统一由组件驱动，这样创造模式、命令给予以及任何能吃东西的
 * 生物都会得到一致的结算。</p>
 */
public class MooncakeItem extends Item {
    /** 15 分钟 = 18000 tick。 */
    public static final int BUFF_TICKS = 20 * 60 * 15;

    public MooncakeItem(Properties properties) {
        super(properties);
    }

    public static FoodProperties foodProperties() {
        // 20 点营养、20 点饱和度；受伤时也能安心吃完，不挑饥饿值。
        return new FoodProperties(20, 20.0F, true);
    }

    public static Consumable consumable() {
        List<MobEffectInstance> effects = List.of(
                new MobEffectInstance(MobEffects.RESISTANCE, BUFF_TICKS, 1),
                new MobEffectInstance(MobEffects.REGENERATION, BUFF_TICKS, 1),
                new MobEffectInstance(MobEffects.ABSORPTION, BUFF_TICKS, 2),
                new MobEffectInstance(MobEffects.LUCK, BUFF_TICKS, 1),
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, BUFF_TICKS, 0),
                new MobEffectInstance(MobEffects.JUMP_BOOST, BUFF_TICKS, 1),
                new MobEffectInstance(MobEffects.SPEED, BUFF_TICKS, 1),
                new MobEffectInstance(MobEffects.STRENGTH, BUFF_TICKS, 1),
                new MobEffectInstance(MobEffects.SATURATION, BUFF_TICKS, 0));
        return Consumable.builder()
                .consumeSeconds(1.6F)
                .animation(ItemUseAnimation.EAT)
                .sound(SoundEvents.GENERIC_EAT)
                .hasConsumeParticles(true)
                .onConsume(new ApplyStatusEffectsConsumeEffect(effects))
                .build();
    }

    /**
     * 先回满生命，再交给原版食用流程处理饥饿与增益。
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (!level.isClientSide() && living.isAlive()) {
            living.setHealth(living.getMaxHealth());
        }
        return super.finishUsingItem(stack, level, living);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> consumer, TooltipFlag flag) {
        consumer.accept(Component.translatable("item.xinkun_genshin.mooncake.tooltip")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        consumer.accept(Component.translatable("item.xinkun_genshin.mooncake.tooltip.buffs")
                .withStyle(ChatFormatting.GRAY));
    }
}
