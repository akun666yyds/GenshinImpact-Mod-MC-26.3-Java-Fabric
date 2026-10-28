package com.akun.xinkun.particle;

import com.akun.xinkun.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ScalableParticleOptionsBase;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;

/**
 * 全亮的彩色光尘：颜色与大小的语义与原版 {@code DustParticleOptions} 保持一致，
 * 客户端粒子在渲染时强制使用满亮度，因此夜间、洞穴与水下都保持同样的亮度。
 * 两种配色叠在同一条射线上，就能画出又粗又亮的魔法光束。
 */
public class GlowDustOptions extends ScalableParticleOptionsBase implements ParticleOptions {
    public static final MapCodec<GlowDustOptions> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ExtraCodecs.RGB_COLOR_CODEC.fieldOf("color").forGetter(GlowDustOptions::getColorRgb),
                    SCALE.fieldOf("scale").forGetter(GlowDustOptions::getScale)
            ).apply(instance, GlowDustOptions::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlowDustOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, GlowDustOptions::getColorRgb,
            ByteBufCodecs.FLOAT, GlowDustOptions::getScale,
            GlowDustOptions::new);

    private final int color;

    public GlowDustOptions(final int color, final float scale) {
        super(scale);
        this.color = color & 0xFFFFFF;
    }

    @Override
    public ParticleType<GlowDustOptions> getType() {
        return ModParticles.GLOW_DUST;
    }

    public int getColorRgb() {
        return this.color;
    }

    public Vector3f getColor() {
        return ARGB.vector3fFromRGB24(this.color);
    }
}
