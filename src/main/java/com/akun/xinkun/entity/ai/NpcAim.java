package com.akun.xinkun.entity.ai;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 三维瞄准工具：无论目标在头顶、脚下还是背后，都从施法者眼睛向目标身体
 * 做真正的方块射线检测。躯干、眼睛、脚边依次尝试，任何一处畅通就返回
 * 可以命中的落点；全部被挡才返回 {@code null}。
 *
 * <p>这样远攻既不会因为目标站在高处或坑底而拒绝出手，也不会隔着墙壁
 * 凭空造成伤害。返回的落点同时作为射线与弹道的终点，视觉和判定一致。</p>
 */
public final class NpcAim {
    private static final double MAX_RANGE_SQR = 128.0 * 128.0;

    private NpcAim() {
    }

    /** 返回第一个没有方块阻挡的瞄准点；完全被挡住时返回 {@code null}。 */
    public static Vec3 clearAim(Entity shooter, LivingEntity target) {
        if (shooter.level() != target.level()) {
            return null;
        }
        Vec3 from = shooter.getEyePosition();
        Vec3 body = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        Vec3 eye = target.getEyePosition();
        Vec3 feet = target.position().add(0.0, Math.min(0.45, target.getBbHeight() * 0.3), 0.0);
        for (Vec3 to : new Vec3[] { body, eye, feet }) {
            if (from.distanceToSqr(to) > MAX_RANGE_SQR) {
                continue;
            }
            HitResult hit = shooter.level().clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (hit.getType() == HitResult.Type.MISS) {
                return to;
            }
        }
        return null;
    }

    /**
     * 目标是否真的处在近战能够碰到的位置：水平贴脸且高度相近。
     * 头顶或脚下的敌人不算贴身，远攻必须继续接管。
     */
    public static boolean withinMeleeReach(LivingEntity self, LivingEntity target,
                                           double radius, double vertical) {
        double dx = target.getX() - self.getX();
        double dz = target.getZ() - self.getZ();
        double dy = target.getY() - self.getY();
        return dx * dx + dz * dz <= radius * radius && Math.abs(dy) <= vertical;
    }

    /**
     * 水平距离的平方。凋零这类目标飞在头顶时，三维距离会被高度差撑爆，
     * 让远攻误判为“太远”而拒绝施法；判断射程应该看水平距离，
     * 高度差单独用垂直射程衡量。
     */
    public static double horizontalDistanceSqr(Entity a, Entity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** 目标是否高悬在施法者上方，需要用对空招式而不是贴地技能。 */
    public static boolean isHighAbove(Entity shooter, LivingEntity target) {
        return target.getY() > shooter.getY() + 2.5;
    }
}
