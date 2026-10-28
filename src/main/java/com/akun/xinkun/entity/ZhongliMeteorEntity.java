package com.akun.xinkun.entity;

import com.akun.xinkun.particle.GlowDustOptions;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 钟离的天星：一颗巨大的基岩陨石，从落点斜上方的天空坠下。
 *
 * <p>撞击只对敌对生物生效，不破坏地形。落点中心造成一百二十点伤害，
 * 随半径线性衰减到边缘的三十六点，并把命中者向外掀开。命中瞬间整个
 * 爆炸圆范围内迸起向外飞散的岩金尘埃，余波尘埃环还会持续外扩，
 * 把九格的杀伤范围"画"给在场所有人看。</p>
 */
public class ZhongliMeteorEntity extends Entity {
    public static final float BLAST_RADIUS = 9.0F;
    public static final float CENTER_DAMAGE = 120.0F;
    public static final float EDGE_DAMAGE = 36.0F;

    /** 起落点偏移：东北方高空，形成一条明显的斜向轨迹。 */
    private static final double SPAWN_OFFSET_X = 15.0;
    private static final double SPAWN_OFFSET_Y = 30.0;
    private static final double SPAWN_OFFSET_Z = 15.0;
    private static final int FLIGHT_TICKS = 34;
    private static final int IMPACT_LINGER = 12;

    private Vec3 impact = Vec3.ZERO;
    private LivingEntity owner;
    private int age;
    private boolean impacted;
    private int impactTicks;

    public ZhongliMeteorEntity(EntityType<? extends ZhongliMeteorEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setSilent(true);
        this.setPermanentlyInvulnerable(true);
    }

    public void configure(double x, double groundY, double z, LivingEntity owner) {
        this.impact = new Vec3(x, groundY, z);
        this.owner = owner;
        this.age = 0;
        this.impacted = false;
        this.impactTicks = 0;
        this.setPos(x + SPAWN_OFFSET_X, groundY + SPAWN_OFFSET_Y, z + SPAWN_OFFSET_Z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.impacted) {
            this.impactTicks++;
            this.scatterRing(level);
            if (this.impactTicks >= IMPACT_LINGER) {
                this.discard();
            }
            return;
        }
        this.age++;
        if (this.age >= FLIGHT_TICKS) {
            this.setPos(this.impact.x, this.impact.y, this.impact.z);
            this.impact(level);
            return;
        }
        double t = (double) this.age / FLIGHT_TICKS;
        Vec3 start = this.impact.add(SPAWN_OFFSET_X, SPAWN_OFFSET_Y, SPAWN_OFFSET_Z);
        Vec3 point = start.lerp(this.impact, t);
        this.setPos(point.x, point.y, point.z);
        this.trail(level);
    }

    private void trail(ServerLevel level) {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 1.1, 1.1, 1.1, 0.02);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 4, 0.9, 0.9, 0.9, 0.02);
        level.sendParticles(new GlowDustOptions(0xE8C46A, 1.6F), x, y, z,
                4, 1.2, 1.2, 1.2, 0.03);
        if (this.age % 3 == 0) {
            level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.6, 0.6, 0.6, 0.0);
        }
    }

    private void impact(ServerLevel level) {
        this.impacted = true;
        double x = this.impact.x;
        double y = this.impact.y;
        double z = this.impact.z;
        level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY,
                SoundSource.HOSTILE, 3.0F, 0.72F);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 2.4F, 0.55F);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1.0, z, 2, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 1.0, z, 12, 2.4, 0.8, 2.4, 0.06);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.2, z, 60, 3.6, 1.4, 3.6, 0.12);
        level.sendParticles(ParticleTypes.LAVA, x, y + 0.6, z, 26, 3.0, 0.5, 3.0, 0.2);
        level.sendParticles(new GlowDustOptions(0xFFB300, 2.2F),
                x, y + 1.0, z, 90, 4.0, 1.2, 4.0, 0.22);
        level.sendParticles(new GlowDustOptions(0xFFE08A, 2.0F),
                x, y + 1.0, z, 50, 3.2, 1.0, 3.2, 0.16);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BEDROCK.defaultBlockState()),
                x, y + 0.8, z, 60, 3.2, 0.9, 3.2, 0.16);
        // 贴地扩散的冲击环，让九格半径在视觉上读得出来。
        GlowDustOptions ringDust = new GlowDustOptions(0xE8C46A, 1.5F);
        for (int i = 0; i < 72; i++) {
            double angle = Math.PI * 2.0 * i / 72.0;
            double px = x + Math.cos(angle) * BLAST_RADIUS;
            double pz = z + Math.sin(angle) * BLAST_RADIUS;
            level.sendParticles(ringDust, px, y + 0.2, pz, 1, 0.0, 0.35, 0.0, 0.05);
        }
        // 范围尘埃迸溅：整个九格圆面内随机扬起向外飞散的岩金尘团，
        // 越靠外的尘团初速越大，读得出冲击波从落点推出去的方向。
        GlowDustOptions splash = new GlowDustOptions(0xE8C46A, 1.2F);
        for (int i = 0; i < 48; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = BLAST_RADIUS * Math.sqrt(level.getRandom().nextDouble());
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            double py = y + 0.1 + level.getRandom().nextDouble() * 0.5;
            level.sendParticles(splash, px, py, pz, 2,
                    0.25, 0.15, 0.25, 0.10 + 0.18 * (radius / BLAST_RADIUS));
        }

        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.blastBox())) {
            if (!(victim instanceof Enemy) || !victim.isAlive()) {
                continue;
            }
            double dx = victim.getX() - x;
            double dz = victim.getZ() - z;
            double dy = Math.max(0.0, victim.getY() - y);
            double distance = Math.sqrt(dx * dx + dz * dz + dy * dy * 0.35);
            if (distance > BLAST_RADIUS) {
                continue;
            }
            float falloff = (float) (distance / BLAST_RADIUS);
            float damage = CENTER_DAMAGE + (EDGE_DAMAGE - CENTER_DAMAGE) * falloff;
            DamageSource source = this.owner != null && this.owner.isAlive()
                    ? level.damageSources().mobAttack(this.owner)
                    : level.damageSources().magic();
            victim.hurtServer(level, source, damage);
            double push = 1.6 * (1.0 - falloff) + 0.35;
            if (dx * dx + dz * dz > 1.0E-4) {
                Vec3 away = new Vec3(dx, 0.0, dz).normalize().scale(push);
                victim.push(away.x, 0.55 * (1.0 - falloff) + 0.18, away.z);
            }
            level.sendParticles(new GlowDustOptions(0xE8C46A, 1.4F),
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    10, 0.3, 0.4, 0.3, 0.03);
        }
    }

    /**
     * 撞击余波：尘埃环在滞留期内继续向外迸散，让范围尘埃的"迸溅"
     * 不只是一帧的爆点，而是能看见的一圈推出去的马。
     */
    private void scatterRing(ServerLevel level) {
        double r = BLAST_RADIUS * (1.0 + 0.35 * this.impactTicks / IMPACT_LINGER);
        GlowDustOptions dust = new GlowDustOptions(0xD9B25A, 1.0F);
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2.0 * i / 40.0 + this.impactTicks * 0.07;
            level.sendParticles(dust,
                    this.impact.x + Math.cos(angle) * r,
                    this.impact.y + 0.12,
                    this.impact.z + Math.sin(angle) * r,
                    1, 0.10, 0.06, 0.10, 0.05);
        }
    }

    private net.minecraft.world.phys.AABB blastBox() {
        return new net.minecraft.world.phys.AABB(
                this.impact.x - BLAST_RADIUS, this.impact.y - 3.0, this.impact.z - BLAST_RADIUS,
                this.impact.x + BLAST_RADIUS, this.impact.y + 7.0, this.impact.z + BLAST_RADIUS);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 192.0 * 192.0;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putDouble("ImpactX", this.impact.x);
        output.putDouble("ImpactY", this.impact.y);
        output.putDouble("ImpactZ", this.impact.z);
        output.putInt("Age", this.age);
        output.putBoolean("Impacted", this.impacted);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.impact = new Vec3(
                input.getDoubleOr("ImpactX", this.getX()),
                input.getDoubleOr("ImpactY", this.getY()),
                input.getDoubleOr("ImpactZ", this.getZ()));
        this.age = input.getIntOr("Age", 0);
        this.impacted = input.getBooleanOr("Impacted", false);
    }
}
