package com.akun.xinkun.registry;

import com.akun.xinkun.effect.MoonPaleEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

public final class ModEffects {
    public static final Holder<MobEffect> MOON_PALE = MoonPaleEffect.register();

    private ModEffects() {
    }

    public static void initialize() {
        // 类加载时完成效果注册
    }
}
