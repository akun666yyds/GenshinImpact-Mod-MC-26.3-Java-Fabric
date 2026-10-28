package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.ZhongliEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import com.akun.xinkun.particle.GlowDustOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 钟离的挖矿方式：不挥镐，而是把附近地脉里的矿石整块唤出来。
 *
 * <p>触发是随机的，不占用固定时间表；一旦起手，他双臂张开站定，
 * 矿石会一圈一圈从岩层里浮起，迅速沿弧线追上他的位置。受袭或看见
 * 敌人会立刻收势，但已经在空中的矿石不会凭空消失，仍会落进背包。</p>
 */
public class ZhongliLeystoneGoal extends Goal {
    /** 地脉感知半径。 */
    private static final int RADIUS = 12;
    private static final int VERTICAL = 8;
    /** 一次最多搬走多少块，避免把整条矿脉连同地形一起掏空。 */
    private static final int MAX_BLOCKS = 20;
    private static final int MIN_BLOCKS = 2;
    /** 每批吸走几块。 */
    private static final int BATCH_SIZE = 3;
    private static final int BATCH_INTERVAL = 4;
    private static final int WINDUP = 8;

    private final ZhongliEntity npc;
    private final Deque<BlockPos> pending = new ArrayDeque<>();
    private int castTicks;
    private int duration;
    private int batchCooldown;
    private int cooldown;

    public ZhongliLeystoneGoal(ZhongliEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.npc.getTarget() != null) {
            this.cooldown = 40;
            return false;
        }
        // 随机刻式触发：大多数检查只是空手而归。
        if (this.npc.getRandom().nextInt(3) != 0) {
            this.cooldown = 60;
            return false;
        }
        List<BlockPos> ores = this.findOres();
        if (ores.size() < MIN_BLOCKS) {
            this.cooldown = 120;
            return false;
        }
        this.pending.clear();
        this.pending.addAll(ores);
        this.duration = Math.min(96, 44 + ores.size() * 2);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.npc.isAlive()
                && this.npc.getTarget() == null
                && this.castTicks < this.duration
                && !this.pending.isEmpty();
    }

    @Override
    public void start() {
        this.castTicks = 0;
        this.batchCooldown = 0;
        this.npc.getNavigation().stop();
        this.npc.setActionPose(ZhongliEntity.POSE_ABSORB, this.duration + 4);
        if (this.npc.level() instanceof ServerLevel level) {
            level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 0.6F);
        }
    }

    @Override
    public void stop() {
        this.npc.clearActionPose();
        this.pending.clear();
        this.castTicks = 0;
        // 吸完一次要歇一会儿，不然矿石会被瞬间搬空。
        this.cooldown = 220 + this.npc.getRandom().nextInt(180);
    }

    @Override
    public void tick() {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        this.castTicks++;
        this.npc.getNavigation().stop();
        if (this.castTicks < WINDUP) {
            return;
        }
        if (this.batchCooldown > 0) {
            this.batchCooldown--;
        } else {
            this.batchCooldown = BATCH_INTERVAL;
            for (int i = 0; i < BATCH_SIZE && !this.pending.isEmpty(); i++) {
                this.npc.beginAbsorb(level, this.pending.removeFirst());
            }
        }
        if (this.castTicks % 5 == 0) {
            this.absorbAura(level);
        }
        if (this.castTicks % 24 == 0) {
            level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5F, 0.75F);
        }
    }

    /** 双臂张开时脚下缓慢旋转的岩金色地脉环。 */
    private void absorbAura(ServerLevel level) {
        double baseY = this.npc.getY() + 0.12;
        GlowDustOptions dust = new GlowDustOptions(0xE8C46A, 1.1F);
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2.0 * i / 16.0 + this.castTicks * 0.06;
            double radius = 1.6;
            level.sendParticles(dust,
                    this.npc.getX() + Math.cos(angle) * radius, baseY,
                    this.npc.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD,
                this.npc.getX(), this.npc.getY() + this.npc.getBbHeight() * 0.6, this.npc.getZ(),
                2, 0.5, 0.6, 0.5, 0.01);
    }

    private List<BlockPos> findOres() {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return List.of();
        }
        BlockPos feet = this.npc.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                feet.offset(-RADIUS, -VERTICAL, -RADIUS),
                feet.offset(RADIUS, VERTICAL, RADIUS))) {
            if (!this.isAbsorbable(pos, feet)) {
                continue;
            }
            found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(pos -> this.npc.distanceToSqr(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        if (found.size() > MAX_BLOCKS) {
            return new ArrayList<>(found.subList(0, MAX_BLOCKS));
        }
        return found;
    }

    private boolean isAbsorbable(BlockPos pos, BlockPos feet) {
        BlockState state = this.npc.level().getBlockState(pos);
        if (!state.is(BlockTags.ORES) || state.hasBlockEntity()) {
            return false;
        }
        // 脚底那一块不能抽，否则他会跟着塌下去。
        return pos.getX() != feet.getX() || pos.getZ() != feet.getZ()
                || pos.getY() >= feet.getY();
    }
}
