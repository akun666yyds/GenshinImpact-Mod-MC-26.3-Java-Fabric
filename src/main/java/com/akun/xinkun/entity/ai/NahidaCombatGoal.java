package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.NahidaEntity;
import com.akun.xinkun.item.VerdantCodexItem;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 纳西妲的草叶横扫：先主动追到她能扫到的距离，再翻开绿色书卷，
 * 让一圈草木叶片从脚下卷出去，命中范围内所有敌人。
 *
 * <p>通用战斗目标是“拉开距离等技能”，但纳西妲的技能只有近身横扫，
 * 如果沿用那套后撤逻辑，她会永远停在八格外既不施法也不近身。
 * 这个目标优先级更高，负责把距离缩短到横扫范围内。</p>
 */
public class NahidaCombatGoal extends Goal {
    private static final double RANGE = VerdantCodexItem.SWEEP_RADIUS + 0.8;
    /** 敌人跑出这个距离就不再追：和通用战斗目标的上限保持一致。 */
    private static final double CHASE_RANGE = 32.0;
    /** 垂直追击范围：横扫地圆柱高度与索敌保持一致。 */
    private static final double VERTICAL_REACH = VerdantCodexItem.SWEEP_VERTICAL_REACH;
    private static final float DAMAGE = 14.0F;

    private final NahidaEntity npc;
    private int cooldown;
    private int repathCooldown;

    public NahidaCombatGoal(NahidaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.npc.getTarget();
        return target != null && target.isAlive()
                && this.npc.canFightNow()
                && NpcAim.horizontalDistanceSqr(this.npc, target) <= CHASE_RANGE * CHASE_RANGE
                && Math.abs(target.getY() - this.npc.getY()) <= VERTICAL_REACH;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.repathCooldown = 0;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.npc.getTarget();
        if (target == null) {
            return;
        }
        this.npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }

        double distanceSqr = NpcAim.horizontalDistanceSqr(this.npc, target);
        // 飞行目标只追水平位置，横扫地本身是圆柱判定。
        boolean airborne = NpcAim.isHighAbove(this.npc, target);
        if (distanceSqr > RANGE * RANGE) {
            // 还在横扫范围外：主动追过去，不能站着等对方走进来。
            if (this.repathCooldown <= 0
                    && (this.npc.getNavigation().isDone() || this.npc.getNavigation().isStuck())) {
                this.repathCooldown = 8;
                double speed = 1.35;
                boolean moved = airborne
                        ? this.npc.getNavigation().moveTo(target.getX(), this.npc.getY(), target.getZ(), speed)
                        : this.npc.getNavigation().moveTo(target, speed);
                if (!moved && this.npc.level() instanceof ServerLevel level) {
                    // 寻路失败时直接朝目标方向迈步；空中/水里也能继续接近。
                    this.npc.getMoveControl().setWantedPosition(
                            target.getX(), airborne ? this.npc.getY() : target.getY(), target.getZ(), speed);
                }
            }
            return;
        }

        this.npc.getNavigation().stop();
        if (this.cooldown > 0) {
            return;
        }
        if (!this.npc.ensureWeapon()) {
            return;
        }
        if (this.npc.level() instanceof ServerLevel level) {
            this.npc.swingForAttack(InteractionHand.MAIN_HAND);
            VerdantCodexItem.sweep(level, this.npc, DAMAGE);
        }
        this.cooldown = VerdantCodexItem.SWEEP_COOLDOWN;
    }
}
