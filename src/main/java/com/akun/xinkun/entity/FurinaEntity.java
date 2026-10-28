package com.akun.xinkun.entity;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.entity.ai.FurinaCakeGoal;
import com.akun.xinkun.entity.ai.FurinaExtinguishGoal;
import com.akun.xinkun.entity.ai.FurinaWaterMagicGoal;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModEntities;
import com.akun.xinkun.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class FurinaEntity extends GenshinNpcEntity {
    private static final double SPIRIT_SEARCH_RADIUS = 64.0;

    public FurinaEntity(EntityType<? extends FurinaEntity> type, Level level) {
        super(type, level);
    }

    /** 芙宁娜自身攻击按共享基础值的 1.5 倍计算。 */
    public static AttributeSupplier.Builder createAttributes() {
        return GenshinNpcEntity.createAttributes().add(Attributes.ATTACK_DAMAGE, 7.5);
    }

    @Override
    public GenshinCharacter getCharacter() {
        return GenshinCharacter.FURINA;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.tickCount % 20 != 0) {
            return;
        }
        // The trio is an extension of her magic, not a one-off summon: while she
        // has prey the three shapes are kept in the field even if one dissolves.
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.summonWaterSpiritTrio(null, null, target);
        }
    }

    @Override
    protected void registerCharacterGoals() {
        // Fire response sits above magic, work and combat: a blaze is an
        // emergency, and the goal itself carries no movement flags.
        this.goalSelector.addGoal(1, new FurinaExtinguishGoal(this));
        this.goalSelector.addGoal(2, new FurinaWaterMagicGoal(this));
        this.goalSelector.addGoal(5, new FurinaCakeGoal(this));
    }

    /**
     * Summons one seahorse, one crab and one octopus in a single cast. Every
     * Furina performs the full trio each time, so the three shapes always
     * appear together.
     */
    public void summonWaterSpiritTrio(BlockPos firePos, LivingEntity burningTarget, LivingEntity attackTarget) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean spawned = false;
        WaterSpiritKind[] kinds = WaterSpiritKind.values();
        for (int i = 0; i < kinds.length; i++) {
            WaterSpiritEntity existing = this.findOwnedSpirit(kinds[i]);
            if (existing != null) {
                this.assignTask(existing, firePos, burningTarget, attackTarget);
                continue;
            }
            double angle = Math.PI * 2.0 * i / kinds.length
                    + (this.getRandom().nextDouble() - 0.5) * 0.5;
            if (this.spawnWaterSpirit(kinds[i], angle, firePos, burningTarget, attackTarget) != null) {
                spawned = true;
            }
        }
        if (spawned) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.WATER_SUMMON,
                    SoundSource.NEUTRAL, 0.9F, 0.95F + this.getRandom().nextFloat() * 0.1F);
        }
    }

    public WaterSpiritEntity summonWaterSpirit(WaterSpiritKind kind, BlockPos firePos, LivingEntity burningTarget,
                                               LivingEntity attackTarget) {
        WaterSpiritEntity existing = this.findOwnedSpirit(kind);
        if (existing != null) {
            this.assignTask(existing, firePos, burningTarget, attackTarget);
            return existing;
        }
        double angle = this.getRandom().nextDouble() * Math.PI * 2.0;
        return this.spawnWaterSpirit(kind, angle, firePos, burningTarget, attackTarget);
    }

    /**
     * A Furina keeps at most one spirit of each shape alive. Recasting redirects
     * the existing trio instead of stacking a second set that would linger until
     * every 30-second lifetime expires independently.
     */
    private WaterSpiritEntity findOwnedSpirit(WaterSpiritKind kind) {
        List<WaterSpiritEntity> spirits = this.level().getEntitiesOfClass(WaterSpiritEntity.class,
                this.getBoundingBox().inflate(SPIRIT_SEARCH_RADIUS),
                spirit -> spirit.isAlive() && spirit.getKind() == kind && spirit.getOwner() == this);
        return spirits.isEmpty() ? null : spirits.get(0);
    }

    private void assignTask(WaterSpiritEntity spirit, BlockPos firePos, LivingEntity burningTarget,
                            LivingEntity attackTarget) {
        // Only overwrite what this call actually carries: a combat refresh must
        // not wipe an emergency fire task, and a fire call must not drop prey.
        if (firePos != null || burningTarget != null) {
            spirit.setFirefightTarget(firePos, burningTarget);
        }
        if (attackTarget != null && attackTarget.isAlive()) {
            spirit.setTarget(attackTarget);
        }
    }

    private WaterSpiritEntity spawnWaterSpirit(WaterSpiritKind kind, double angle, BlockPos firePos,
                                               LivingEntity burningTarget, LivingEntity attackTarget) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        double distance = 1.5 + this.getRandom().nextDouble() * 0.8;
        BlockPos spawnPos = BlockPos.containing(
                this.getX() + Math.cos(angle) * distance,
                this.getY() + 1.0 + this.getRandom().nextDouble() * 0.4,
                this.getZ() + Math.sin(angle) * distance);
        EntityType<WaterSpiritEntity> type = ModEntities.waterSpirit(kind);
        WaterSpiritEntity spirit = type.spawn(serverLevel, spawnPos, EntitySpawnReason.EVENT);
        if (spirit == null) {
            return null;
        }
        spirit.setOwner(this);
        spirit.setBoundOrigin(this.blockPosition());
        spirit.setLifeTicks(WaterSpiritEntity.LIFETIME_TICKS);
        spirit.setFirefightTarget(firePos, burningTarget);
        if (attackTarget != null && attackTarget.isAlive()) {
            spirit.setTarget(attackTarget);
        }
        Vec3 point = Vec3.atCenterOf(spawnPos);
        NpcFx.spiritSummon(serverLevel, point);
        return spirit;
    }
}
