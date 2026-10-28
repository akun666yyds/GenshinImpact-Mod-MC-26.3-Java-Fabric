package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.ZhongliEntity;
import java.util.EnumSet;
import com.akun.xinkun.particle.GlowDustOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 把地脉吸来的矿财当面交给玩家。
 *
 * <p>吸矿结束时矿石不会凭空进背包：钟离先收在袖里，随后走向最近的玩家，
 * 在两步之内停下、转身，把每一摞矿物沿一条短抛物线抛到对方脚边。
 * 附近没有玩家时矿财一直留着，等他遇见人为止；战斗中则先应战，
 * 打完再把东西送出去。</p>
 */
public class ZhongliGiftGoal extends Goal {
    /** 只把礼物送给这个距离内的玩家，太远就先留着。 */
    private static final double SEARCH_RADIUS = 96.0;
    /** 走到两步以内就算“当面”。 */
    private static final double DELIVER_RANGE_SQR = 6.25;
    /** 玩家跑出这个距离就暂时放弃，礼物不丢。 */
    private static final double GIVE_UP_DISTANCE_SQR = 160.0 * 160.0;
    private static final int PATIENCE = 600;
    private static final int THROW_INTERVAL = 7;

    private final ZhongliEntity npc;
    private ServerPlayer player;
    private int ticks;
    private int throwCooldown;

    public ZhongliGiftGoal(ZhongliEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.npc.hasPendingGifts() || this.npc.getTarget() != null) {
            return false;
        }
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return false;
        }
        this.player = this.findNearestPlayer(level);
        return this.player != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.npc.isAlive()
                && this.npc.hasPendingGifts()
                && this.npc.getTarget() == null
                && this.ticks < PATIENCE
                && this.isReachable(this.player);
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.throwCooldown = 0;
        this.npc.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
        this.player = null;
    }

    @Override
    public void tick() {
        if (!(this.npc.level() instanceof ServerLevel level) || !this.isReachable(this.player)) {
            return;
        }
        this.ticks++;
        this.npc.getLookControl().setLookAt(this.player, 40.0F, 40.0F);
        if (this.npc.distanceToSqr(this.player) > DELIVER_RANGE_SQR) {
            if (this.npc.getNavigation().isDone() || this.npc.getNavigation().isStuck()) {
                this.npc.getNavigation().moveTo(this.player, 1.25);
            }
            return;
        }
        this.npc.getNavigation().stop();
        this.npc.getLookControl().setLookAt(this.player, 40.0F, 40.0F);
        if (this.throwCooldown > 0) {
            this.throwCooldown--;
            return;
        }
        this.throwCooldown = THROW_INTERVAL;
        this.throwGift(level);
    }

    private void throwGift(ServerLevel level) {
        ItemStack stack = this.npc.takePendingGift();
        if (stack.isEmpty()) {
            return;
        }
        Vec3 from = new Vec3(this.npc.getX(),
                this.npc.getY() + this.npc.getBbHeight() * 0.78, this.npc.getZ());
        Vec3 to = new Vec3(this.player.getX(),
                this.player.getY() + this.player.getBbHeight() * 0.55, this.player.getZ());
        Vec3 aim = to.subtract(from);
        double distance = aim.length();
        Vec3 dir = distance < 0.01 ? new Vec3(0.0, 0.0, 1.0) : aim.scale(1.0 / distance);
        // 十 tick 左右落在对方脚边的短抛物线；距离越远水平速度略大。
        double travel = 10.0;
        double vx = aim.x / travel;
        double vz = aim.z / travel;
        double vy = aim.y / travel + 0.20;
        ItemEntity item = new ItemEntity(level,
                from.x + dir.x * 0.35,
                from.y + 0.05,
                from.z + dir.z * 0.35,
                stack);
        item.setDeltaMovement(vx, vy, vz);
        // 刚离手的一瞬间不能立刻被吸走，否则看不清“当面丢出”的动作。
        item.setPickUpDelay(8);
        level.addFreshEntity(item);
        level.playSound(null, from.x, from.y, from.z,
                SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.45F, 1.15F);
        level.sendParticles(new GlowDustOptions(0xE8C46A, 0.9F),
                from.x, from.y, from.z, 5, 0.18, 0.18, 0.18, 0.02);
        level.sendParticles(ParticleTypes.END_ROD,
                from.x, from.y, from.z, 2, 0.1, 0.1, 0.1, 0.01);
    }

    private ServerPlayer findNearestPlayer(ServerLevel level) {
        PlayerCandidate candidate = null;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            double distance = player.distanceToSqr(this.npc);
            if (distance > SEARCH_RADIUS * SEARCH_RADIUS) {
                continue;
            }
            if (candidate == null || distance < candidate.distance) {
                candidate = new PlayerCandidate(player, distance);
            }
        }
        return candidate == null ? null : candidate.player;
    }

    private boolean isReachable(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator()
                && player.level() == this.npc.level()
                && player.distanceToSqr(this.npc) <= GIVE_UP_DISTANCE_SQR;
    }

    private record PlayerCandidate(ServerPlayer player, double distance) {
    }
}
