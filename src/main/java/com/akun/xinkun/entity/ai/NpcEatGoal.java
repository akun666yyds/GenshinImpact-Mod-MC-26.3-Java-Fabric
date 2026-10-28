package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.GenshinNpcEntity;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;

public class NpcEatGoal extends Goal {
    private final GenshinNpcEntity npc;
    private int timer;
    private int nextStartTick = 40;

    public NpcEatGoal(GenshinNpcEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        }
        this.nextStartTick = 80;
        if (this.npc.getTarget() != null || this.npc.getHealth() >= this.npc.getMaxHealth() * 0.6F) {
            return false;
        }
        return this.npc.countItem(Items.BREAD) > 0;
    }

    @Override
    public void start() {
        this.timer = 30;
        this.npc.getNavigation().stop();
    }

    @Override
    public boolean canContinueToUse() {
        return this.timer > 0 && this.npc.isAlive() && this.npc.countItem(Items.BREAD) > 0;
    }

    @Override
    public void tick() {
        this.timer--;
        if (this.timer == 15 && this.npc.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6F, 1.0F);
        }
        if (this.timer <= 0) {
            if (this.npc.consumeItem(Items.BREAD, 1)) {
                this.npc.heal(4.0F);
                this.npc.level().playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(), SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.6F, 1.0F);
                if (this.npc.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.npc.getX(), this.npc.getY() + 1.4, this.npc.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
                }
            }
            this.nextStartTick = 200;
        }
    }

    @Override
    public void stop() {
        this.nextStartTick = 100;
    }
}
