package com.akun.xinkun.client.particle;

import com.akun.xinkun.particle.GlowDustOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/**
 * 全亮光尘。相比原版 dust，它在渲染时直接返回满亮度光照坐标，
 * 因此不受夜晚、洞穴或水下光照影响；材质采用原版柔和光斑，
 * 多层叠在一条直线上就形成有体积感的发光光束。
 */
public class GlowDustParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float baseAlpha;

    private GlowDustParticle(ClientLevel level, double x, double y, double z,
                             double xa, double ya, double za,
                             GlowDustOptions options, SpriteSet sprites) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.sprites = sprites;
        this.hasPhysics = false;
        this.friction = 0.93F;
        this.gravity = 0.0F;
        this.quadSize = this.quadSize * (0.95F * options.getScale());
        this.lifetime = Mth.clamp(
                (int) (7.0 / (this.random.nextDouble() * 0.7 + 0.25) * (0.6 + 0.4 * options.getScale())),
                6, 44);
        Vector3f color = options.getColor();
        float jitter = 0.86F + this.random.nextFloat() * 0.14F;
        this.setColor(color.x() * jitter, color.y() * jitter, color.z() * jitter);
        // 速度只保留很轻的游动感，保证光束轮廓稳定。
        this.xd *= 0.32;
        this.yd *= 0.32;
        this.zd *= 0.32;
        this.baseAlpha = 0.9F;
        this.setAlpha(this.baseAlpha);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public int getLightCoords(float partialTickTime) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    public float getQuadSize(float partialTickTime) {
        float grow = Mth.clamp((this.age + partialTickTime) / 2.5F, 0.0F, 1.0F);
        return this.quadSize * grow;
    }

    @Override
    public void tick() {
        super.tick();
        float progress = (float) this.age / Math.max(1, this.lifetime);
        this.setAlpha(this.baseAlpha * Mth.clamp(1.0F - progress * progress, 0.0F, 1.0F));
        this.setSpriteFromAge(this.sprites);
    }

    public static class Provider implements ParticleProvider<GlowDustOptions> {
        private final SpriteSet sprites;

        public Provider(final SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(GlowDustOptions options, ClientLevel level,
                                       double x, double y, double z,
                                       double xa, double ya, double za,
                                       RandomSource random) {
            return new GlowDustParticle(level, x, y, z, xa, ya, za, options, this.sprites);
        }
    }
}
