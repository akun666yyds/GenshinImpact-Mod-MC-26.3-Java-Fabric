package com.akun.xinkun.entity.projectile;

import com.akun.xinkun.fx.NpcFx;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 宵宫的烟花火箭。它是一枚真正飞行的弹射物：有初速、有可见的拖尾，但
 * 不受重力影响，瞄准在哪里就飞到哪里，朝头顶或脚下的敌人发射同样能命中。
 * 命中方块、命中敌人或飞满射程后才爆开，在落点附近造成比火焰箭更高的
 * 伤害与更大的范围，且只影响敌对生物。
 */
public class FireworkRocketProjectile extends ThrowableItemProjectile {
    public static final float BLAST_RADIUS = 7.5F;
    public static final float CENTER_DAMAGE = 22.5F;
    public static final float EDGE_DAMAGE = 10.125F;
    public static final float IGNITE_SECONDS = 8.0F;
    /** About four seconds of flight; enough to reach any target in range. */
    private static final int MAX_LIFE_TICKS = 80;

    private int lifeTicks;
    private boolean burst;

    public FireworkRocketProjectile(EntityType<? extends FireworkRocketProjectile> type, Level level) {
        super(type, level);
    }

    public FireworkRocketProjectile(EntityType<? extends FireworkRocketProjectile> type, LivingEntity owner, Level level) {
        super(type, owner, level, new ItemStack(Items.FIREWORK_ROCKET));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIREWORK_ROCKET;
    }

    /** Rockets keep most of their speed, so the flight stays readable. */
    @Override
    protected float getAirDrag() {
        return 0.995F;
    }

    /** 无重力：直线飞行，抬头和低头都能命中。 */
    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.burst) {
            return;
        }
        NpcFx.rocketTrail(level, this.position());
        if (++this.lifeTicks >= MAX_LIFE_TICKS) {
            this.burst(level, this.position());
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level) {
            this.burst(level, hit.getLocation());
        }
    }

    private void burst(ServerLevel level, Vec3 center) {
        if (this.burst) {
            return;
        }
        this.burst = true;
        NpcFx.fireworkBurst(level, center, BLAST_RADIUS / 5.0F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.NEUTRAL, 1.35F, 0.92F);
        AABB area = new AABB(center, center).inflate(BLAST_RADIUS);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area);
        Entity owner = this.getOwner();
        for (LivingEntity victim : victims) {
            if (victim == owner || !victim.isAlive()) {
                continue;
            }
            // 烟花是宵宫的舞台：玩家、同伴与被动生物绝不被波及。
            if (!(victim instanceof Enemy) || victim instanceof Creeper) {
                continue;
            }
            Vec3 body = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
            double distance = body.distanceTo(center);
            if (distance > BLAST_RADIUS) {
                continue;
            }
            float strength = (float) (1.0 - distance / BLAST_RADIUS);
            float damage = EDGE_DAMAGE + (CENTER_DAMAGE - EDGE_DAMAGE) * strength;
            victim.hurtServer(level, level.damageSources().magic(), damage);
            victim.igniteForSeconds(IGNITE_SECONDS);
        }
        this.discard();
    }
}
