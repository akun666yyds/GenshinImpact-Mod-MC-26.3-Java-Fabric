package com.akun.xinkun.entity;

import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A friendly water spirit summoned by Furina. It deliberately keeps none of the
 * vex's hostile goals, weapon or voice; only the floating movement is reused.
 */
public class WaterSpiritEntity extends Vex {
    public static final int LIFETIME_TICKS = 600;

    private final WaterSpiritKind kind;
    private int lifeTicks = LIFETIME_TICKS;
    private BlockPos firePos;
    private LivingEntity burningTarget;

    public WaterSpiritEntity(EntityType<? extends WaterSpiritEntity> type, Level level, WaterSpiritKind kind) {
        super(type, level);
        this.kind = kind;
        this.moveControl = new com.akun.xinkun.entity.ai.SpiritMoveControl(this);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.setPermanentlyInvulnerable(true);
        this.noPhysics = true;
        this.setCustomName(Component.translatable(this.kind.translationKey()));
        this.setCustomNameVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    public WaterSpiritKind getKind() {
        return this.kind;
    }

    public int getLifeTicks() {
        return this.lifeTicks;
    }

    public void setLifeTicks(int ticks) {
        this.lifeTicks = Math.max(1, Math.min(LIFETIME_TICKS, ticks));
    }

    public void setFirefightTarget(BlockPos pos, LivingEntity entity) {
        this.firePos = pos == null ? null : pos.immutable();
        this.burningTarget = entity;
    }

    public BlockPos getFirePos() {
        return this.firePos;
    }

    /** Called after the block has been doused; the creature task, if any, stays. */
    public void clearFirePos() {
        this.firePos = null;
    }

    public LivingEntity getBurningTarget() {
        return this.burningTarget;
    }

    public boolean hasFirefightTask() {
        return this.firePos != null || this.burningTarget != null;
    }

    public boolean isFirefightTaskValid() {
        boolean blockBurning = this.firePos != null
                && this.level().getBlockState(this.firePos).is(BlockTags.FIRE);
        boolean creatureBurning = this.burningTarget != null
                && this.burningTarget.isAlive()
                && this.burningTarget.isOnFire()
                && !(this.burningTarget instanceof net.minecraft.world.entity.monster.Enemy);
        return blockBurning || creatureBurning;
    }

    public void finishFirefightTask() {
        this.firePos = null;
        this.burningTarget = null;
        this.setIsCharging(false);
    }

    public void equipSpiritWeapon() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new com.akun.xinkun.entity.ai.SpiritFirefightGoal(this));
        this.goalSelector.addGoal(1, new com.akun.xinkun.entity.ai.SpiritCombatGoal(this));
        this.goalSelector.addGoal(2, new com.akun.xinkun.entity.ai.SpiritHoverGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.08F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // 水灵只使用自己的三叉戟，不继承恼鬼的铁剑
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason reason, SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnGroupData);
        this.equipSpiritWeapon();
        this.setCustomName(Component.translatable(this.kind.translationKey()));
        this.setCustomNameVisible(false);
        return data;
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = true;
        if (this.level().isClientSide()) {
            return;
        }
        if (!this.isPermanentlyInvulnerable()) {
            this.setPermanentlyInvulnerable(true);
        }
        if (this.getBoundOrigin() == null) {
            this.setBoundOrigin(this.blockPosition());
        }
        if (!this.hasCustomName()) {
            this.setCustomName(Component.translatable(this.kind.translationKey()));
            this.setCustomNameVisible(false);
        }
        if (this.getMainHandItem().isEmpty()) {
            this.equipSpiritWeapon();
        }
        this.setIsCharging(this.getTarget() != null || this.hasFirefightTask());
        LivingEntity target = this.getTarget();
        if (target != null && !this.isEnemyTarget(target)) {
            this.setTarget(null);
        }
        if (!this.isFirefightTaskValid()) {
            // Drop only the stale half of the task: while a creature is still
            // burning the spirit must keep flying at it after the floor flame
            // has been put out.
            if (this.firePos != null
                    && !this.level().getBlockState(this.firePos).is(BlockTags.FIRE)) {
                this.firePos = null;
            }
            if (this.burningTarget != null
                    && (!this.burningTarget.isAlive() || !this.burningTarget.isOnFire()
                            || this.burningTarget instanceof Enemy)) {
                this.burningTarget = null;
            }
        }
        if (this.tickCount % 8 == 0) {
            NpcFx.spiritTrail((ServerLevel) this.level(), this);
        }
        if (--this.lifeTicks <= 0) {
            this.dissolve();
        }
    }

    private boolean isEnemyTarget(LivingEntity target) {
        return target instanceof Enemy
                && !(target instanceof WaterSpiritEntity)
                && !(target instanceof WitherBoss)
                && !(target instanceof net.minecraft.world.entity.monster.Creeper)
                && target.isAlive();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity living) || !this.isEnemyTarget(living)) {
            return false;
        }
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            Vec3 point = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
            NpcFx.waterBurst(level, point);
            // Three spirits attacking every second used to fill the whole fight
            // with splashes; keep a soft accent on some hits only.
            if (this.getRandom().nextInt(3) == 0) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SPIRIT_ATTACK,
                        SoundSource.NEUTRAL, 0.32F, 1.0F + this.getRandom().nextFloat() * 0.15F);
            }
            this.swingForAttack(InteractionHand.MAIN_HAND);
        }
        return hit;
    }

    /**
     * Summoned water spirits are living air: nothing can hurt them, they carry no
     * health bar and they never die. Every damage entry point is sealed here so
     * no death packet, loot drop or death animation can ever reach a client.
     */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
        return true;
    }

    /**
     * Spirits have no health pool to lose: any attempt to write a lower value
     * (NBT merge, command, mod hook) is clamped back to full.
     */
    @Override
    public void setHealth(float health) {
        float max = this.getMaxHealth();
        super.setHealth(Float.isNaN(health) ? max : Math.max(health, max));
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float damage) {
        return false;
    }

    @Override
    public void kill(ServerLevel level) {
        // /kill, void damage and mod-forced kills are all ignored.
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        // A spirit has no death state; only its own lifetime dissolve() removes it.
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    private void dissolve() {
        if (this.level() instanceof ServerLevel serverLevel) {
            NpcFx.spiritDissolve(serverLevel, this);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SPIRIT_DEATH,
                    SoundSource.NEUTRAL, 0.4F, 1.15F);
        }
        this.discard();
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        // Summoned spirits are silent companions; three looping ambience tracks
        // at once were the main source of the constant noise.
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return ModSounds.SPIRIT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SPIRIT_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SpiritLife", this.lifeTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.lifeTicks = input.getIntOr("SpiritLife", LIFETIME_TICKS);
    }
}
