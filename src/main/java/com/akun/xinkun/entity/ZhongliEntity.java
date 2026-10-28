package com.akun.xinkun.entity;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.entity.ai.ZhongliCombatGoal;
import com.akun.xinkun.entity.ai.ZhongliGiftGoal;
import com.akun.xinkun.entity.ai.ZhongliLeystoneGoal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import com.akun.xinkun.particle.GlowDustOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * 钟离。往生堂客卿，岩王帝君在人间的化身。
 *
 * <p>战斗分三段：贴身时用下界合金长矛突刺并向前突进；距离拉开后收起长矛、
 * 双臂高举，在敌人前方降下一排贯地的岩枪；蓄势足够时双手抱胸召唤天星，
 * 让巨大的基岩陨石斜落砸出范围重击。只要他还处于戒备状态，就会为身边
 * 至多四名友方（自己、玩家和另外两名角色）挂上磐岩护盾。</p>
 *
 * <p>挖矿不靠镐子：他的双臂一张，附近地脉里的矿石便会整块浮起，
 * 沿一条缓慢的弧线飞进他的行囊。</p>
 */
public class ZhongliEntity extends GenshinNpcEntity {
    public static final int POSE_NONE = 0;
    /** 双臂举起、召唤岩枪。 */
    public static final int POSE_RAISE = 1;
    /** 双手抱在胸前、凝聚天星。 */
    public static final int POSE_CROSS = 2;
    /** 双臂张开、汲取地脉。 */
    public static final int POSE_ABSORB = 3;
    /** 挺矛突刺。 */
    public static final int POSE_THRUST = 4;

    /** 磐岩护盾：极高的伤害吸收，持续到钟离本人脱离战斗。 */
    public static final int SHIELD_ABSORPTION_AMPLIFIER = 9;
    public static final int SHIELD_RESISTANCE_AMPLIFIER = 2;
    /** 护盾最多覆盖四名友方。 */
    public static final int SHIELD_MAX_ALLIES = 4;
    public static final double SHIELD_RADIUS = 24.0;
    /** 被吸取的矿石方块带这个标记，便于重载后清理残留。 */
    public static final String LEY_BLOCK_TAG = "xinkun_ley_block";

    private static final EntityDataAccessor<Integer> DATA_ACTION_POSE =
            SynchedEntityData.defineId(ZhongliEntity.class, EntityDataSerializers.INT);

    /** 正在飞向钟离的矿石块；与它们对应的真实掉落。 */
    private record LeyFlight(FallingBlockEntity block, List<ItemStack> loot) {
    }

    private final List<LeyFlight> leyFlights = new ArrayList<>();
    /** 还没送到玩家手里的矿财；吸矿结束后会当面逐件抛出。 */
    private final List<ItemStack> pendingGifts = new ArrayList<>();
    private final List<UUID> shieldedAllies = new ArrayList<>();
    private int actionPoseTicks;
    private int shieldCheckCooldown;
    private boolean leySwept;

    public ZhongliEntity(EntityType<? extends ZhongliEntity> type, Level level) {
        super(type, level);
        var followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            // 岩枪的落点可以很远，他需要先看见远处的敌人。
            followRange.setBaseValue(48.0);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GenshinNpcEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 44.0)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.45);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION_POSE, POSE_NONE);
    }

    @Override
    public GenshinCharacter getCharacter() {
        return GenshinCharacter.ZHONGLI;
    }

    /** 帝君的武器是五星长柄「贯虹之槊」。 */
    @Override
    public boolean ensureWeapon() {
        return super.ensureWeapon();
    }

    @Override
    protected void addCharacterDefaults(List<ItemStack> kit) {
        // 专武由基类统一加入默认装备。
    }

    @Override
    protected void registerCharacterGoals() {
        // 三段战斗统一由岩元素战技接管，优先级高于共享近战。
        this.goalSelector.addGoal(2, new ZhongliCombatGoal(this));
        // 吸到的矿财优先送到玩家手里，送完才继续下一轮地脉。
        this.goalSelector.addGoal(3, new ZhongliGiftGoal(this));
        // 地脉吸取优先于普通砍伐，但低于战斗与拾取。
        this.goalSelector.addGoal(3, new ZhongliLeystoneGoal(this));
    }

    /** 原版挖矿行为被地脉吸取取代。 */
    @Override
    public boolean canMineOre() {
        return false;
    }

    public int getActionPose() {
        return this.entityData.get(DATA_ACTION_POSE);
    }

    /** 服务端设置动作；客户端只读取同步值摆姿势。 */
    public void setActionPose(int pose, int ticks) {
        if (this.level().isClientSide()) {
            return;
        }
        this.actionPoseTicks = Math.max(this.actionPoseTicks, ticks);
        this.entityData.set(DATA_ACTION_POSE, pose);
    }

    public void clearActionPose() {
        this.actionPoseTicks = 0;
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_ACTION_POSE, POSE_NONE);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.actionPoseTicks > 0 && --this.actionPoseTicks == 0) {
            this.entityData.set(DATA_ACTION_POSE, POSE_NONE);
        }
        if (!this.leySwept) {
            this.leySwept = true;
            this.sweepStaleLeyBlocks();
        }
        this.tickLeyFlights();
        if (this.shieldCheckCooldown > 0) {
            this.shieldCheckCooldown--;
        } else {
            this.shieldCheckCooldown = 20;
            this.updateShield();
        }
    }

    // ------------------------------------------------------------- 磐岩护盾

    /**
     * 戒备时给身边至多四名友方挂上护盾：自己、最近的一名玩家，
     * 以及距离最近的两名角色。离开戒备后立刻收回。
     */
    private void updateShield() {
        LivingEntity target = this.getTarget();
        boolean alert = target != null && target.isAlive();
        if (!alert) {
            this.releaseShield();
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        List<LivingEntity> allies = new ArrayList<>(SHIELD_MAX_ALLIES);
        allies.add(this);
        Player player = level.getNearestPlayer(this, SHIELD_RADIUS);
        if (player != null && player.isAlive()) {
            allies.add(player);
        }
        List<GenshinNpcEntity> companions = level.getEntitiesOfClass(GenshinNpcEntity.class,
                this.getBoundingBox().inflate(SHIELD_RADIUS),
                npc -> npc != this && npc.isAlive());
        companions.sort(Comparator.comparingDouble(npc -> npc.distanceToSqr(this)));
        for (GenshinNpcEntity companion : companions) {
            if (allies.size() >= SHIELD_MAX_ALLIES) {
                break;
            }
            allies.add(companion);
        }
        for (LivingEntity ally : allies) {
            boolean fresh = !ally.hasEffect(MobEffects.ABSORPTION);
            if (fresh || !ally.hasEffect(MobEffects.RESISTANCE)) {
                ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                        MobEffectInstance.INFINITE_DURATION, SHIELD_ABSORPTION_AMPLIFIER,
                        true, false, true));
                ally.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,
                        MobEffectInstance.INFINITE_DURATION, SHIELD_RESISTANCE_AMPLIFIER,
                        true, false, true));
            }
            if (!this.shieldedAllies.contains(ally.getUUID())) {
                this.shieldedAllies.add(ally.getUUID());
                this.shieldBurst(level, ally);
            }
        }
        // 护盾存在期间，友方脚下偶尔浮起一圈岩金色微光。
        if (this.tickCount % 40 == 0) {
            for (LivingEntity ally : allies) {
                level.sendParticles(new GlowDustOptions(0xE8C46A, 0.9F),
                        ally.getX(), ally.getY() + 0.15, ally.getZ(), 6, 0.45, 0.05, 0.45, 0.0);
            }
        }
    }

    private void shieldBurst(ServerLevel level, LivingEntity ally) {
        level.sendParticles(new GlowDustOptions(0xE8C46A, 1.4F),
                ally.getX(), ally.getY() + ally.getBbHeight() * 0.55, ally.getZ(),
                16, 0.4, 0.5, 0.4, 0.02);
        level.sendParticles(ParticleTypes.END_ROD,
                ally.getX(), ally.getY() + ally.getBbHeight() * 0.6, ally.getZ(),
                6, 0.3, 0.4, 0.3, 0.01);
        level.playSound(null, ally.getX(), ally.getY(), ally.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.45F, 0.7F);
    }

    private void releaseShield() {
        if (this.shieldedAllies.isEmpty()) {
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            for (UUID id : this.shieldedAllies) {
                Entity ally = level.getEntityInAnyDimension(id);
                if (ally == null) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                    if (player != null) {
                        ally = player;
                    }
                }
                if (ally instanceof LivingEntity living) {
                    living.removeEffect(MobEffects.ABSORPTION);
                    living.removeEffect(MobEffects.RESISTANCE);
                }
            }
        }
        this.shieldedAllies.clear();
    }

    // ------------------------------------------------------------ 地脉吸取

    /**
     * 把一块矿石从地脉里拔出来：原方块立刻消失，由一块快速飞行的
     * 方块代替，一路追赶钟离，抵达胸口时化作矿财——附近有人就直接
     * 塞进对方的背包。
     */
    public void beginAbsorb(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        List<ItemStack> loot = Block.getDrops(state, level, pos, null);
        level.removeBlock(pos, false);
        // fall() 内部已经把方块实体加入世界，这里绝不能再次 addFreshEntity，
        // 否则同一个 UUID 会被重复添加，服务端会刷屏警告。
        FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
        block.setNoGravity(true);
        block.setDeltaMovement(Vec3.ZERO);
        block.setHurtsEntities(0.0F, 0);
        block.disableDrop();
        block.addTag(LEY_BLOCK_TAG);
        this.leyFlights.add(new LeyFlight(block, loot));
        level.sendParticles(new GlowDustOptions(0xE8C46A, 1.1F),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8, 0.3, 0.3, 0.3, 0.02);
    }

    private void tickLeyFlights() {
        if (this.leyFlights.isEmpty()) {
            return;
        }
        Vec3 chest = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.62, this.getZ());
        Iterator<LeyFlight> iterator = this.leyFlights.iterator();
        while (iterator.hasNext()) {
            LeyFlight flight = iterator.next();
            FallingBlockEntity block = flight.block();
            if (!this.isAlive() || block.isRemoved() || block.level() != this.level()) {
                block.discard();
                iterator.remove();
                continue;
            }
            Vec3 from = block.position();
            Vec3 delta = chest.subtract(from);
            double distance = delta.length();
            if (distance <= 1.1) {
                block.discard();
                this.depositLoot(flight.loot());
                iterator.remove();
                continue;
            }
            // 追赶式回撤：距离越远飞得越快，钟离走到哪里矿石就跟到哪里。
            // 每 tick 位移都超过原版的位置同步阈值，客户端逐 tick 插值，
            // 视觉上是一条连续的滑轨，而不是 20 帧一次的方块跳格子。
            double speed = Mth.clamp(distance * 0.30, 0.6, 1.5);
            Vec3 step = delta.normalize().scale(Math.min(speed, distance));
            block.setPos(from.x + step.x, from.y + step.y, from.z + step.z);
            block.setDeltaMovement(step.x, step.y, step.z);
            if (this.level() instanceof ServerLevel level && this.tickCount % 2 == 0) {
                level.sendParticles(new GlowDustOptions(0xE8C46A, 0.8F),
                        from.x, from.y + 0.35, from.z, 1, 0.12, 0.12, 0.12, 0.0);
                level.sendParticles(ParticleTypes.END_ROD,
                        from.x, from.y + 0.5, from.z, 1, 0.08, 0.08, 0.08, 0.0);
            }
        }
    }

    /**
     * 帝君吸来的矿财先收进袖里，再由 {@link com.akun.xinkun.entity.ai.ZhongliGiftGoal}
     * 走到最近的玩家面前逐件抛出。附近没有人时也不会随手丢在地上，
     * 矿财会一直留到他找到人为止。
     */
    private void depositLoot(List<ItemStack> loot) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) {
                this.addPendingGift(stack.copy());
            }
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.35F, 0.8F);
        level.sendParticles(new GlowDustOptions(0xE8C46A, 1.2F),
                this.getX(), this.getY() + this.getBbHeight() * 0.6, this.getZ(),
                10, 0.35, 0.35, 0.35, 0.02);
    }

    /** 同种矿财并进已有的一摞，送给玩家时不会拆成一地碎堆。 */
    private void addPendingGift(ItemStack stack) {
        for (ItemStack existing : this.pendingGifts) {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }
            int room = Math.min(existing.getMaxStackSize(), stack.getMaxStackSize())
                    - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, stack.getCount());
            existing.grow(moved);
            stack.shrink(moved);
            if (stack.isEmpty()) {
                return;
            }
        }
        this.pendingGifts.add(stack);
    }

    public boolean hasPendingGifts() {
        return !this.pendingGifts.isEmpty();
    }

    /** 取走一摞矿财交给赠礼目标；由 AI 在玩家面前调用。 */
    public ItemStack takePendingGift() {
        if (this.pendingGifts.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return this.pendingGifts.removeFirst();
    }

    private void dropPendingGifts() {
        for (ItemStack stack : this.pendingGifts) {
            this.drop(stack.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        this.pendingGifts.clear();
    }

    private void sweepStaleLeyBlocks() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        for (FallingBlockEntity block : level.getEntitiesOfClass(FallingBlockEntity.class,
                this.getBoundingBox().inflate(64.0),
                b -> b.entityTags().contains(LEY_BLOCK_TAG))) {
            block.discard();
        }
    }

    // -------------------------------------------------------------- 生命周期

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        this.dropLeyLoot();
        this.dropPendingGifts();
        this.releaseShield();
        this.clearActionPose();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        for (LeyFlight flight : this.leyFlights) {
            flight.block().discard();
        }
        this.leyFlights.clear();
        super.remove(reason);
    }

    private void dropLeyLoot() {
        if (!this.leyFlights.isEmpty() && this.level() instanceof ServerLevel level) {
            for (LeyFlight flight : this.leyFlights) {
                flight.block().discard();
                for (ItemStack stack : flight.loot()) {
                    this.drop(stack.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
        }
        this.leyFlights.clear();
    }

    // ------------------------------------------------------------- 存档数据

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        List<String> ids = new ArrayList<>(this.shieldedAllies.size());
        for (UUID id : this.shieldedAllies) {
            ids.add(id.toString());
        }
        output.putString("ShieldedAllies", String.join(",", ids));
        ValueOutput.TypedOutputList<ItemStack> gifts = output.list("PendingGifts", ItemStack.CODEC);
        for (ItemStack stack : this.pendingGifts) {
            if (!stack.isEmpty()) {
                gifts.add(stack);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.shieldedAllies.clear();
        String raw = input.getStringOr("ShieldedAllies", "");
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                this.shieldedAllies.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                // 损坏的记录跳过。
            }
        }
        this.pendingGifts.clear();
        for (ItemStack stack : input.listOrEmpty("PendingGifts", ItemStack.CODEC)) {
            if (!stack.isEmpty()) {
                this.addPendingGift(stack.copy());
            }
        }
    }
}
