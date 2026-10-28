package com.akun.xinkun.fx;

import com.akun.xinkun.particle.GlowDustOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Shared particle choreography so every character skill reads as a deliberate
 * effect instead of a single particle call.
 *
 * <p>所有角色魔法统一使用 {@link GlowDustOptions}：客户端会把这种光尘渲染在满亮度，
 * 因此夜里、洞穴与水下都不会变暗。纯色与浅色两层叠加，让射线拥有体积感。</p>
 */
public final class NpcFx {
    // 芙宁娜：纯蓝与浅蓝的双色水光。
    private static final GlowDustOptions WATER_DEEP = new GlowDustOptions(0x1D5CFF, 3.0F);
    private static final GlowDustOptions WATER_LIGHT = new GlowDustOptions(0x8FE3FF, 2.2F);
    private static final GlowDustOptions WATER_PALE = new GlowDustOptions(0xD6F6FF, 1.5F);

    // 胡桃：橙红火焰与金色高光。
    private static final GlowDustOptions FIRE_CORE = new GlowDustOptions(0xFF6A18, 2.8F);
    private static final GlowDustOptions FIRE_GOLD = new GlowDustOptions(0xFFC23D, 2.1F);
    private static final GlowDustOptions FIRE_PALE = new GlowDustOptions(0xFFF0B0, 1.4F);

    // 其他角色与通用效果。
    private static final GlowDustOptions CAKE_PINK = new GlowDustOptions(0xFF9AC8, 1.5F);
    private static final GlowDustOptions SPIRIT_AQUA = new GlowDustOptions(0x66E0FF, 1.5F);
    private static final GlowDustOptions RESPAWN_VIOLET = new GlowDustOptions(0xB58CFF, 1.7F);
    private static final GlowDustOptions FIREWORK_GOLD = new GlowDustOptions(0xFFC23D, 1.9F);
    private static final GlowDustOptions FIREWORK_RED = new GlowDustOptions(0xFF4A5E, 1.7F);
    private static final GlowDustOptions FIREWORK_PALE = new GlowDustOptions(0xFFF3B0, 1.4F);

    // 哥伦比娅的月华与钟离的岩光。
    private static final GlowDustOptions MOON_PALE = new GlowDustOptions(0xF2ECFF, 1.9F);
    private static final GlowDustOptions MOON_VIOLET = new GlowDustOptions(0xC9B6FF, 1.6F);
    private static final GlowDustOptions EARTH_GOLD = new GlowDustOptions(0xE8C46A, 1.6F);

    // 桑多涅：淡蓝冰晶、雪白高光，以及掌心的机械冷光。
    private static final GlowDustOptions FROST_DEEP = new GlowDustOptions(0x6FB8FF, 2.2F);
    private static final GlowDustOptions FROST_LIGHT = new GlowDustOptions(0xAEE6FF, 2.0F);
    private static final GlowDustOptions FROST_WHITE = new GlowDustOptions(0xF2FCFF, 2.6F);
    private static final GlowDustOptions MECH_CYAN = new GlowDustOptions(0x8FF2FF, 1.5F);
    private static final GlowDustOptions MECH_GOLD = new GlowDustOptions(0xFFD27A, 1.3F);

    private NpcFx() {
    }

    /** 供其他角色模块直接取用全亮光尘。 */
    public static GlowDustOptions glow(int rgb, float scale) {
        return new GlowDustOptions(rgb, scale);
    }

    public static GlowDustOptions waterDeep() {
        return WATER_DEEP;
    }

    public static GlowDustOptions waterLight() {
        return WATER_LIGHT;
    }

    public static GlowDustOptions fireCore() {
        return FIRE_CORE;
    }

    public static GlowDustOptions fireGold() {
        return FIRE_GOLD;
    }

    public static GlowDustOptions earthGold() {
        return EARTH_GOLD;
    }

    public static GlowDustOptions moonPale() {
        return MOON_PALE;
    }

    public static GlowDustOptions moonViolet() {
        return MOON_VIOLET;
    }

    /**
     * 芙宁娜的水魔法射线：纯蓝与浅蓝交织的粗光束。
     * 中心是一条高亮浅蓝核心，外层两条纯蓝与浅蓝的光带缠绕，
     * 末端再炸开一片水光，保证仰射、俯射时整条轨迹都清晰可见。
     */
    public static void waterCast(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 0.01) {
            return;
        }
        Vec3 unit = direction.scale(1.0 / length);
        Vec3 side = unit.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize();
        Vec3 up = side.cross(unit).normalize();
        int steps = Math.min(64, Math.max(20, (int) (length * 6.0)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double radius = 0.34 * (1.0 - t * 0.35);
            double angle = t * Math.PI * 6.0;
            Vec3 center = from.add(direction.scale(t));
            Vec3 strandA = center
                    .add(side.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
            Vec3 strandB = center
                    .add(side.scale(Math.cos(angle + Math.PI) * radius))
                    .add(up.scale(Math.sin(angle + Math.PI) * radius));
            // 粗核心：浅蓝光斑叠在中心，让射线拥有实体般的粗细。
            level.sendParticles(WATER_PALE, center.x, center.y, center.z, 1, 0.05, 0.05, 0.05, 0.0);
            level.sendParticles(WATER_LIGHT, center.x, center.y, center.z, 1, 0.11, 0.11, 0.11, 0.0);
            // 双螺旋外缘：纯蓝与浅蓝各一条。
            level.sendParticles(WATER_DEEP, strandA.x, strandA.y, strandA.z, 1, 0.02, 0.02, 0.02, 0.0);
            level.sendParticles(WATER_LIGHT, strandB.x, strandB.y, strandB.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(WATER_DEEP, center.x, center.y, center.z, 1, 0.16, 0.16, 0.16, 0.0);
            }
            if (i % 5 == 0) {
                level.sendParticles(ParticleTypes.GLOW, strandB.x, strandB.y, strandB.z, 1, 0.04, 0.04, 0.04, 0.0);
                level.sendParticles(ParticleTypes.NAUTILUS, strandA.x, strandA.y, strandA.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if (i % 9 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }
        waterBurst(level, to);
        ring(level, from, 1.15, 20, WATER_DEEP);
        ring(level, from.add(0.0, 0.12, 0.0), 0.75, 16, WATER_LIGHT);
        ring(level, from.add(0.0, 0.22, 0.0), 0.45, 12, WATER_PALE);
    }

    public static void waterBurst(ServerLevel level, Vec3 pos) {
        level.sendParticles(WATER_LIGHT, pos.x, pos.y, pos.z, 44, 0.45, 0.4, 0.45, 0.06);
        level.sendParticles(WATER_DEEP, pos.x, pos.y, pos.z, 34, 0.55, 0.45, 0.55, 0.05);
        level.sendParticles(WATER_PALE, pos.x, pos.y + 0.1, pos.z, 22, 0.4, 0.35, 0.4, 0.04);
        level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y, pos.z, 20, 0.4, 0.32, 0.4, 0.08);
        level.sendParticles(ParticleTypes.GLOW, pos.x, pos.y + 0.2, pos.z, 12, 0.38, 0.32, 0.38, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.15, pos.z, 9, 0.24, 0.18, 0.24, 0.02);
        level.sendParticles(ParticleTypes.NAUTILUS, pos.x, pos.y + 0.2, pos.z, 7, 0.3, 0.25, 0.3, 0.02);
        ring(level, pos, 1.3, 20, WATER_DEEP);
        ring(level, pos.add(0.0, 0.1, 0.0), 0.85, 16, WATER_LIGHT);
    }

    public static void waterRing(ServerLevel level, Vec3 center) {
        ring(level, center, 1.9, 30, WATER_DEEP);
        ring(level, center.add(0.0, 0.08, 0.0), 1.35, 22, WATER_LIGHT);
        ring(level, center.add(0.0, 0.16, 0.0), 0.85, 16, WATER_PALE);
        level.sendParticles(WATER_LIGHT, center.x, center.y + 0.15, center.z, 20, 0.55, 0.15, 0.55, 0.04);
    }

    /**
     * 胡桃的火魔法射线：橙红核心与金色光带，外加真实的火焰粒子作点缀。
     */
    public static void fireCast(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 0.01) {
            return;
        }
        Vec3 unit = direction.scale(1.0 / length);
        Vec3 side = unit.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize();
        Vec3 up = side.cross(unit).normalize();
        int steps = Math.min(56, Math.max(18, (int) (length * 5.0)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double angle = t * Math.PI * 6.0;
            double radius = 0.3 * (1.0 - t * 0.3);
            Vec3 center = from.add(direction.scale(t));
            Vec3 strandA = center
                    .add(side.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
            Vec3 strandB = center
                    .add(side.scale(Math.cos(angle + Math.PI) * radius))
                    .add(up.scale(Math.sin(angle + Math.PI) * radius));
            level.sendParticles(FIRE_PALE, center.x, center.y, center.z, 1, 0.05, 0.05, 0.05, 0.0);
            level.sendParticles(FIRE_GOLD, center.x, center.y, center.z, 1, 0.1, 0.1, 0.1, 0.0);
            level.sendParticles(FIRE_CORE, strandA.x, strandA.y, strandA.z, 1, 0.02, 0.02, 0.02, 0.0);
            level.sendParticles(FIRE_GOLD, strandB.x, strandB.y, strandB.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 1, 0.1, 0.1, 0.1, 0.0);
            }
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, strandA.x, strandA.y, strandA.z,
                        1, 0.03, 0.03, 0.03, 0.0);
            }
            if (i % 9 == 0) {
                level.sendParticles(ParticleTypes.LAVA, strandB.x, strandB.y, strandB.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        fireBurst(level, to);
        ring(level, from, 1.05, 18, FIRE_CORE);
        ring(level, from.add(0.0, 0.1, 0.0), 0.65, 14, FIRE_GOLD);
    }

    public static void fireBurst(ServerLevel level, Vec3 pos) {
        level.sendParticles(FIRE_CORE, pos.x, pos.y, pos.z, 34, 0.45, 0.42, 0.45, 0.06);
        level.sendParticles(FIRE_GOLD, pos.x, pos.y, pos.z, 30, 0.5, 0.4, 0.5, 0.05);
        level.sendParticles(FIRE_PALE, pos.x, pos.y + 0.1, pos.z, 20, 0.38, 0.34, 0.38, 0.04);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 24, 0.35, 0.4, 0.35, 0.05);
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 22, 0.45, 0.35, 0.45, 0.04);
        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 5, 0.3, 0.25, 0.3, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.3, pos.z, 9, 0.3, 0.3, 0.3, 0.03);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 0.4, pos.z, 6, 0.3, 0.3, 0.3, 0.05);
        ring(level, pos, 1.55, 24, FIRE_CORE);
        ring(level, pos.add(0.0, 0.1, 0.0), 0.95, 18, FIRE_GOLD);
        ring(level, pos.add(0.0, 0.2, 0.0), 0.55, 12, FIRE_PALE);
    }

    /** 芙宁娜的蛋糕与胡桃的小聚：粉色光尘加爱心。 */
    public static void cakeBurst(ServerLevel level, Vec3 pos) {
        level.sendParticles(CAKE_PINK, pos.x, pos.y + 0.5, pos.z, 18, 0.38, 0.38, 0.38, 0.02);
        level.sendParticles(WATER_PALE, pos.x, pos.y + 0.4, pos.z, 8, 0.3, 0.3, 0.3, 0.01);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.35, pos.z, 12, 0.35, 0.35, 0.35, 0.0);
        level.sendParticles(ParticleTypes.HEART, pos.x, pos.y + 0.8, pos.z, 4, 0.25, 0.25, 0.25, 0.0);
        ring(level, pos, 0.75, 16, CAKE_PINK);
    }

    public static void spiritSummon(ServerLevel level, Vec3 pos) {
        level.sendParticles(SPIRIT_AQUA, pos.x, pos.y + 0.5, pos.z, 20, 0.32, 0.38, 0.32, 0.03);
        level.sendParticles(WATER_PALE, pos.x, pos.y + 0.6, pos.z, 10, 0.3, 0.35, 0.3, 0.02);
        level.sendParticles(ParticleTypes.NAUTILUS, pos.x, pos.y + 0.6, pos.z, 5, 0.25, 0.3, 0.25, 0.02);
        level.sendParticles(ParticleTypes.GLOW, pos.x, pos.y + 0.5, pos.z, 8, 0.3, 0.3, 0.3, 0.01);
        ring(level, pos.add(0.0, 0.2, 0.0), 0.85, 18, WATER_LIGHT);
    }

    public static void spiritTrail(ServerLevel level, Entity entity) {
        level.sendParticles(SPIRIT_AQUA, entity.getX(), entity.getY() + 0.3, entity.getZ(),
                1, 0.1, 0.1, 0.1, 0.0);
        if (entity.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.GLOW, entity.getX(), entity.getY() + 0.35, entity.getZ(),
                    1, 0.08, 0.08, 0.08, 0.0);
        }
    }

    public static void spiritDissolve(ServerLevel level, Entity entity) {
        level.sendParticles(SPIRIT_AQUA, entity.getX(), entity.getY() + 0.35, entity.getZ(),
                16, 0.3, 0.3, 0.3, 0.03);
        level.sendParticles(WATER_LIGHT, entity.getX(), entity.getY() + 0.2, entity.getZ(),
                10, 0.28, 0.22, 0.28, 0.02);
        level.sendParticles(ParticleTypes.NAUTILUS, entity.getX(), entity.getY() + 0.45, entity.getZ(),
                4, 0.25, 0.25, 0.25, 0.02);
    }

    public static void respawnBurst(ServerLevel level, Vec3 pos) {
        level.sendParticles(RESPAWN_VIOLET, pos.x, pos.y + 1.0, pos.z, 26, 0.3, 0.6, 0.3, 0.04);
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 16, 0.3, 0.6, 0.3, 0.05);
        ring(level, pos, 0.95, 20, WATER_PALE);
        ring(level, pos.add(0.0, 1.8, 0.0), 0.75, 16, RESPAWN_VIOLET);
    }

    /** A burning arrow leaves a short streak of fire from the bow. */
    public static void arrowLaunch(ServerLevel level, Vec3 eye, Vec3 aim) {
        Vec3 unit = aim.normalize();
        for (int i = 1; i <= 9; i++) {
            Vec3 point = eye.add(unit.scale(i * 0.55));
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0);
            level.sendParticles(FIRE_GOLD, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, point.x, point.y, point.z,
                        1, 0.03, 0.03, 0.03, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.LAVA, eye.x, eye.y, eye.z, 3, 0.08, 0.08, 0.08, 0.0);
        level.sendParticles(FIRE_PALE, eye.x, eye.y, eye.z, 5, 0.12, 0.12, 0.12, 0.02);
    }

    /** The flame arrow keeps shedding sparks for every tick it is in flight. */
    public static void fieryArrowTrail(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.008);
        level.sendParticles(FIRE_GOLD, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
        if (level.getRandom().nextInt(4) == 0) {
            level.sendParticles(FIRE_PALE, pos.x, pos.y, pos.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        if (level.getRandom().nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    /** One tick of the flying firework rocket: spark, flame and an occasional ember. */
    public static void rocketTrail(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 2, 0.04, 0.04, 0.04, 0.015);
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 1, 0.03, 0.03, 0.03, 0.005);
        level.sendParticles(FIRE_GOLD, pos.x, pos.y, pos.z, 1, 0.03, 0.03, 0.03, 0.0);
        if (level.getRandom().nextInt(4) == 0) {
            level.sendParticles(FIREWORK_PALE, pos.x, pos.y, pos.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
        if (level.getRandom().nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
    }

    /**
     * A firework rocket streaking towards its target. The trail is slightly
     * arched so it reads as a launched rocket rather than a straight laser.
     */
    public static void fireworkTrail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 0.01) {
            return;
        }
        int steps = Math.min(48, Math.max(16, (int) (length * 4.0)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            // The rocket rises a little before settling onto the aim line.
            double lift = Math.sin(t * Math.PI) * Math.min(0.9, length * 0.03);
            Vec3 point = from.add(direction.scale(t)).add(0.0, lift, 0.0);
            level.sendParticles(ParticleTypes.FIREWORK, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.01);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.01, 0.01, 0.01, 0.0);
                level.sendParticles(FIRE_GOLD, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, point.x, point.y, point.z,
                        1, 0.03, 0.03, 0.03, 0.0);
            }
            if (i % 7 == 0) {
                level.sendParticles(FIRE_PALE, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    /**
     * The full firework bloom: white sparks, fire, and three rings of coloured
     * glow dust so the burst stays visible in daylight as well as at night.
     */
    public static void fireworkBurst(ServerLevel level, Vec3 center) {
        fireworkBurst(level, center, 1.0F);
    }

    /** Same bloom, widened for the combat rocket's larger blast radius. */
    public static void fireworkBurst(ServerLevel level, Vec3 center, float scale) {
        float s = Math.max(0.5F, scale);
        level.sendParticles(ParticleTypes.FIREWORK, center.x, center.y, center.z,
                (int) (64 * s), 1.1 * s, 0.9 * s, 1.1 * s, 0.14);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                (int) (22 * s), 0.8 * s, 0.7 * s, 0.8 * s, 0.08);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z,
                (int) (30 * s), 1.0 * s, 0.8 * s, 1.0 * s, 0.05);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y + 0.2, center.z,
                (int) (12 * s), 0.7 * s, 0.6 * s, 0.7 * s, 0.04);
        level.sendParticles(FIREWORK_GOLD, center.x, center.y, center.z,
                (int) (50 * s), 1.0 * s, 0.8 * s, 1.0 * s, 0.12);
        level.sendParticles(FIREWORK_RED, center.x, center.y, center.z,
                (int) (34 * s), 1.1 * s, 0.85 * s, 1.1 * s, 0.1);
        level.sendParticles(FIREWORK_PALE, center.x, center.y, center.z,
                (int) (30 * s), 0.9 * s, 0.75 * s, 0.9 * s, 0.08);
        glowRing(level, center, 2.1 * s, 36, 0xFFB300, 1.8F);
        glowRing(level, center, 3.1 * s, 44, 0xFF3355, 1.5F);
        glowRing(level, center.add(0.0, 0.35, 0.0), 1.15 * s, 26, 0xFFF3B0, 2.0F);
        ring(level, center, 1.8 * s, 24, ParticleTypes.SMALL_FLAME);
    }

    /** 月影悬停时的呼吸光尘：双层旋转光环、垂落月华与星点，托住巨大月盘。 */
    public static void moonPhantomAura(ServerLevel level, double x, double y, double z, float scale) {
        double radius = Math.max(1.5, scale * 0.42);
        GlowDustOptions pale = new GlowDustOptions(0xF2ECFF, 1.9F);
        GlowDustOptions violet = new GlowDustOptions(0xC9B6FF, 1.5F);
        double spin = level.getGameTime() * 0.012;
        int points = 22;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points + spin;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            double py = y + Math.sin(angle * 1.7) * radius * 0.3;
            level.sendParticles(pale, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            // 内圈反向旋转的淡紫光环，让月盘看起来有两层光晕。
            double inner = radius * 0.72;
            double angle2 = Math.PI * 2.0 * i / points - spin * 1.6;
            level.sendParticles(violet,
                    x + Math.cos(angle2) * inner,
                    y + Math.cos(angle2 * 2.3) * radius * 0.22,
                    z + Math.sin(angle2) * inner,
                    1, 0.0, 0.0, 0.0, 0.0);
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.0, 0.0, 0.0, 0.002);
            }
            if (i % 6 == 0) {
                // 自月缘垂落的月华：缓慢下坠的光点。
                level.sendParticles(ParticleTypes.WHITE_ASH, px, py - radius * 0.2, pz,
                        1, 0.02, 0.0, 0.02, -0.02);
            }
        }
        level.sendParticles(pale, x, y, z, 8, radius * 0.5, radius * 0.4, radius * 0.5, 0.01);
        level.sendParticles(violet, x, y, z, 6, radius * 0.4, radius * 0.35, radius * 0.4, 0.01);
        level.sendParticles(ParticleTypes.GLOW, x, y, z, 4, radius * 0.35, radius * 0.3, radius * 0.35, 0.0);
    }

    /** 地面月华池：领域边界上一圈缓慢流转的苍白光尘，标示月光落下的范围。 */
    public static void moonDomainGround(ServerLevel level, double x, double y, double z, double radius) {
        GlowDustOptions pale = new GlowDustOptions(0xEFE8FF, 1.4F);
        double spin = level.getGameTime() * 0.006;
        int points = 30;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points + spin;
            level.sendParticles(pale,
                    x + Math.cos(angle) * radius, y + 0.12, z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
            if (i % 5 == 0) {
                double inner = radius * (0.3 + 0.55 * ((i / 5) % 4) / 3.0);
                level.sendParticles(ParticleTypes.WHITE_ASH,
                        x + Math.cos(-angle * 1.3) * inner, y + 0.2, z + Math.sin(-angle * 1.3) * inner,
                        1, 0.0, 0.02, 0.0, 0.006);
            }
        }
    }

    /** 月光脉冲：三重扩散光环加自地面升起的月华光柱。 */
    public static void moonPulse(ServerLevel level, double x, double y, double z, double radius) {
        GlowDustOptions pale = new GlowDustOptions(0xF5F1FF, 2.2F);
        GlowDustOptions violet = new GlowDustOptions(0xCDBBFF, 1.7F);
        int points = 56;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            level.sendParticles(pale, px, y + 0.1, pz, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, px, y + 0.2, pz, 1, 0.0, 0.03, 0.0, 0.012);
            // 第二、三重内环：脉冲不是一条线，而是一片漾开的光。
            level.sendParticles(violet,
                    x + Math.cos(angle + 0.06) * radius * 0.78, y + 0.15,
                    z + Math.sin(angle + 0.06) * radius * 0.78, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(pale,
                        x + Math.cos(angle - 0.05) * radius * 0.55, y + 0.1,
                        z + Math.sin(angle - 0.05) * radius * 0.55, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        for (int i = 0; i < 26; i++) {
            double angle = Math.PI * 2.0 * i / 26;
            double py = y + Math.sin(angle) * radius * 0.55;
            double pz = z + Math.cos(angle) * radius * 0.55;
            level.sendParticles(ParticleTypes.WHITE_ASH, x, py, pz, 1, 0.0, 0.0, 0.0, 0.012);
        }
        // 自领域中心升起的月华光柱，把地面与月盘连成一体。
        for (int i = 0; i < 10; i++) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.3 + i * 0.55, z,
                    1, radius * 0.12, 0.0, radius * 0.12, 0.03);
            level.sendParticles(violet, x, y + 0.3 + i * 0.55, z,
                    1, radius * 0.1, 0.0, radius * 0.1, 0.02);
        }
        level.sendParticles(ParticleTypes.GLOW, x, y + 0.4, z, 8, radius * 0.4, 0.3, radius * 0.4, 0.0);
    }

    /** 被月光锁定的瞬间：苍白的光从目标身上炸开。 */
    public static void paleStrike(ServerLevel level, Entity entity) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.55;
        double z = entity.getZ();
        level.sendParticles(new GlowDustOptions(0xF6F2FF, 2.2F), x, y, z, 18, 0.3, 0.45, 0.3, 0.03);
        level.sendParticles(new GlowDustOptions(0xCDBBFF, 1.6F), x, y, z, 12, 0.32, 0.42, 0.32, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.24, 0.36, 0.24, 0.03);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 10, 0.3, 0.4, 0.3, 0.02);
        ring(level, new Vec3(x, entity.getY() + 0.05, z), 0.95, 16, WATER_PALE);
    }

    /** 月影降临：从目标头顶垂落的银白月华。 */
    public static void moonSummon(ServerLevel level, Vec3 center, float scale) {
        double radius = Math.max(2.0, scale * 0.5);
        for (int i = 0; i < 64; i++) {
            double angle = Math.PI * 2.0 * i / 64;
            double px = center.x + Math.cos(angle) * radius;
            double pz = center.z + Math.sin(angle) * radius;
            level.sendParticles(new GlowDustOptions(0xF4EEFF, 2.0F), px, center.y - 1.2, pz, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, px, center.y - 1.0, pz, 1, 0.0, 0.04, 0.0, 0.01);
        }
        // 自月盘垂到地面的螺旋月华幕：把“月亮落下来”的过程画完整。
        int steps = (int) (scale * 1.6);
        for (int i = 0; i < steps; i++) {
            double y = center.y - 1.5 - i * 0.42;
            double radius2 = radius * (1.0 - i / (double) (steps + 10));
            double angle = i * 0.55;
            level.sendParticles(ParticleTypes.WHITE_ASH,
                    center.x + Math.cos(angle) * radius2, y, center.z + Math.sin(angle) * radius2,
                    1, 0.02, 0.02, 0.02, -0.01);
            if (i % 3 == 0) {
                level.sendParticles(new GlowDustOptions(0xD9CCFF, 1.5F),
                        center.x + Math.cos(-angle) * radius2 * 0.8, y,
                        center.z + Math.sin(-angle) * radius2 * 0.8,
                        1, 0.02, 0.02, 0.02, -0.01);
            }
        }
        level.sendParticles(new GlowDustOptions(0xF4EEFF, 2.2F), center.x, center.y, center.z,
                20, radius * 0.4, radius * 0.35, radius * 0.4, 0.02);
        level.sendParticles(ParticleTypes.GLOW, center.x, center.y, center.z,
                16, radius * 0.4, radius * 0.35, radius * 0.4, 0.02);
    }

    /** 治疗光环：温柔的月光落在友方身上。 */
    public static void moonHeal(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.6, pos.z, 10, 0.35, 0.4, 0.35, 0.0);
        level.sendParticles(ParticleTypes.HEART, pos.x, pos.y + 1.1, pos.z, 2, 0.22, 0.22, 0.22, 0.0);
        level.sendParticles(new GlowDustOptions(0xEFE7FF, 1.6F), pos.x, pos.y + 0.5, pos.z,
                10, 0.32, 0.3, 0.32, 0.01);
        ring(level, pos.add(0.0, 0.1, 0.0), 0.85, 18, WATER_PALE);
    }

    /**
     * 桑多涅的冰霜射线：光束主体由客户端以连续光柱绘制，这里只补充少量飘散的寒气。
     *
     * <p>沿弹道每两三格撒一两粒淡蓝光尘，偶尔夹一片雪花或电火花，让连续光柱
     * 的边缘有呼吸感；不再沿光束密集铺粒子，避免整条射线看起来像断断续续的粒子流。</p>
     */
    public static void frostRay(ServerLevel level, Vec3 from, Vec3 to, int tick) {
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 0.01) {
            return;
        }
        int motes = Math.min(10, Math.max(2, (int) (length / 3.2)));
        for (int i = 0; i < motes; i++) {
            double t = (i + 0.5) / motes;
            Vec3 point = from.add(direction.scale(t));
            double jitter = 0.12 + t * 0.1;
            level.sendParticles(FROST_LIGHT, point.x, point.y, point.z,
                    1, jitter, jitter, jitter, 0.004);
            if (i % 2 == 0) {
                level.sendParticles(FROST_DEEP, point.x, point.y, point.z,
                        1, jitter * 1.3, jitter * 1.3, jitter * 1.3, 0.002);
            }
        }
        if (tick % 5 == 0) {
            Vec3 point = from.add(direction.scale(level.getRandom().nextDouble()));
            level.sendParticles(ParticleTypes.SNOWFLAKE, point.x, point.y, point.z,
                    1, 0.08, 0.08, 0.08, 0.01);
        }
        if (tick % 3 == 0) {
            Vec3 point = from.add(direction.scale(level.getRandom().nextDouble()));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z,
                    1, 0.05, 0.05, 0.05, 0.0);
        }
        // 射线尽头的一小团寒气收尾，提示落点。
        level.sendParticles(FROST_LIGHT, to.x, to.y, to.z, 3, 0.16, 0.16, 0.16, 0.01);
        level.sendParticles(FROST_WHITE, to.x, to.y, to.z, 1, 0.1, 0.1, 0.1, 0.0);
    }

    /** 冰霜命中：淡蓝与雪白的寒气炸开，带一圈冰晶与雪花。 */
    public static void frostBurst(ServerLevel level, Vec3 pos, float scale) {
        float s = Math.max(0.4F, scale);
        level.sendParticles(FROST_WHITE, pos.x, pos.y, pos.z, (int) (26 * s), 0.4 * s, 0.36 * s, 0.4 * s, 0.05);
        level.sendParticles(FROST_LIGHT, pos.x, pos.y, pos.z, (int) (34 * s), 0.5 * s, 0.42 * s, 0.5 * s, 0.05);
        level.sendParticles(FROST_DEEP, pos.x, pos.y, pos.z, (int) (22 * s), 0.55 * s, 0.46 * s, 0.55 * s, 0.04);
        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, (int) (20 * s), 0.4 * s, 0.38 * s, 0.4 * s, 0.03);
        level.sendParticles(ParticleTypes.WHITE_ASH, pos.x, pos.y + 0.1, pos.z, (int) (14 * s), 0.36 * s, 0.32 * s, 0.36 * s, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.12, pos.z, (int) (8 * s), 0.26 * s, 0.24 * s, 0.26 * s, 0.02);
        ring(level, pos, 1.15 * s, 18, FROST_LIGHT);
        ring(level, pos.add(0.0, 0.1, 0.0), 0.7 * s, 14, FROST_WHITE);
    }

    /**
     * 掌心的机械光环：两团冷光在手掌位置旋转，外圈偶尔闪过金色电弧。
     * 光环平面始终垂直于射线方向，看起来像是把寒气从掌心“推”出去。
     */
    public static void frostPalmHalo(ServerLevel level, Vec3 leftPalm, Vec3 rightPalm, Vec3 direction, int tick) {
        Vec3 unit = direction.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        Vec3 side = unit.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize();
        Vec3 up = side.cross(unit).normalize();
        double spin = tick * 0.55;
        halo(level, leftPalm, side, up, spin);
        halo(level, rightPalm, side, up, spin + Math.PI);
    }

    private static void halo(ServerLevel level, Vec3 center, Vec3 side, Vec3 up, double spin) {
        double radius = 0.17;
        int points = 8;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points + spin;
            double x = center.x + side.x * Math.cos(angle) * radius + up.x * Math.sin(angle) * radius;
            double y = center.y + side.y * Math.cos(angle) * radius + up.y * Math.sin(angle) * radius;
            double z = center.z + side.z * Math.cos(angle) * radius + up.z * Math.sin(angle) * radius;
            level.sendParticles(MECH_CYAN, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(MECH_GOLD, x, y, z, 1, 0.01, 0.01, 0.01, 0.0);
            }
        }
        level.sendParticles(FROST_WHITE, center.x, center.y, center.z, 2, 0.04, 0.04, 0.04, 0.0);
        if (level.getRandom().nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z,
                    1, 0.06, 0.06, 0.06, 0.0);
        }
    }

    private static void glowRing(ServerLevel level, Vec3 center, double radius, int points,
                                 int color, float scale) {
        GlowDustOptions dust = new GlowDustOptions(color, scale);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            level.sendParticles(dust, center.x + Math.cos(angle) * radius, center.y,
                    center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void ring(ServerLevel level, Vec3 center, double radius, int points, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y,
                    center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
