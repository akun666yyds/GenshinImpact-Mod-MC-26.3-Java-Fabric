package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.GenshinNpcEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Combat behaviour shared by every Genshin NPC.
 *
 * <p>She never gives up on a target that hurt her: as long as it lives she
 * keeps closing the distance, even across water. Vulnerable characters may
 * break away briefly to eat when badly hurt, but they immediately re-engage.
 * Creepers are the one exception to standing and trading blows: she hits once
 * and backs out of the blast radius, then comes straight back in.</p>
 *
 * <p>近战永远不是优先选项：只有贴脸两格内才按概率出手，其余时间她主动
 * 后撤到八格开外站桩，把输出交给各角色自己的技能目标。</p>
 */
public class NpcCombatGoal extends Goal {
    /** 近战只在贴脸两格内才可能出手，永远不是首选。 */
    private static final double MELEE_REACH = 2.0;
    /** 贴脸时每次出手判定的概率：近战只是兜底，不是常规手段。 */
    private static final double MELEE_CHANCE = 0.35;
    /** 比这更近就要后撤拉开：把距离留给技能，而不是留给拳头。 */
    private static final double KITE_RANGE = 8.0;
    /** 站桩等技能的距离上限：超过才往前走。 */
    private static final double HOLD_RANGE = 16.0;
    /** Beyond this a target is hopelessly lost rather than merely far away. */
    private static final double MAX_CHASE_SQR = 128.0 * 128.0;
    private static final int ATTACK_COOLDOWN = 18;
    private static final int EAT_TIME = 32;
    private static final int CREEPER_RETREAT = 24;

    private final GenshinNpcEntity npc;
    private final double speedModifier;
    private int attackCooldown;
    private int retreatTicks;
    private int eatTicks;
    private int healCooldown;
    private int repathCooldown;

    public NpcCombatGoal(GenshinNpcEntity npc, double speedModifier) {
        this.npc = npc;
        this.speedModifier = speedModifier;
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
        return this.npc.distanceToSqr(target) <= MAX_CHASE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive()
                && !(target instanceof net.minecraft.world.entity.player.Player)
                && !(target instanceof GenshinNpcEntity);
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
        this.retreatTicks = 0;
        this.eatTicks = 0;
        this.repathCooldown = 0;
        this.npc.ensureWeapon();
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
        this.npc.stowTool();
        this.npc.stopSwimmingPose();
        this.attackCooldown = 0;
        this.retreatTicks = 0;
        this.eatTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.npc.getTarget();
        if (target == null) {
            return;
        }
        if (this.healCooldown > 0) {
            this.healCooldown--;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }
        this.npc.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // 目标在水下时，用玩家的游泳姿势追进去，而不是站在岸边等。
        if (this.npc.isInWater()) {
            this.npc.swimToward(target.getX(), target.getEyeY() - 0.6, target.getZ(), 0.16);
        } else if (target.isInWater()) {
            // 还在岸上：先朝目标游过去，入水后上面的分支接管。
            if (this.repathCooldown <= 0
                    && (this.npc.getNavigation().isDone() || this.npc.getNavigation().isStuck())) {
                this.repathCooldown = 6;
                this.npc.getNavigation().moveTo(target, this.speedModifier);
            }
        } else {
            this.npc.stopSwimmingPose();
        }

        if (this.eatTicks > 0) {
            this.eatTicks--;
            this.npc.getNavigation().stop();
            if (this.eatTicks == 16) {
                this.npc.level().playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                        SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6F, 1.0F);
            }
            if (this.eatTicks == 0 && this.npc.consumeItem(Items.BREAD, 1)) {
                this.npc.heal(6.0F);
                this.healCooldown = 600;
                this.npc.level().playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.5F, 1.0F);
                if (this.npc.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.npc.getX(), this.npc.getY() + 1.4,
                            this.npc.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
                }
            }
            return;
        }

        double distance = Math.sqrt(this.npc.distanceToSqr(target));
        boolean badlyHurt = this.npc.getHealth() <= this.npc.getMaxHealth() * 0.3F;
        boolean canEat = this.npc.countItem(Items.BREAD) > 0 && this.healCooldown <= 0;

        // 濒死时才短暂脱离，吃完立刻回头继续追。
        if (badlyHurt && canEat) {
            if (distance < 7.0) {
                this.retreatFrom(target, 1.4);
                return;
            }
            this.npc.getNavigation().stop();
            this.eatTicks = EAT_TIME;
            return;
        }

        if (this.retreatTicks > 0) {
            this.retreatTicks--;
            if (this.npc.getNavigation().isDone()) {
                this.retreatFrom(target, 1.35);
            }
            return;
        }

        // 苦力怕：打完就退，退到安全距离后再冲回去补刀。
        if (target instanceof Creeper && distance < 5.0) {
            this.retreatFrom(target, 1.5);
            this.retreatTicks = CREEPER_RETREAT;
            return;
        }

        // 贴脸两格：近战只是概率兜底，绝大多数时候她宁可退开等技能。
        if (distance <= MELEE_REACH) {
            this.npc.getNavigation().stop();
            if (this.attackCooldown <= 0
                    && this.npc.getSensing().hasLineOfSight(target)
                    && this.npc.getRandom().nextDouble() < MELEE_CHANCE
                    && this.npc.level() instanceof ServerLevel serverLevel) {
                this.npc.swingForAttack(InteractionHand.MAIN_HAND);
                this.npc.doHurtTarget(serverLevel, target);
                this.attackCooldown = ATTACK_COOLDOWN;
                if (target instanceof Creeper) {
                    this.retreatTicks = CREEPER_RETREAT + 10;
                }
            }
            return;
        }

        // 水里没有“拉开距离”一说：照旧游过去贴身，否则会在岸边干等。
        boolean aquatic = this.npc.isInWater() || target.isInWater();
        if (aquatic) {
            if (this.repathCooldown <= 0
                    && (this.npc.getNavigation().isDone() || this.npc.getNavigation().isStuck())) {
                this.repathCooldown = 6;
                this.npc.getNavigation().moveTo(target, this.speedModifier);
            }
            return;
        }

        // 中距离先往后撤，把 8 格以内的空间留给技能而不是拳头。
        if (distance < KITE_RANGE) {
            if (this.repathCooldown <= 0 && this.npc.getNavigation().isDone()) {
                this.repathCooldown = 12;
                this.retreatFrom(target, 1.2);
            }
            return;
        }
        // 8~16 格站桩等技能冷却；再远才往前走，但也只走到 16 格为止。
        if (distance > HOLD_RANGE) {
            if (this.repathCooldown <= 0
                    && (this.npc.getNavigation().isDone() || this.npc.getNavigation().isStuck())) {
                this.repathCooldown = 6;
                this.npc.getNavigation().moveTo(target, this.speedModifier);
            }
            return;
        }
        this.npc.getNavigation().stop();
    }

    private void retreatFrom(LivingEntity target, double speedMultiplier) {
        Vec3 away = this.npc.position().subtract(target.position());
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(this.npc.getRandom().nextDouble() - 0.5, 0.0, this.npc.getRandom().nextDouble() - 0.5);
        }
        Vec3 destination = this.npc.position().add(away.normalize().scale(6.0));
        BlockPos pos = BlockPos.containing(destination.x, this.npc.getY(), destination.z);
        if (!this.npc.level().getBlockState(pos).getFluidState().isEmpty()) {
            Vec3 side = new Vec3(-away.z, 0.0, away.x).normalize().scale(6.0);
            destination = this.npc.position().add(side);
        }
        this.npc.getNavigation().moveTo(destination.x, destination.y, destination.z, this.speedModifier * speedMultiplier);
    }
}
