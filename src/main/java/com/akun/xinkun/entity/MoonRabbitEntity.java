package com.akun.xinkun.entity;

import com.akun.xinkun.entity.ai.MoonRabbitFollowGoal;
import com.akun.xinkun.fx.NpcFx;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 月之领域里被月光唤来的白兔。外形与原版白兔完全一致，但它们是
 * 哥伦比娅的宠物：会主动扑咬领域里的敌人，并在她脱战十秒后化作
 * 光尘散去，不会留在世界里变成普通兔子。
 */
public class MoonRabbitEntity extends Rabbit {
    /** 脱战后的存续时间：十秒。 */
    private static final int VANISH_DELAY = 200;

    private static final int COMBAT_STUCK_LIMIT = 60;

    private UUID ownerUuid;
    private int vanishCountdown = VANISH_DELAY;
    private int combatStuckTicks;
    private double lastX;
    private double lastZ;

    public MoonRabbitEntity(EntityType<? extends MoonRabbitEntity> type, Level level) {
        super(type, level);
        // 月兔不该被自然刷新规则清掉：它们的去留只由主人的战斗状态决定。
        this.setPersistenceRequired();
        // 月之加护：比普通兔子更快、更耐打，也更难被击退。
        this.setBase(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 20.0);
        this.setBase(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.45);
        this.setBase(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, 10.0);
        this.setBase(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.6);
        this.setHealth(this.getMaxHealth());
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public void setOwner(UUID owner) {
        this.ownerUuid = owner;
        this.vanishCountdown = VANISH_DELAY;
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        // 原版兔子的属性基础上补攻击伤害：没有它 MeleeAttackGoal 打不出伤害。
        return Rabbit.createAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 6.0);
    }

    public UUID getOwner() {
        return this.ownerUuid;
    }

    /** 月兔永远是雪白色，不受群系随机毛色影响。 */
    @Override
    public Variant getVariant() {
        return Variant.WHITE;
    }

    @Override
    protected void registerGoals() {
        // 不沿用原版兔子的惊慌、躲避与啃菜园目标：月兔是战斗宠物，
        // 那些目标会在追击途中把兔子拽走向反方向，看起来就像卡住了。
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.6, true) {
            @Override
            protected int getAttackInterval() {
                // 原版是 20 tick 一击；月兔 0.4 秒一击。
                return 8;
            }
        });
        this.goalSelector.addGoal(5, new MoonRabbitFollowGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<LivingEntity>(
                this, LivingEntity.class, 10, true, false,
                (target, level) -> target instanceof Enemy && target.isAlive()));
    }

    @Override
    public void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.watchCombatStuck(level);
        net.minecraft.world.entity.Entity found = this.ownerUuid == null
                ? null : level.getEntityInAnyDimension(this.ownerUuid);
        LivingEntity owner = found instanceof LivingEntity living ? living : null;
        boolean inCombat = owner instanceof ColumbinaEntity columbina
                && columbina.isAlive() && columbina.hasHostileTarget();
        if (inCombat) {
            this.vanishCountdown = VANISH_DELAY;
            return;
        }
        if (this.vanishCountdown > 0) {
            this.vanishCountdown--;
        }
        if (this.vanishCountdown == 0) {
            NpcFx.spiritDissolve(level, this);
            this.discard();
        }
    }

    /**
     * 追敌人时如果连续三秒几乎没挪动，说明寻路被栅栏、墙根或水面卡住，
     * 直接闪到目标身边继续打，避免月兔顶在障碍物前原地蹦到消失。
     */
    private void watchCombatStuck(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || target.level() != level) {
            this.combatStuckTicks = 0;
            return;
        }
        if (this.tickCount % 20 != 0) {
            return;
        }
        double moved = Math.abs(this.getX() - this.lastX) + Math.abs(this.getZ() - this.lastZ);
        this.lastX = this.getX();
        this.lastZ = this.getZ();
        if (this.distanceToSqr(target) > 9.0 && moved < 0.35) {
            this.combatStuckTicks += 20;
        } else {
            this.combatStuckTicks = 0;
        }
        if (this.combatStuckTicks >= COMBAT_STUCK_LIMIT) {
            this.teleportNear(target, this.getRandom().nextInt(3) - 1);
            this.combatStuckTicks = 0;
        }
    }

    /** 闪到目标旁边的安全落脚点，用于跟丢或追击被卡住时脱困。 */
    public void teleportNear(LivingEntity target, int side) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos spot = MoonRabbitFollowGoal.safeSpotNear(serverLevel, target, side);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
    }

    /** 宠物不散落兔肉兔皮：散去时只留下光尘。 */
    @Override
    public void dropFromLootTable(ServerLevel level, DamageSource damageSource, boolean causedByPlayer) {
        // 故意留空。
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean causedByPlayer) {
        // 故意留空。
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("MoonOwner", this.ownerUuid == null ? "" : this.ownerUuid.toString());
        output.putInt("VanishCountdown", this.vanishCountdown);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String raw = input.getStringOr("MoonOwner", "");
        try {
            this.ownerUuid = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            this.ownerUuid = null;
        }
        this.vanishCountdown = input.getIntOr("VanishCountdown", VANISH_DELAY);
    }
}
