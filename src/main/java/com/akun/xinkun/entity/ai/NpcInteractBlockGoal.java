package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.GenshinNpcEntity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

public class NpcInteractBlockGoal extends Goal {
    private final GenshinNpcEntity npc;
    private BlockPos targetPos;
    private int nextStartTick = 80;
    private int stayTicks;

    public NpcInteractBlockGoal(GenshinNpcEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        }
        this.nextStartTick = 120 + this.npc.getRandom().nextInt(160);
        if (this.npc.getTarget() != null || this.npc.getRandom().nextInt(3) != 0) {
            return false;
        }
        BlockPos origin = this.npc.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -3, -8), origin.offset(8, 3, 8))) {
            if (this.isInteractable(this.npc.level().getBlockState(pos))) {
                candidates.add(pos.immutable());
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        this.targetPos = candidates.get(this.npc.getRandom().nextInt(candidates.size()));
        return true;
    }

    private boolean isInteractable(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof ButtonBlock;
    }

    @Override
    public void start() {
        this.stayTicks = 300;
        this.npc.getNavigation().moveTo(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5, 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.stayTicks-- <= 0 || this.npc.getTarget() != null) {
            return false;
        }
        return this.isInteractable(this.npc.level().getBlockState(this.targetPos));
    }

    @Override
    public void tick() {
        if (this.npc.blockPosition().distSqr(this.targetPos) > 9.0) {
            if (this.npc.getNavigation().isDone()) {
                this.npc.getNavigation().moveTo(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5, 1.0);
            }
            return;
        }
        this.npc.getLookControl().setLookAt(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
        this.interact(this.npc.level().getBlockState(this.targetPos));
        this.nextStartTick = 200 + this.npc.getRandom().nextInt(240);
        this.stayTicks = 0;
        this.npc.getNavigation().stop();
    }

    private void interact(BlockState state) {
        BlockPos pos = this.targetPos;
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(this.npc, this.npc.level(), state, pos, !state.getValue(DoorBlock.OPEN));
        } else if (state.getBlock() instanceof TrapDoorBlock) {
            boolean open = state.getValue(TrapDoorBlock.OPEN);
            this.npc.level().setBlockAndUpdate(pos, state.setValue(TrapDoorBlock.OPEN, !open));
            boolean iron = state.getBlock() == net.minecraft.world.level.block.Blocks.IRON_TRAPDOOR;
            this.npc.level().playSound(null, pos, open
                    ? (iron ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.WOODEN_TRAPDOOR_CLOSE)
                    : (iron ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_OPEN),
                    SoundSource.BLOCKS, 0.9F, 1.0F);
        } else if (state.getBlock() instanceof FenceGateBlock) {
            boolean open = state.getValue(FenceGateBlock.OPEN);
            this.npc.level().setBlockAndUpdate(pos, state.setValue(FenceGateBlock.OPEN, !open));
            this.npc.level().playSound(null, pos, open ? SoundEvents.FENCE_GATE_CLOSE : SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 0.9F, 1.0F);
        } else if (state.getBlock() instanceof LeverBlock lever) {
            lever.pull(state, this.npc.level(), pos, null);
        } else if (state.getBlock() instanceof ButtonBlock button) {
            button.press(state, this.npc.level(), pos, null);
        }
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
    }
}
