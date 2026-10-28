package com.akun.xinkun.registry;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.particle.GlowDustOptions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class ModParticles {
    /**
     * 全亮光尘：服务端只用颜色和大小描述效果，亮度完全由客户端粒子决定。
     */
    public static final ParticleType<GlowDustOptions> GLOW_DUST = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            XinkunGenshinMod.id("glow_dust"),
            new ParticleType<GlowDustOptions>(false) {
                @Override
                public MapCodec<GlowDustOptions> codec() {
                    return GlowDustOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, GlowDustOptions> streamCodec() {
                    return GlowDustOptions.STREAM_CODEC;
                }
            });

    private ModParticles() {
    }

    public static void initialize() {
    }
}
