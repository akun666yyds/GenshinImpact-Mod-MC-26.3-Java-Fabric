package com.akun.xinkun.entity;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.entity.ai.SandroneCompanionGoal;
import com.akun.xinkun.entity.ai.SandroneFrostRayGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 「木偶」桑多涅：愚人众第七席执行官。
 *
 * <p>战斗时她悬停在半空，双腿微曲分开，双手向前推出，从掌心连射淡蓝与雪白的
 * 冰霜射线。射线没有重力、射速极高、会穿透沿途所有敌人，命中水面会结冰，
 * 熄灭火焰，把岩浆凝成黑曜石，并让被击中的方块表面慢慢积雪。</p>
 *
 * <p>白天没有敌人时，她会去找附近的哥伦比娅并肩散步，两人轮流说上一两句
 * 官方语音；入夜后这段同行机制自动暂停，各自回到原本的作息。</p>
 */
public class SandroneEntity extends GenshinNpcEntity {
    /** 客户端骨架模型据此切换浮空冰雪射线的施法姿态。 */
    private static final EntityDataAccessor<Boolean> DATA_CASTING =
            SynchedEntityData.defineId(SandroneEntity.class, EntityDataSerializers.BOOLEAN);
    /** 射线真实落点（世界坐标）。客户端据此把连续光束画到命中处，而不是自己猜。 */
    private static final EntityDataAccessor<Float> DATA_BEAM_X =
            SynchedEntityData.defineId(SandroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_Y =
            SynchedEntityData.defineId(SandroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_Z =
            SynchedEntityData.defineId(SandroneEntity.class, EntityDataSerializers.FLOAT);

    public SandroneEntity(EntityType<? extends SandroneEntity> type, Level level) {
        super(type, level);
        var followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            // 冰霜射线射程很远，她需要先锁定更远处的敌人。
            followRange.setBaseValue(52.0);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GenshinNpcEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32);
    }

    @Override
    public GenshinCharacter getCharacter() {
        return GenshinCharacter.SANDRONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CASTING, false);
        builder.define(DATA_BEAM_X, 0.0F);
        builder.define(DATA_BEAM_Y, 0.0F);
        builder.define(DATA_BEAM_Z, 0.0F);
    }

    /** 是否正在浮空施展冰霜射线。 */
    public boolean isCastingFrost() {
        return this.entityData.get(DATA_CASTING);
    }

    public void setCastingFrost(boolean casting) {
        if (!this.level().isClientSide() && this.entityData.get(DATA_CASTING) != casting) {
            this.entityData.set(DATA_CASTING, casting);
        }
    }

    /** 最近一发射线真实打到的位置，供客户端绘制连续光束。 */
    public net.minecraft.world.phys.Vec3 getBeamTarget() {
        return new net.minecraft.world.phys.Vec3(
                this.entityData.get(DATA_BEAM_X),
                this.entityData.get(DATA_BEAM_Y),
                this.entityData.get(DATA_BEAM_Z));
    }

    public void setBeamTarget(net.minecraft.world.phys.Vec3 target) {
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_BEAM_X, (float) target.x);
            this.entityData.set(DATA_BEAM_Y, (float) target.y);
            this.entityData.set(DATA_BEAM_Z, (float) target.z);
        }
    }

    @Override
    protected void registerCharacterGoals() {
        // 冰雪射线优先于共享的近战目标：她站定浮空、连续射击，不追着敌人挥剑。
        this.goalSelector.addGoal(2, new SandroneFrostRayGoal(this));
        // 白天与哥伦比娅同行是休闲行为：低于战斗与工作，但高于闲逛，
        // 否则负责散步的低优先级目标会一直占着移动旗标，让她永远走不到同伴身边。
        this.goalSelector.addGoal(5, new SandroneCompanionGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        // 目标消失、天亮入夜或死亡时都要立刻落回地面，免得一直保持施法姿态。
        if (this.isCastingFrost()) {
            LivingEntity target = this.getTarget();
            boolean valid = this.isAlive() && target != null && target.isAlive();
            if (!valid) {
                this.setCastingFrost(false);
            }
        }
    }
}
