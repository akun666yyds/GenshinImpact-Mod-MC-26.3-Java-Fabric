package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.GenshinNpcEntity;
import com.akun.xinkun.entity.ZhongliEntity;
import com.akun.xinkun.entity.ZhongliMeteorEntity;
import com.akun.xinkun.entity.ZhongliStoneSpearEntity;
import com.akun.xinkun.registry.ModEntities;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.akun.xinkun.particle.GlowDustOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 钟离的战斗节奏。
 *
 * <p>贴身两格半以内是标准的持矛突刺：挺矛、向前半步、收势。距离拉开之后
 * 他会把长矛收回行囊，双臂高举，在敌人前方唤下一排自天而降、贯穿沿途
 * 所有敌人的岩枪。蓄势到一定程度时还有概率改放天星——巨大的基岩陨石
 * 斜着砸下来，中心一百二十点伤害向四周递减，命中时整个爆炸范围内迸起
 * 向外飞散的岩金尘埃。面对普通怪物他每次都会以六成半的概率请出天星；
 * 只要附近出现五只以上的怪物，或者任意一只凋零、坚守者、末影龙、劫掠兽、
 * 疣猪兽，天星就会无条件排在第一位，哪怕敌人已经贴到脸上。</p>
 *
 * <p>近战只留给普通弱敌，并且依旧只是概率兜底；强敌和群怪面前他绝不拼拳。</p>
 *
 * <p>所有施法都会先收起长矛再恢复，保证动作和手里的兵器一致。</p>
 */
public class ZhongliCombatGoal extends Goal {
    /** 近战判定：贴脸两格，且只是概率兜底。 */
    private static final double MELEE_REACH = 2.0;
    /** 贴脸时每次出手判定的概率。 */
    private static final double MELEE_CHANCE = 0.35;
    /** 岩枪冷却中又被贴脸时，先撤到这条线外重新拉开距离。 */
    private static final double KITE_RANGE = 5.0;
    /** 岩枪最远覆盖距离：与索敌范围一致，保证最远的目标也躲不开枪雨。 */
    private static final double SPEAR_MAX_RANGE = 48.0;
    private static final double MAX_CHASE_SQR = 128.0 * 128.0;
    /** 对空射程：只看水平距离，垂直方向额外允许 48 格。 */
    private static final double AIR_VERTICAL_REACH = 48.0;

    private static final int MELEE_COOLDOWN = 22;
    private static final int THRUST_TICKS = 10;
    private static final int CREEPER_RETREAT = 26;

    private static final int SPEAR_COOLDOWN = 80;
    /** 岩枪前摇：抬手到落枪只需 8 tick，整个施法也压缩到 1.5 秒。 */
    private static final int SPEAR_CAST_TICKS = 30;
    private static final int SPEAR_RELEASE_TICK = 8;
    private static final int SPEAR_MIN = 6;
    private static final int SPEAR_MAX = 28;

    private static final int METEOR_COOLDOWN = 600;
    /** 打飞行目标时天星更快转好，避免岩枪无用的情况下只能干等。 */
    private static final int METEOR_COOLDOWN_AIR = 220;
    private static final int METEOR_RETRY = 30;
    /** 首领级目标的天星重试间隔：几乎一冷却完就再赌一次。 */
    private static final int METEOR_RETRY_BOSS = 20;
    private static final int METEOR_CAST_TICKS = 52;
    private static final int METEOR_RELEASE_TICK = 32;
    /** 天星发动概率：普通怪六成半，首领级目标必定请出。 */
    private static final double METEOR_CHANCE = 0.65;
    private static final double METEOR_CHANCE_BOSS = 1.0;
    /** 身边怪物多于四只就算被围住，天星直接排到最前面。 */
    private static final double CROWD_RADIUS = 16.0;
    private static final int CROWD_THRESHOLD = 5;

    private enum Cast {
        NONE,
        SPEAR_RAIN,
        METEOR
    }

    private final ZhongliEntity npc;
    private Cast cast = Cast.NONE;
    private int castTicks;
    private boolean released;
    private Vec3 castAim = Vec3.ZERO;
    private Vec3 castTargetPos = Vec3.ZERO;
    private boolean castAirborne;

    private int attackCooldown;
    private int thrustTicks;
    private int retreatTicks;
    private int spearCooldown;
    private int meteorCooldown;
    private int repathCooldown;

    public ZhongliCombatGoal(ZhongliEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.npc.canFightNow()) {
            return false;
        }
        LivingEntity target = this.npc.getTarget();
        if (!this.isValidTarget(target)) {
            return false;
        }
        return NpcAim.horizontalDistanceSqr(this.npc, target) <= MAX_CHASE_SQR
                && Math.abs(target.getY() - this.npc.getY()) <= AIR_VERTICAL_REACH;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.npc.isAlive()) {
            return false;
        }
        // 已经起手的招式一定要打完：岩枪和天星落下去之后才算数。
        if (this.cast != Cast.NONE) {
            return true;
        }
        return this.canUse();
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive()
                && !(target instanceof net.minecraft.world.entity.player.Player)
                && !(target instanceof GenshinNpcEntity);
    }

    /** 附近敌意的数量；达到阈值就提前返回，避免每 tick 数完整片怪群。 */
    private int countNearbyEnemies() {
        int count = 0;
        for (LivingEntity other : this.npc.level().getEntitiesOfClass(LivingEntity.class,
                this.npc.getBoundingBox().inflate(CROWD_RADIUS),
                mob -> mob instanceof Enemy && mob.isAlive())) {
            if (++count >= CROWD_THRESHOLD) {
                return count;
            }
        }
        return count;
    }

    /** 强敌名单：凋零、坚守者、末影龙、劫掠兽、疣猪兽。 */
    private static boolean isStrongEnemy(LivingEntity entity) {
        return entity instanceof WitherBoss
                || entity instanceof Warden
                || entity instanceof EnderDragon
                || entity instanceof Ravager
                || entity instanceof Hoglin;
    }

    /** 附近是否至少有一只强敌。当前目标本身也算，哪怕它站在十六格外。 */
    private boolean hasStrongThreat() {
        LivingEntity target = this.npc.getTarget();
        if (isStrongEnemy(target)) {
            return true;
        }
        for (LivingEntity other : this.npc.level().getEntitiesOfClass(LivingEntity.class,
                this.npc.getBoundingBox().inflate(CROWD_RADIUS),
                mob -> mob instanceof Enemy && mob.isAlive() && isStrongEnemy(mob))) {
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
        this.thrustTicks = 0;
        this.retreatTicks = 0;
        this.repathCooldown = 0;
        this.cast = Cast.NONE;
        this.castTicks = 0;
        this.npc.ensureWeapon();
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
        if (this.cast != Cast.NONE) {
            this.npc.clearActionPose();
            this.npc.ensureWeapon();
        }
        this.cast = Cast.NONE;
        this.castTicks = 0;
        this.released = false;
        this.attackCooldown = 0;
        this.thrustTicks = 0;
        this.retreatTicks = 0;
    }

    @Override
    public void tick() {
        if (this.cast != Cast.NONE) {
            this.tickCast();
            return;
        }
        LivingEntity target = this.npc.getTarget();
        if (!this.isValidTarget(target)) {
            return;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.thrustTicks > 0) {
            this.thrustTicks--;
        }
        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }
        if (this.spearCooldown > 0) {
            this.spearCooldown--;
        }
        this.npc.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.retreatTicks > 0) {
            this.retreatTicks--;
            this.retreatFrom(target);
            return;
        }

        double distance = Math.sqrt(this.npc.distanceToSqr(target));
        double horizontalDistance = Math.sqrt(NpcAim.horizontalDistanceSqr(this.npc, target));
        // 高悬头顶的目标只能靠天星：岩枪是插在地上的，垂直差距大时永远打不中。
        boolean airborne = NpcAim.isHighAbove(this.npc, target);

        // 群怪与强敌：天星无条件优先，贴到脸上也先落天星。
        boolean urgent = this.countNearbyEnemies() >= CROWD_THRESHOLD || this.hasStrongThreat();
        if (this.meteorCooldown > 0) {
            this.meteorCooldown--;
        }
        if (urgent) {
            if (this.meteorCooldown <= 0 && NpcAim.clearAim(this.npc, target) != null) {
                this.beginMeteor(target);
                return;
            }
            // 天星冷却时用岩枪雨撑住；但对空目标不浪费枪雨，继续等天星。
            if (!airborne && this.spearCooldown <= 0 && distance > 1.0
                    && NpcAim.clearAim(this.npc, target) != null) {
                this.beginSpearRain(target, distance);
                return;
            }
            // 两个技能都在冷却：拉开距离等待下一发天星，而不是贴身互殴。
            if (distance < KITE_RANGE && this.repathCooldown <= 0
                    && this.npc.getNavigation().isDone()) {
                this.repathCooldown = 10;
                this.retreatFrom(target);
            }
            return;
        }

        boolean boss = GenshinNpcEntity.isBoss(target);
        // 目标明显高过或低于自己时，岩枪的落地判定不可靠，直接改由天星处理。
        boolean vertical = Math.abs(target.getY() - this.npc.getY()) > 3.0;
        // 天星是赌出来的大招：先冷却，再按概率起手。首领与高低差目标必定。
        if (this.meteorCooldown <= 0 && horizontalDistance <= SPEAR_MAX_RANGE
                && NpcAim.clearAim(this.npc, target) != null) {
            double chance = (boss || vertical) ? METEOR_CHANCE_BOSS : METEOR_CHANCE;
            if (this.npc.getRandom().nextDouble() < chance) {
                this.beginMeteor(target);
                return;
            }
            this.meteorCooldown = boss ? METEOR_RETRY_BOSS : METEOR_RETRY;
        }

        // 飞行目标等天星转好即可；枪雨和突刺都够不到空中。
        if (airborne) {
            if (horizontalDistance < KITE_RANGE && this.repathCooldown <= 0
                    && this.npc.getNavigation().isDone()) {
                this.repathCooldown = 10;
                this.retreatFrom(target);
            }
            return;
        }

        // 中近距离的首选：岩枪雨。枪排一路铺到目标脚下，最远的目标也躲不开。
        // 贴身时也允许起手，所以整场战斗里岩枪的出场率明显更高。
        if (this.spearCooldown <= 0
                && distance > 1.0
                && distance <= SPEAR_MAX_RANGE
                && NpcAim.clearAim(this.npc, target) != null) {
            this.beginSpearRain(target, distance);
            return;
        }

        if (distance <= MELEE_REACH) {
            // 近战只对普通弱敌、且只是概率兜底；强敌由上面的天星分支接管。
            if (!boss
                    && this.attackCooldown <= 0
                    && NpcAim.clearAim(this.npc, target) != null
                    && this.npc.getRandom().nextDouble() < MELEE_CHANCE) {
                this.melee(target);
            }
            return;
        }

        // 技能都在冷却又被贴脸：后撤重新拉开施法距离，而不是站着互殴。
        if (distance < KITE_RANGE && this.spearCooldown > 0 && this.meteorCooldown > 0) {
            if (this.repathCooldown <= 0 && this.npc.getNavigation().isDone()) {
                this.repathCooldown = 10;
                this.retreatFrom(target);
            }
            return;
        }

        if (this.npc.getNavigation().isDone() || this.npc.getNavigation().isStuck()) {
            if (this.repathCooldown <= 0) {
                this.repathCooldown = 6;
                this.npc.getNavigation().moveTo(target, 1.2);
            }
        }
    }

    // ------------------------------------------------------------------ 近战

    private void melee(LivingEntity target) {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        this.npc.getNavigation().stop();
        this.npc.ensureWeapon();
        Vec3 aim = target.position().subtract(this.npc.position());
        Vec3 forward = new Vec3(aim.x, 0.0, aim.z);
        if (forward.lengthSqr() < 1.0E-4) {
            forward = new Vec3(this.npc.getLookAngle().x, 0.0, this.npc.getLookAngle().z);
        }
        if (forward.lengthSqr() > 1.0E-4) {
            forward = forward.normalize();
        }
        this.npc.setActionPose(ZhongliEntity.POSE_THRUST, THRUST_TICKS);
        // 标准突进：矛尖向前，整个人顺势压上小半步。
        this.npc.setDeltaMovement(forward.x * 0.52, this.npc.getDeltaMovement().y, forward.z * 0.52);
        this.thrustTicks = THRUST_TICKS;
        this.npc.swingForAttack(InteractionHand.MAIN_HAND);
        this.npc.doHurtTarget(level, target);
        this.attackCooldown = MELEE_COOLDOWN;
        level.sendParticles(new GlowDustOptions(0xE8C46A, 1.2F),
                this.npc.getX() + forward.x * 1.4,
                this.npc.getY() + this.npc.getBbHeight() * 0.7,
                this.npc.getZ() + forward.z * 1.4,
                6, 0.18, 0.18, 0.18, 0.02);
        level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.9F, 0.72F);

        if (target instanceof Creeper) {
            this.retreatTicks = CREEPER_RETREAT;
        }
    }

    private void retreatFrom(LivingEntity target) {
        Vec3 away = this.npc.position().subtract(target.position());
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(this.npc.getRandom().nextDouble() - 0.5, 0.0,
                    this.npc.getRandom().nextDouble() - 0.5);
        }
        Vec3 destination = this.npc.position().add(away.normalize().scale(6.0));
        this.npc.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.5);
    }

    // ------------------------------------------------------------------ 岩枪

    private void beginSpearRain(LivingEntity target, double distance) {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 aim = target.position().subtract(this.npc.position());
        Vec3 forward = new Vec3(aim.x, 0.0, aim.z);
        if (forward.lengthSqr() < 1.0E-4) {
            forward = new Vec3(this.npc.getLookAngle().x, 0.0, this.npc.getLookAngle().z);
        }
        this.castAim = forward.normalize();
        this.castTargetPos = target.position();
        this.cast = Cast.SPEAR_RAIN;
        this.castTicks = 0;
        this.released = false;
        this.npc.getNavigation().stop();
        // 主手收起长矛：抬手召唤的姿态里不该握着一杆矛。
        this.npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.npc.setActionPose(ZhongliEntity.POSE_RAISE, SPEAR_CAST_TICKS);
        this.npc.getLookControl().setLookAt(target, 40.0F, 40.0F);
        level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.2F, 0.85F);
    }

    private void launchSpearRow(ServerLevel level) {
        double distance = Math.sqrt(this.npc.distanceToSqr(this.castTargetPos));
        int count = Mth.clamp((int) Math.ceil(distance / 1.6), SPEAR_MIN, SPEAR_MAX);
        double spacing = Mth.clamp((distance - 2.4) / Math.max(1, count - 1), 1.2, 2.0);
        Vec3 origin = this.npc.position();
        for (int i = 0; i < count; i++) {
            double along = 2.4 + spacing * i;
            double x = origin.x + this.castAim.x * along;
            double z = origin.z + this.castAim.z * along;
            double groundY = findGroundY(level, x, z, origin.y);
            ZhongliStoneSpearEntity spear = new ZhongliStoneSpearEntity(
                    ModEntities.ZHONGLI_STONE_SPEAR, level);
            spear.configure(x, groundY, z, i * 2, this.npc);
            level.addFreshEntity(spear);
        }
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.5F, 0.7F);
    }

    // ------------------------------------------------------------------ 天星

    private void beginMeteor(LivingEntity target) {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        this.castTargetPos = target.position();
        this.castAirborne = NpcAim.isHighAbove(this.npc, target);
        this.cast = Cast.METEOR;
        this.castTicks = 0;
        this.released = false;
        this.npc.getNavigation().stop();
        this.npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.npc.setActionPose(ZhongliEntity.POSE_CROSS, METEOR_CAST_TICKS);
        this.npc.getLookControl().setLookAt(target, 40.0F, 40.0F);
        level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4F, 0.55F);
    }

    private void launchMeteor(ServerLevel level) {
        double groundY = findGroundY(level, this.castTargetPos.x, this.castTargetPos.z,
                this.castTargetPos.y);
        // 目标飞在半空时，天星就在它所在的高度炸开，不从它脚下穿过去。
        double impactY = this.castTargetPos.y > groundY + 2.5 ? this.castTargetPos.y : groundY;
        ZhongliMeteorEntity meteor = new ZhongliMeteorEntity(ModEntities.ZHONGLI_METEOR, level);
        meteor.configure(this.castTargetPos.x, impactY, this.castTargetPos.z, this.npc);
        level.addFreshEntity(meteor);
        level.playSound(null, this.castTargetPos.x, impactY, this.castTargetPos.z,
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.2F, 0.5F);
    }

    // ------------------------------------------------------------------ 施法

    private void tickCast() {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        this.castTicks++;
        this.npc.getNavigation().stop();
        this.npc.getLookControl().setLookAt(this.castTargetPos.x,
                this.castTargetPos.y + 1.0, this.castTargetPos.z, 30.0F, 30.0F);

        switch (this.cast) {
            case SPEAR_RAIN -> {
                if (!this.released && this.castTicks >= SPEAR_RELEASE_TICK) {
                    this.released = true;
                    this.launchSpearRow(level);
                }
                if (this.castTicks % 6 == 0) {
                    this.raiseAura(level);
                }
                if (this.castTicks >= SPEAR_CAST_TICKS) {
                    this.finishCast(SPEAR_COOLDOWN);
                }
            }
            case METEOR -> {
                // 天星锁定目标：施法过程中持续更新落点，飞行目标也躲不掉。
                LivingEntity target = this.npc.getTarget();
                if (target != null && target.isAlive()
                        && NpcAim.horizontalDistanceSqr(this.npc, target) <= SPEAR_MAX_RANGE * SPEAR_MAX_RANGE
                        && Math.abs(target.getY() - this.npc.getY()) <= AIR_VERTICAL_REACH) {
                    this.castTargetPos = target.position();
                }
                if (!this.released && this.castTicks >= METEOR_RELEASE_TICK) {
                    this.released = true;
                    this.launchMeteor(level);
                }
                if (this.castTicks % 4 == 0) {
                    this.crossAura(level);
                }
                if (this.castTicks >= METEOR_CAST_TICKS) {
                    this.finishCast(METEOR_COOLDOWN);
                }
            }
            default -> this.finishCast(0);
        }
    }

    private void raiseAura(ServerLevel level) {
        double y = this.npc.getY() + this.npc.getBbHeight() * 0.92;
        level.sendParticles(new GlowDustOptions(0xE8C46A, 1.1F),
                this.npc.getX(), y, this.npc.getZ(), 5, 0.5, 0.18, 0.5, 0.01);
        level.sendParticles(ParticleTypes.END_ROD,
                this.npc.getX(), y, this.npc.getZ(), 2, 0.35, 0.12, 0.35, 0.01);
    }

    private void crossAura(ServerLevel level) {
        double y = this.npc.getY() + this.npc.getBbHeight() * 0.6;
        Vec3 forward = this.castAim.lengthSqr() > 0.01 ? this.castAim : this.npc.getLookAngle();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2.0 * i / 6.0 + this.castTicks * 0.12;
            double radius = 0.85;
            level.sendParticles(new GlowDustOptions(0xFFD979, 1.3F),
                    this.npc.getX() + Math.cos(angle) * radius,
                    y + Math.sin(angle) * 0.35,
                    this.npc.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        if (this.castTicks % 12 == 0) {
            level.sendParticles(ParticleTypes.END_ROD,
                    this.npc.getX() + forward.x * 1.1, y + 0.4,
                    this.npc.getZ() + forward.z * 1.1, 3, 0.16, 0.16, 0.16, 0.01);
        }
    }

    private void finishCast(int cooldown) {
        boolean airborneCast = this.castAirborne;
        this.cast = Cast.NONE;
        this.castTicks = 0;
        this.released = false;
        this.castAirborne = false;
        this.npc.clearActionPose();
        this.npc.ensureWeapon();
        if (cooldown == SPEAR_COOLDOWN) {
            this.spearCooldown = cooldown + this.npc.getRandom().nextInt(20);
        } else if (cooldown == METEOR_COOLDOWN) {
            int base = airborneCast ? METEOR_COOLDOWN_AIR : METEOR_COOLDOWN;
            this.meteorCooldown = base + this.npc.getRandom().nextInt(airborneCast ? 40 : 120);
        }
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 岩枪要插在地上，不能插进树冠、水面或者岩壁里：从施法者脚下往上找
     * 第一块有碰撞的方块，找不到就退回施法者所在高度。
     */
    static double findGroundY(ServerLevel level, double x, double z, double fromY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                Mth.floor(x), Mth.floor(fromY) + 3, Mth.floor(z));
        for (int i = 0; i < 18; i++) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + 1.0;
            }
            pos.move(Direction.DOWN);
        }
        return fromY;
    }
}
