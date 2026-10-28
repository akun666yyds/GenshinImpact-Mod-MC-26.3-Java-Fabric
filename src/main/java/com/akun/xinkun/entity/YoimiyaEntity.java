package com.akun.xinkun.entity;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.entity.ai.YoimiyaBowGoal;
import com.akun.xinkun.entity.ai.YoimiyaFireworksShowGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 宵宫。基础的采集、拾取、死亡掉落与语音都来自共享角色类；
 * 她自己的部分只有长弓烟花战斗，以及夜里请玩家看烟花的小习惯。
 */
public class YoimiyaEntity extends GenshinNpcEntity {
    /** 0 无动作，1 仰望天空双手举高。 */
    public static final int POSE_NONE = 0;
    public static final int POSE_SHOW = 1;

    /** 表演姿态的保持时长。26.3 的目标每两 tick 才 tick 一次，这里留足余量。 */
    private static final int SHOW_POSE_HOLD = 20;

    private static final EntityDataAccessor<Integer> DATA_FIREWORK_POSE =
            SynchedEntityData.defineId(YoimiyaEntity.class, EntityDataSerializers.INT);
    /**
     * 拉弓状态必须单独同步。原版的“使用物品”标记只对玩家可靠：生物在客户端
     * 会因为手持物与使用物不一致而立刻自我取消，渲染器读不到瞄准状态。
     */
    private static final EntityDataAccessor<Boolean> DATA_AIMING =
            SynchedEntityData.defineId(YoimiyaEntity.class, EntityDataSerializers.BOOLEAN);

    private int showPoseTicks;

    public YoimiyaEntity(EntityType<? extends YoimiyaEntity> type, Level level) {
        super(type, level);
        // 弓箭需要比近战更早发现敌人，也更愿意在远处解决战斗。
        var followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(48.0);
        }
    }

    /** 宵宫自身攻击按共享基础值的 1.5 倍计算。 */
    public static AttributeSupplier.Builder createAttributes() {
        return GenshinNpcEntity.createAttributes().add(Attributes.ATTACK_DAMAGE, 7.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FIREWORK_POSE, POSE_NONE);
        builder.define(DATA_AIMING, false);
    }

    /** 拉弓瞄准：客户端据此摆出与玩家一致的双手持弓姿势。 */
    public void setAiming(boolean aiming) {
        if (this.level().isClientSide()) {
            return;
        }
        this.entityData.set(DATA_AIMING, aiming);
    }

    public boolean isAiming() {
        return this.entityData.get(DATA_AIMING);
    }

    /**
     * 动作状态完全由服务端实体自己按 tick 维护：客户端只读同步值摆姿势，
     * 不受目标调度频率影响，也就不会出现动作中途掉回站姿的空档。
     */
    public void beginFireworkShow() {
        if (this.level().isClientSide()) {
            return;
        }
        this.showPoseTicks = SHOW_POSE_HOLD;
        this.entityData.set(DATA_FIREWORK_POSE, POSE_SHOW);
    }

    /** 表演继续进行：续上双手举高的表演姿态。 */
    public void keepFireworkShowPose() {
        if (this.level().isClientSide()) {
            return;
        }
        this.showPoseTicks = SHOW_POSE_HOLD;
        this.entityData.set(DATA_FIREWORK_POSE, POSE_SHOW);
    }

    public void endFireworkShow() {
        if (this.level().isClientSide()) {
            return;
        }
        this.showPoseTicks = 0;
        this.entityData.set(DATA_FIREWORK_POSE, POSE_NONE);
    }

    public int getFireworkPose() {
        return this.entityData.get(DATA_FIREWORK_POSE);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.showPoseTicks > 0 && --this.showPoseTicks == 0) {
            this.entityData.set(DATA_FIREWORK_POSE, POSE_NONE);
        }
    }

    @Override
    public GenshinCharacter getCharacter() {
        return GenshinCharacter.YOIMIYA;
    }

    @Override
    protected void registerCharacterGoals() {
        // 战斗先于采集：只要有敌人，长弓就接管移动与视线。
        this.goalSelector.addGoal(2, new YoimiyaBowGoal(this));
        // 夜间的烟花表演是休闲行为，必须让位给问候、工作与拾取。
        this.goalSelector.addGoal(4, new YoimiyaFireworksShowGoal(this));
    }

    @Override
    protected void addCharacterDefaults(java.util.List<ItemStack> kit) {
        // 原版长弓；箭与烟花无限、不毁、死亡不掉落。
        addDefault(kit, Items.BOW);
        addDefault(kit, Items.ARROW);
        addDefault(kit, Items.FIREWORK_ROCKET);
    }
}
