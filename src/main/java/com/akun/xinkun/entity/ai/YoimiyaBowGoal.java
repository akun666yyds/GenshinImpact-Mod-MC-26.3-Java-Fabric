package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.YoimiyaEntity;
import com.akun.xinkun.entity.projectile.FireworkRocketProjectile;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModEntities;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 宵宫的远程战斗：先用长弓点射，间隔中穿插她最拿手的烟花箭。
 *
 * <p>箭矢是真实的箭实体，飞行时带火焰拖尾并点燃命中的敌人。烟花攻击
 * 不是普通的爆炸物：伤害与引燃只作用于敌对生物和当前目标，绝不会误伤
 * 玩家；视觉上则铺满拖尾与彩色爆裂粒子。目标越强，烟花的使用率越高。</p>
 */
public class YoimiyaBowGoal extends Goal {
    /** Tag used by the server tick hook to keep the arrow's flame trail alive. */
    public static final String FLAME_ARROW_TAG = "xinkun_flame_arrow";

    private static final double ENGAGE_RANGE_SQR = 40.0 * 40.0;
    private static final double MAX_RANGE_SQR = 32.0 * 32.0;
    /** 只有水平贴脸且高度相近才把输出交给近战；头顶/脚下的敌人继续射箭。 */
    private static final double MELEE_HANDOFF_RADIUS = 3.0;
    private static final double MELEE_HANDOFF_HEIGHT = 2.0;
    private static final double CLOSE_RANGE_SQR = 8.0 * 8.0;
    /** Long enough for the player-style bow aim pose to read clearly. */
    private static final int DRAW_TICKS = 12;
    private static final int ARROW_COOLDOWN_MIN = 18;
    private static final int ARROW_COOLDOWN_MAX = 28;
    private static final int FIREWORK_COOLDOWN_MIN = 34;
    private static final int FIREWORK_COOLDOWN_MAX = 46;
    private static final float FIREWORK_CHANCE = 0.35F;
    private static final float FIREWORK_CHANCE_BOSS = 0.75F;
    /**
     * Fire arrow: an ordinary single-target shot. Vanilla multiplies an arrow's
     * base damage by its impact speed, so this value is the multiplier that
     * yields roughly 13.5 damage at the arrow's 3.0 blocks/tick flight speed.
     */
    private static final float ARROW_DAMAGE = 4.8F;
    private static final float ARROW_SPEED = 3.0F;
    /** Rocket: slow enough to watch it fly, then a larger and harder-hitting blast. */
    private static final float ROCKET_SPEED = 0.9F;

    private final YoimiyaEntity npc;
    private int cooldown = 20;
    private int backstepCooldown;
    private int drawTicks;
    private boolean drawIsFirework;

    public YoimiyaBowGoal(YoimiyaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.hasCastableTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return this.hasCastableTarget();
    }

    private boolean hasCastableTarget() {
        if (!(this.npc.getTarget() instanceof LivingEntity target)) {
            return false;
        }
        if (!(target instanceof Enemy) || target instanceof Creeper
                || !target.isAlive()) {
            return false;
        }
        double distanceSqr = this.npc.distanceToSqr(target);
        return distanceSqr <= ENGAGE_RANGE_SQR
                && !NpcAim.withinMeleeReach(this.npc, target, MELEE_HANDOFF_RADIUS, MELEE_HANDOFF_HEIGHT);
    }

    @Override
    public void start() {
        this.cooldown = Math.min(this.cooldown, 12);
        this.backstepCooldown = 0;
        this.drawTicks = 0;
    }

    @Override
    public void stop() {
        this.npc.stopUsingItem();
        this.npc.setAiming(false);
        this.npc.getNavigation().stop();
        this.npc.stowTool();
        this.drawTicks = 0;
    }

    @Override
    public void tick() {
        if (!(this.npc.getTarget() instanceof LivingEntity target)) {
            return;
        }
        this.npc.getLookControl().setLookAt(target, 40.0F, 40.0F);
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.backstepCooldown > 0) {
            this.backstepCooldown--;
        }

        if (this.drawTicks > 0) {
            this.tickDraw(target);
            return;
        }

        double distanceSqr = this.npc.distanceToSqr(target);
        if (distanceSqr > MAX_RANGE_SQR) {
            this.npc.getNavigation().moveTo(target, 1.18);
            return;
        }
        if (distanceSqr < CLOSE_RANGE_SQR && this.backstepCooldown <= 0) {
            this.stepBack(target);
            this.backstepCooldown = 26;
        } else if (this.npc.getNavigation().isDone()) {
            this.npc.getNavigation().stop();
        }
        if (this.cooldown > 0 || NpcAim.clearAim(this.npc, target) == null) {
            return;
        }
        if (!this.npc.ensureWeapon()) {
            return;
        }
        this.startDraw(target);
    }

    private void startDraw(LivingEntity target) {
        float roll = this.npc.isBoss(target) ? FIREWORK_CHANCE_BOSS : FIREWORK_CHANCE;
        boolean firework = this.npc.getRandom().nextFloat() < roll;
        if (!firework && this.npc.countItem(Items.ARROW) <= 0) {
            // 没有箭了也不至于哑火：她改用烟花把敌人逼退。
            firework = true;
        }
        this.drawIsFirework = firework;
        this.drawTicks = DRAW_TICKS;
        this.npc.startUsingItem(InteractionHand.MAIN_HAND);
        // 同步的瞄准标记驱动客户端的双手拉弓姿势。
        this.npc.setAiming(true);
    }

    private void tickDraw(LivingEntity target) {
        if (NpcAim.clearAim(this.npc, target) == null) {
            this.cancelDraw();
            return;
        }
        if (--this.drawTicks > 0) {
            return;
        }
        this.npc.stopUsingItem();
        this.npc.setAiming(false);
        if (this.npc.distanceToSqr(target) > ENGAGE_RANGE_SQR) {
            this.cancelDraw();
            return;
        }
        if (this.drawIsFirework) {
            this.castFirework(target);
        } else {
            this.castArrow(target);
        }
    }

    private void cancelDraw() {
        this.npc.stopUsingItem();
        this.npc.setAiming(false);
        this.drawTicks = 0;
        this.cooldown = Math.max(this.cooldown, 10);
    }

    private void stepBack(LivingEntity target) {
        Vec3 away = this.npc.position().subtract(target.position());
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 destination = this.npc.position().add(away.normalize().scale(5.0));
        this.npc.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.25);
    }

    private void castArrow(LivingEntity target) {
        if (!(this.npc.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!this.npc.consumeItem(Items.ARROW, 1)) {
            this.castFirework(target);
            return;
        }
        Vec3 start = this.npc.getEyePosition();
        Vec3 base = NpcAim.clearAim(this.npc, target);
        if (base == null) {
            return;
        }
        Vec3 aim = this.leadAim(start, base, target);
        Vec3 direction = aim.subtract(start);
        if (direction.lengthSqr() < 0.0001) {
            return;
        }
        Arrow arrow = EntityTypes.ARROW.create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (arrow == null) {
            return;
        }
        arrow.setPos(start.x, start.y - 0.12, start.z);
        arrow.setOwner(this.npc);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(ARROW_DAMAGE);
        arrow.setCritArrow(false);
        // 无重力直射：瞄准哪里就飞到哪里，高出或低于她的目标同样能命中。
        arrow.setNoGravity(true);
        // 火焰附魔箭的火焰贴图与点燃效果：飞行途中自带火星拖尾。
        arrow.setRemainingFireTicks(400);
        arrow.addTag(FLAME_ARROW_TAG);
        Vec3 unit = direction.normalize();
        arrow.shoot(unit.x, unit.y, unit.z, ARROW_SPEED, 0.02F);
        serverLevel.addFreshEntity(arrow);
        NpcFx.arrowLaunch(serverLevel, start, unit);
        serverLevel.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 0.9F, 0.95F + this.npc.getRandom().nextFloat() * 0.1F);
        this.cooldown = ARROW_COOLDOWN_MIN
                + this.npc.getRandom().nextInt(ARROW_COOLDOWN_MAX - ARROW_COOLDOWN_MIN + 1);
    }

    /**
     * 箭矢与烟花都不再受重力影响，因此只需按飞行时间补偿目标自身的移动；
     * 不再向下抬高落点，射击高处目标时不会因补偿量变成"越过头顶"。
     */
    private Vec3 leadAim(Vec3 start, Vec3 point, LivingEntity target) {
        double travel = start.distanceTo(point) / ARROW_SPEED;
        return point.add(target.getDeltaMovement().scale(travel * 0.35));
    }

    private Vec3 rocketAim(Vec3 start, Vec3 point, LivingEntity target) {
        double travel = start.distanceTo(point) / ROCKET_SPEED;
        return point.add(target.getDeltaMovement().scale(travel * 0.5));
    }

    private void castFirework(LivingEntity target) {
        if (!(this.npc.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 start = this.npc.getEyePosition();
        Vec3 base = NpcAim.clearAim(this.npc, target);
        if (base == null) {
            return;
        }
        Vec3 aim = this.rocketAim(start, base, target);
        Vec3 direction = aim.subtract(start);
        if (direction.lengthSqr() < 0.0001) {
            return;
        }
        // 真正的烟花火箭：有初速、会飞行、命中的是实体而不是瞬间结算的光束。
        FireworkRocketProjectile rocket = new FireworkRocketProjectile(
                ModEntities.FIREWORK_ROCKET, this.npc, serverLevel);
        rocket.setPos(start.x, start.y - 0.12, start.z);
        Vec3 unit = direction.normalize();
        rocket.shoot(unit.x, unit.y, unit.z, ROCKET_SPEED, 0.02F);
        serverLevel.addFreshEntity(rocket);
        NpcFx.rocketTrail(serverLevel, start);
        serverLevel.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1.0F, 1.0F);

        if (this.npc.isSpecialVoiceReady()) {
            this.npc.speak(VoiceContext.FIREWORK);
            this.npc.markSpecialVoice(900);
        }
        this.cooldown = FIREWORK_COOLDOWN_MIN
                + this.npc.getRandom().nextInt(FIREWORK_COOLDOWN_MAX - FIREWORK_COOLDOWN_MIN + 1);
    }
}
