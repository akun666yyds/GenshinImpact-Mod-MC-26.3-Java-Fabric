package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.GenshinNpcEntity;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class NpcPickupGoal extends Goal {
    /** How long a drop may stay out of reach before it is given up. */
    private static final int STUCK_LIMIT = 100;
    private final GenshinNpcEntity npc;
    /** Drops that already proved unreachable (floating in leaves, sealed in walls). */
    private final Set<UUID> ignored = new HashSet<>();
    private ItemEntity item;
    private int nextStartTick = 20;
    private int stuckTicks;
    private double bestDistance = Double.MAX_VALUE;

    public NpcPickupGoal(GenshinNpcEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        }
        this.nextStartTick = 20;
        if (this.npc.getTarget() != null) {
            return false;
        }
        this.item = this.npc.level().getEntitiesOfClass(ItemEntity.class, this.npc.getBoundingBox().inflate(12.0),
                entity -> entity.isAlive()
                        && !entity.getItem().isEmpty()
                        && isUseful(entity.getItem())
                        && !this.ignored.contains(entity.getUUID())
                        && this.isReachableDrop(entity)
                        && this.npc.canAccept(entity.getItem()))
                .stream()
                .min(java.util.Comparator.comparingDouble(this.npc::distanceToSqr))
                .orElse(null);
        return this.item != null;
    }

    /**
     * Drops that fell inside a wall or a leaf canopy can never be touched. A
     * player simply leaves them there, so the character does too instead of
     * standing below them forever.
     */
    private boolean isReachableDrop(ItemEntity entity) {
        return this.npc.level().getBlockState(entity.blockPosition())
                .getCollisionShape(this.npc.level(), entity.blockPosition())
                .isEmpty();
    }

    /**
     * Things a player categorically leaves on the ground. Enemy loot such as
     * bones, string, gunpowder and ender pearls stays useful and is kept.
     */
    private static boolean isUseful(ItemStack stack) {
        return !stack.is(Items.ROTTEN_FLESH)
                && !stack.is(Items.SPIDER_EYE)
                && !stack.is(Items.POISONOUS_POTATO)
                && !stack.is(Items.PUFFERFISH)
                && !stack.is(Items.TROPICAL_FISH);
    }

    @Override
    public void start() {
        this.stuckTicks = 0;
        this.bestDistance = Double.MAX_VALUE;
        this.npc.getNavigation().moveTo(this.item, 1.1);
    }

    @Override
    public boolean canContinueToUse() {
        return this.item != null && this.item.isAlive() && !this.item.getItem().isEmpty()
                && isUseful(this.item.getItem())
                && this.npc.canAccept(this.item.getItem());
    }

    @Override
    public void tick() {
        if (this.item == null || !this.item.isAlive()) {
            return;
        }
        double distance = this.npc.distanceToSqr(this.item);
        if (distance <= 2.25) {
            this.npc.pickUpItem(this.item);
            this.nextStartTick = 10;
            return;
        }
        // A drop that never gets closer is out of reach for good. Give up after
        // a few seconds so chasing it cannot pin the character in place.
        if (distance < this.bestDistance - 0.25) {
            this.bestDistance = distance;
            this.stuckTicks = 0;
        } else if (++this.stuckTicks > STUCK_LIMIT) {
            this.ignored.add(this.item.getUUID());
            this.item = null;
            return;
        }
        if (this.npc.getNavigation().isDone()) {
            this.npc.getNavigation().moveTo(this.item, 1.1);
        }
    }

    @Override
    public void stop() {
        this.item = null;
        this.npc.getNavigation().stop();
    }
}
