package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.FurinaEntity;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FurinaCakeGoal extends Goal {
    private final FurinaEntity npc;
    private Player player;
    private BlockPos cakePos;
    private int cooldown = 1200;
    private int stayTicks;

    public FurinaCakeGoal(FurinaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.npc.getTarget() != null || !this.npc.isDayTime() || this.npc.getRandom().nextInt(40) != 0) {
            return false;
        }
        Player found = this.npc.level().getNearestPlayer(this.npc, 6.0);
        if (found == null || found.isSpectator()) {
            return false;
        }
        BlockPos spot = this.findSpot(found);
        if (spot == null) {
            return false;
        }
        this.player = found;
        this.cakePos = spot;
        return true;
    }

    private BlockPos findSpot(Player player) {
        for (int radius = 1; radius <= 3; radius++) {
            for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-radius, -1, -radius), player.blockPosition().offset(radius, 1, radius))) {
                BlockState state = this.npc.level().getBlockState(pos);
                if (!state.canBeReplaced()) {
                    continue;
                }
                BlockPos below = pos.below();
                BlockState belowState = this.npc.level().getBlockState(below);
                if (!belowState.isFaceSturdy(this.npc.level(), below, Direction.UP)) {
                    continue;
                }
                BlockState cake = Blocks.CAKE.defaultBlockState();
                if (cake.canSurvive(this.npc.level(), pos)) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    @Override
    public void start() {
        this.stayTicks = 300;
        this.npc.getNavigation().moveTo(this.cakePos.getX() + 0.5, this.cakePos.getY(), this.cakePos.getZ() + 0.5, 1.05);
    }

    @Override
    public boolean canContinueToUse() {
        return this.npc.getTarget() == null && this.stayTicks-- > 0;
    }

    @Override
    public void tick() {
        if (this.npc.blockPosition().distSqr(this.cakePos) > 9.0) {
            if (this.npc.getNavigation().isDone()) {
                this.npc.getNavigation().moveTo(this.cakePos.getX() + 0.5, this.cakePos.getY(), this.cakePos.getZ() + 0.5, 1.05);
            }
            return;
        }
        BlockPos below = this.cakePos.below();
        if (this.npc.level().getBlockState(this.cakePos).canBeReplaced()
                && this.npc.level().getBlockState(below).isFaceSturdy(this.npc.level(), below, Direction.UP)) {
            this.npc.level().setBlockAndUpdate(this.cakePos, Blocks.CAKE.defaultBlockState());
            this.npc.level().playSound(null, this.cakePos, Blocks.CAKE.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
            if (this.npc.level() instanceof ServerLevel serverLevel) {
                NpcFx.cakeBurst(serverLevel, Vec3.atCenterOf(this.cakePos));
            }
            this.npc.speak(VoiceContext.CAKE);
            this.npc.getLookControl().setLookAt(this.player, 30.0F, 30.0F);
            this.cooldown = 12000;
        }
        this.stayTicks = 0;
        this.npc.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
    }
}
