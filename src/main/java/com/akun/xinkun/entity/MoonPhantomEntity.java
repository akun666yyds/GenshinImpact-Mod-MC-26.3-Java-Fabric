package com.akun.xinkun.entity;

import com.akun.xinkun.effect.MoonPaleEffect;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModEffects;
import com.akun.xinkun.registry.ModEntities;
import com.akun.xinkun.registry.ModSounds;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * 哥伦比娅召唤的巨型月亮幻影。实体本身没有碰撞和伤害判定，
 * 真正的攻击来自月光脉冲：范围内的敌人会被染上苍白月光，
 * 随后在露天的夜色里持续受到溺水伤害。
 *
 * <p>月相在召唤时锁定为当晚真实的月相，由客户端渲染器按 4×2
 * 月相图集取样，所以每一晚看到的月亮形状都不同。</p>
 */
public class MoonPhantomEntity extends Entity {
    public static final int DEFAULT_LIFETIME = 140;
    public static final float DEFAULT_SCALE = 18.0F;
    /** 月轮每次命中都重新挂满级虚弱，持续十二秒，可被后续命中刷新。 */
    private static final int WEAKNESS_DURATION = 240;
    private static final int WEAKNESS_MAX_AMPLIFIER = 255;

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(MoonPhantomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(MoonPhantomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX_LIFE =
            SynchedEntityData.defineId(MoonPhantomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(MoonPhantomEntity.class, EntityDataSerializers.FLOAT);
    /** 月盘正下方地面的高度：月光脉冲落在这里，而不是悬在半空。 */
    private static final EntityDataAccessor<Float> DATA_GROUND_Y =
            SynchedEntityData.defineId(MoonPhantomEntity.class, EntityDataSerializers.FLOAT);

    public MoonPhantomEntity(EntityType<? extends MoonPhantomEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setPermanentlyInvulnerable(true);
        this.setSilent(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_LIFE, 0);
        builder.define(DATA_MAX_LIFE, DEFAULT_LIFETIME);
        builder.define(DATA_SCALE, DEFAULT_SCALE);
        builder.define(DATA_GROUND_Y, 0.0F);
    }

    public void configure(int phase, float scale, int lifetime, float groundY) {
        this.entityData.set(DATA_PHASE, Math.floorMod(phase, 8));
        this.entityData.set(DATA_SCALE, scale);
        this.entityData.set(DATA_MAX_LIFE, lifetime);
        this.entityData.set(DATA_LIFE, 0);
        this.entityData.set(DATA_GROUND_Y, groundY);
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public int getLife() {
        return this.entityData.get(DATA_LIFE);
    }

    public int getMaxLife() {
        return this.entityData.get(DATA_MAX_LIFE);
    }

    public float getScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public float getGroundY() {
        return this.entityData.get(DATA_GROUND_Y);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int life = this.entityData.get(DATA_LIFE);
        int maxLife = this.entityData.get(DATA_MAX_LIFE);
        this.entityData.set(DATA_LIFE, life + 1);
        if (life >= maxLife) {
            this.discard();
            return;
        }
        if (life % 2 == 0) {
            Vec3Point center = new Vec3Point(this.getX(), this.getY(), this.getZ());
            NpcFx.moonPhantomAura(level, center.x, center.y, center.z, this.getScale());
        }
        // 地面月华池随月盘一起呼吸，让领域的边界在夜里清晰可见。
        if (life % 8 == 0) {
            NpcFx.moonDomainGround(level, this.getX(), this.getGroundY(), this.getZ(),
                    this.getScale() * 0.55);
        }
        // 每一次月轮落下只唤来一只月兔：它会在领域里协助作战。
        if (life == 20) {
            this.spawnRabbit(level);
        }
        // 落下的第一波月光有短暂的“凝结”时间，随后每 1.5 秒一道脉冲。
        if (life >= 10 && (life - 10) % 30 == 0) {
            this.pulse(level);
        }
    }

    /** 月兔的主人：脱战计时靠她判断，所以召唤时把哥伦比娅记下来。 */
    private UUID ownerUuid;

    public void setOwner(UUID owner) {
        this.ownerUuid = owner;
    }

    private void spawnRabbit(ServerLevel level) {
        if (this.ownerUuid == null) {
            return;
        }
        double radius = this.getScale() * 0.55 * 0.6;
        MoonRabbitEntity rabbit = new MoonRabbitEntity(ModEntities.MOON_RABBIT, level);
        rabbit.setOwner(this.ownerUuid);
        rabbit.setCustomName(Component.translatable("entity.xinkun_genshin.moon_rabbit"));
        rabbit.setCustomNameVisible(true);
        double angle = this.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = radius * (0.35 + this.getRandom().nextDouble() * 0.6);
        rabbit.snapTo(this.getX() + Math.cos(angle) * dist, this.getGroundY() + 1.0,
                this.getZ() + Math.sin(angle) * dist, this.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(rabbit);
        NpcFx.spiritSummon(level, rabbit.position());
    }

    private void pulse(ServerLevel level) {
        float scale = this.getScale();
        // 月光是一整片落在地面的光池，水平半径与月盘相当。
        double radius = scale * 0.55;
        double groundY = this.getGroundY();
        AABB area = new AABB(
                this.getX() - radius, groundY - 4.0, this.getZ() - radius,
                this.getX() + radius, groundY + 8.0, this.getZ() + radius);
        double damage = 2.5;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area);
        level.playSound(null, this.getX(), groundY, this.getZ(),
                ModSounds.MOON_PULSE, SoundSource.AMBIENT, 1.6F, 1.0F);
        NpcFx.moonPulse(level, this.getX(), groundY, this.getZ(), radius);
        for (LivingEntity victim : victims) {
            if (!(victim instanceof Enemy) || !victim.isAlive()) {
                continue;
            }
            double dx = victim.getX() - this.getX();
            double dz = victim.getZ() - this.getZ();
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            victim.hurtServer(level, level.damageSources().magic(), (float) damage);
            victim.addEffect(new MobEffectInstance(ModEffects.MOON_PALE, MoonPaleEffect.DURATION,
                    0, false, true, true));
            // 满级虚弱：十二秒内敌人的攻击力被压到最低，每次月轮命中都会刷新时长。
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_DURATION,
                    WEAKNESS_MAX_AMPLIFIER, false, true, true));
            NpcFx.paleStrike(level, victim);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    /**
     * 月盘本身没有碰撞体积，但视觉半径有十几格。原版按 0.2 格碰撞箱算出的
     * 可见距离只有十几格，会让远处的月亮整轮被剔除，所以这里按视觉尺寸放宽。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double range = Math.max(64.0, this.getScale() * 16.0);
        return distanceSqr < range * range;
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
    protected void readAdditionalSaveData(ValueInput input) {
        this.configure(input.getIntOr("MoonPhase", 0),
                input.getFloatOr("MoonScale", DEFAULT_SCALE),
                input.getIntOr("MoonLife", DEFAULT_LIFETIME),
                input.getFloatOr("MoonGroundY", (float) this.getY()));
        this.entityData.set(DATA_LIFE, input.getIntOr("Age", 0));
        String raw = input.getStringOr("MoonOwner", "");
        try {
            this.ownerUuid = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            this.ownerUuid = null;
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("MoonPhase", this.getPhase());
        output.putFloat("MoonScale", this.getScale());
        output.putInt("MoonLife", this.getMaxLife());
        output.putFloat("MoonGroundY", this.getGroundY());
        output.putInt("Age", this.getLife());
        output.putString("MoonOwner", this.ownerUuid == null ? "" : this.ownerUuid.toString());
    }

    /** 避免为了计算粒子中心而反复创建 Vec3。 */
    private record Vec3Point(double x, double y, double z) {
    }
}
