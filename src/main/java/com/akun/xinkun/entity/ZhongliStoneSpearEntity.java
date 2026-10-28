package com.akun.xinkun.entity;

import com.akun.xinkun.particle.GlowDustOptions;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 钟离唤下的岩枪。自十余格高空笔直落下，枪身贯穿沿途所有敌对生物，
 * 每一根只能命中同一目标一次；触地后钉在地面上片刻，再沉入土里消散。
 *
 * <p>实体位置取枪身正中心，包围盒高五格。渲染器按同一套坐标把枪尖画在
 * 下方两格半处，所以落点判定与视觉完全一致。</p>
 */
public class ZhongliStoneSpearEntity extends Entity {
    /** 视觉与判定共用的半高：枪长五格。 */
    public static final float HALF_HEIGHT = 2.5F;
    /** 起手高度：枪尖离地十一格。 */
    public static final float FALL_HEIGHT = 11.0F;
    private static final float SPEAR_DAMAGE = 24.0F;
    private static final double HIT_RADIUS = 1.35;
    /** 每 tick 下落距离，十一格大约十四 tick 落完。 */
    private static final double FALL_SPEED = 0.82;
    private static final int LINGER_TICKS = 70;
    private static final int SINK_START = 42;

    private final Set<UUID> struck = new HashSet<>();
    private LivingEntity owner;
    private double groundY;
    private int delayTicks;
    private int life;
    private boolean landed;

    public ZhongliStoneSpearEntity(EntityType<? extends ZhongliStoneSpearEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setSilent(true);
        this.setPermanentlyInvulnerable(true);
    }

    /**
     * @param x       落点 X
     * @param groundY 落点所在地面高度（枪尖到达这里即视为插地）
     * @param z       落点 Z
     * @param delay   延迟多少 tick 才开始下落，用于让一排岩枪依次落地
     * @param owner   施法者，用于伤害归属
     */
    public void configure(double x, double groundY, double z, int delay, LivingEntity owner) {
        this.groundY = groundY;
        this.delayTicks = Math.max(0, delay);
        this.owner = owner;
        this.setPos(x, groundY + FALL_HEIGHT, z);
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
        if (this.delayTicks > 0) {
            this.delayTicks--;
            // 尚未落下时，枪尖先聚起一圈岩金色微光。
            if (this.delayTicks % 4 == 0) {
                level.sendParticles(new GlowDustOptions(0xE8C46A, 1.0F),
                        this.getX(), this.getY() - HALF_HEIGHT, this.getZ(),
                        3, 0.22, 0.3, 0.22, 0.01);
            }
            return;
        }
        if (!this.landed) {
            double nextY = this.getY() - FALL_SPEED;
            if (nextY - HALF_HEIGHT <= this.groundY) {
                nextY = this.groundY + HALF_HEIGHT;
                this.landed = true;
            }
            this.setPos(this.getX(), nextY, this.getZ());
            if (this.landed) {
                this.onLand(level);
            } else {
                this.damageSweep(level);
                if (level.getGameTime() % 2 == 0) {
                    level.sendParticles(new GlowDustOptions(0xE8C46A, 1.0F),
                            this.getX(), this.getY() + HALF_HEIGHT * 0.8, this.getZ(),
                            1, 0.1, 0.3, 0.1, 0.01);
                }
            }
            return;
        }
        this.life++;
        if (this.life >= SINK_START) {
            this.setPos(this.getX(), this.getY() - 0.06, this.getZ());
        }
        if (this.life >= LINGER_TICKS) {
            this.discard();
        }
    }

    /** 枪身在下落过程中扫过的一整段圆柱：沿途每个敌人只挨一下。 */
    private void damageSweep(ServerLevel level) {
        AABB area = new AABB(
                this.getX() - HIT_RADIUS, this.getY() - HALF_HEIGHT, this.getZ() - HIT_RADIUS,
                this.getX() + HIT_RADIUS, this.getY() + HALF_HEIGHT + 1.0, this.getZ() + HIT_RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!(victim instanceof Enemy) || !victim.isAlive()) {
                continue;
            }
            if (this.struck.add(victim.getUUID())) {
                this.strike(level, victim, SPEAR_DAMAGE);
            }
        }
    }

    private void onLand(ServerLevel level) {
        level.playSound(null, this.getX(), this.groundY, this.getZ(),
                SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, 1.0F, 0.62F);
        level.sendParticles(new GlowDustOptions(0xE8C46A, 1.4F),
                this.getX(), this.groundY + 0.1, this.getZ(), 14, 0.35, 0.12, 0.35, 0.03);
        level.sendParticles(ParticleTypes.POOF,
                this.getX(), this.groundY + 0.15, this.getZ(), 6, 0.3, 0.08, 0.3, 0.02);
        // 插地瞬间再补一次判定，确保擦着地面进来的敌人也被刺中。
        this.damageSweep(level);
    }

    private void strike(ServerLevel level, LivingEntity victim, float damage) {
        DamageSource source = this.owner != null && this.owner.isAlive()
                ? level.damageSources().mobAttack(this.owner)
                : level.damageSources().magic();
        victim.hurtServer(level, source, damage);
        level.sendParticles(new GlowDustOptions(0xE8C46A, 1.2F),
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                8, 0.25, 0.35, 0.25, 0.02);
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
        return distanceSqr < 128.0 * 128.0;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putDouble("GroundY", this.groundY);
        output.putInt("Delay", this.delayTicks);
        output.putInt("Life", this.life);
        output.putBoolean("Landed", this.landed);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.groundY = input.getDoubleOr("GroundY", this.getY());
        this.delayTicks = input.getIntOr("Delay", 0);
        this.life = input.getIntOr("Life", 0);
        this.landed = input.getBooleanOr("Landed", false);
    }
}
