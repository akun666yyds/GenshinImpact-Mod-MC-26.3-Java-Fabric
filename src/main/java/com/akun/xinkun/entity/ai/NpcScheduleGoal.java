package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.GenshinNpcEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;

public class NpcScheduleGoal extends Goal {
    private final GenshinNpcEntity npc;
    private BlockPos target;
    private int cooldown = 160;
    private int stayTicks;

    public NpcScheduleGoal(GenshinNpcEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.npc.getTarget() != null || this.npc.isSpeaking()) {
            return false;
        }
        if (this.npc.isNightTime()) {
            BlockPos home = this.npc.getHomePoint();
            if (this.npc.blockPosition().distSqr(home) > 25.0) {
                this.target = home;
                return true;
            }
            this.cooldown = 200;
            return false;
        }
        if (!this.npc.isDayTime()) {
            this.cooldown = 200;
            return false;
        }
        // Straying far from home after gathering must not become a
        // permanent idle spot: walk back first, then wander near the house.
        if (this.npc.blockPosition().distSqr(this.npc.getHomePoint()) > 144.0) {
            this.target = this.npc.getHomePoint();
            return true;
        }
        if (this.npc.getRandom().nextInt(4) != 0) {
            Direction direction = Direction.from2DDataValue(this.npc.getRandom().nextInt(4));
            this.target = this.npc.getHomePoint().relative(direction, 5 + this.npc.getRandom().nextInt(8));
            return true;
        }
        // Idling is allowed, but never for long: three days out of four she
        // picks a stroll, and a failed roll comes back within a few seconds.
        // A character without a job keeps moving through the day instead of
        // standing frozen for half a minute.
        this.cooldown = 40 + this.npc.getRandom().nextInt(80);
        return false;
    }

    @Override
    public void start() {
        this.stayTicks = 600;
        this.npc.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, 0.9);
        this.cooldown = 60 + this.npc.getRandom().nextInt(80);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.npc.getTarget() != null) {
            return false;
        }
        if (this.stayTicks-- <= 0) {
            return false;
        }
        return !this.npc.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
    }
}
