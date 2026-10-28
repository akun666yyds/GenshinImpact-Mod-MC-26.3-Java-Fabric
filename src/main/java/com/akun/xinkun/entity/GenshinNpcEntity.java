package com.akun.xinkun.entity;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.entity.ai.NpcCombatGoal;
import com.akun.xinkun.entity.ai.NpcEatGoal;
import com.akun.xinkun.entity.ai.NpcInteractBlockGoal;
import com.akun.xinkun.entity.ai.NpcPickupGoal;
import com.akun.xinkun.entity.ai.NpcScheduleGoal;
import com.akun.xinkun.entity.ai.NpcWorkGoal;
import com.akun.xinkun.registry.ModItems;
import com.akun.xinkun.voice.ModVoices;
import com.akun.xinkun.voice.VoiceContext;
import com.akun.xinkun.voice.VoiceLine;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.util.Prediction;
import net.minecraft.util.Unit;

public abstract class GenshinNpcEntity extends PathfinderMob {
    private static final int BACKPACK_SIZE = 36;
    /** 与 {@code ModSounds.registerVoice} 的固定传播距离保持一致。 */
    private static final double VOICE_RANGE = 64.0;
    /** 客户端骨架模型据此判断当前是否正在说话。 */
    private static final EntityDataAccessor<Boolean> DATA_SPEAKING =
            SynchedEntityData.defineId(GenshinNpcEntity.class, EntityDataSerializers.BOOLEAN);

    private enum AmbientWeather {
        CLEAR(VoiceContext.WEATHER_SUN),
        RAIN(VoiceContext.WEATHER_RAIN),
        SNOW(VoiceContext.WEATHER_SNOW),
        THUNDER(VoiceContext.WEATHER_THUNDER);

        private final VoiceContext voice;

        AmbientWeather(VoiceContext voice) {
            this.voice = voice;
        }
    }

    private final NonNullList<ItemStack> backpack = NonNullList.withSize(BACKPACK_SIZE, ItemStack.EMPTY);
    /** 角色自带的全部装备与树苗；这些永远不会进背包，也不会死亡掉落。 */
    private List<ItemStack> defaultKit;
    private Set<Item> defaultItems;
    private BlockPos npcSpawnPoint = BlockPos.ZERO;
    private BlockPos homePoint = BlockPos.ZERO;
    private boolean pointsInitialized;
    private boolean respawnScheduled;
    private int speechTicks;
    private ArmorStand speechBubble;
    private SoundEvent activeVoiceSound;
    private boolean speechBubbleSwept;
    private int greetCooldown;
    private int chatCooldown = 800;
    private int attackVoiceCooldown;
    private int hurtVoiceCooldown;
    private int specialVoiceCooldown;
    private int condolenceCooldown;
    /** 工作结束后延迟收回镐/斧，等最后一下挥击放完。 */
    private int workStowDelay = -1;
    private AmbientWeather lastWeather;
    private boolean weatherInitialized;
    private boolean bossArmorWorn;
    private int armorCheckCooldown;
    /** Consecutive ticks spent in lava or inside a solid block, for rescue. */
    private int lavaTicks;
    private int wallTicks;
    /** 被非玩家、非角色生物攻击后锁定的追击目标。 */
    private UUID retaliationTargetId;

    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    protected GenshinNpcEntity(EntityType<? extends GenshinNpcEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.getNavigation().setCanOpenDoors(true);
        this.getNavigation().setCanFloat(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPEAKING, false);
    }

    /** 客户端同步的“正在说话”状态，用于驱动骨骼模型的点头动作。 */
    public boolean isTalking() {
        return this.entityData.get(DATA_SPEAKING);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.3)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 0.6);
    }

    public abstract GenshinCharacter getCharacter();

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        // 战斗高于采集：被生物袭击时立刻放下手里的活反击，打完再续上。
        this.goalSelector.addGoal(3, new NpcCombatGoal(this, 1.2));
        this.goalSelector.addGoal(3, new NpcPickupGoal(this));
        this.goalSelector.addGoal(4, new NpcWorkGoal(this));
        this.goalSelector.addGoal(5, new NpcInteractBlockGoal(this));
        // Wandering is the lowest-priority daytime activity: it must never
        // interrupt a character who is already felling a tree or mining a vein.
        this.goalSelector.addGoal(6, new NpcScheduleGoal(this));
        this.goalSelector.addGoal(8, new NpcEatGoal(this));
        this.registerCharacterGoals();
        this.goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        this.registerCharacterTargets();
        // Enemy covers every hostile mob, including the ones that do not
        // extend Monster (slime, magma cube, ghast, phantom, shulker).
        // Creepers are the only exception: they are never hunted, only
        // fought back when one attacks.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                (target, level) -> target instanceof Enemy
                        && !(target instanceof Creeper)
                        && !(target instanceof WaterSpiritEntity)
                        && target.isAlive()
                        && this.canFightNow()));
    }

    public static boolean isBoss(LivingEntity entity) {
        return entity instanceof WitherBoss
                || entity instanceof Warden
                || entity instanceof EnderDragon
                || entity instanceof ElderGuardian
                || entity instanceof Ravager
                || entity.getMaxHealth() >= 40.0F;
    }

    protected void registerCharacterGoals() {
    }

    protected void registerCharacterTargets() {
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnGroupData);
        // The spawn pipeline loads the incoming entity tag after the
        // constructor has run, which clears the persistence flag the
        // constructor set. Re-assert it for every spawn reason.
        this.setPersistenceRequired();
        if (!this.pointsInitialized) {
            this.npcSpawnPoint = this.blockPosition();
            this.homePoint = this.npcSpawnPoint;
            this.pointsInitialized = true;
        }
        this.equipStartingKit();
        this.resetDisplayName();
        this.setCustomNameVisible(true);
        this.chatCooldown = 1200 + this.getRandom().nextInt(1200);
        return data;
    }

    /**
     * 角色的默认装备是“身体的一部分”，不是背包物品：全部不毁、数量无限，
     * 死亡时不会掉落，也不会占掉任何拾取格。
     */
    private void equipStartingKit() {
        this.ensureDefaultKit();
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            this.setItemSlot(slot, ItemStack.EMPTY);
        }
        this.bossArmorWorn = false;
    }

    private void ensureDefaultKit() {
        if (this.defaultKit != null) {
            return;
        }
        List<ItemStack> kit = new ArrayList<>();
        // 下界合金全家桶：盔甲、剑、镐、斧、锹、锄。
        addDefault(kit, Items.NETHERITE_HELMET);
        addDefault(kit, Items.NETHERITE_CHESTPLATE);
        addDefault(kit, Items.NETHERITE_LEGGINGS);
        addDefault(kit, Items.NETHERITE_BOOTS);
        addDefault(kit, Items.NETHERITE_SWORD);
        // 原版长枪：胡桃与钟离的突刺动作与它配套，武器朝向由原版弹道模板负责。
        addDefault(kit, Items.NETHERITE_SPEAR);
        addDefault(kit, Items.NETHERITE_PICKAXE);
        addDefault(kit, Items.NETHERITE_AXE);
        addDefault(kit, Items.NETHERITE_SHOVEL);
        addDefault(kit, Items.NETHERITE_HOE);
        // 各种树苗，保证伐木后永远可以补种。
        addDefault(kit, Items.OAK_SAPLING);
        addDefault(kit, Items.SPRUCE_SAPLING);
        addDefault(kit, Items.BIRCH_SAPLING);
        addDefault(kit, Items.JUNGLE_SAPLING);
        addDefault(kit, Items.ACACIA_SAPLING);
        addDefault(kit, Items.DARK_OAK_SAPLING);
        addDefault(kit, Items.CHERRY_SAPLING);
        addDefault(kit, Items.PALE_OAK_SAPLING);
        addDefault(kit, Items.MANGROVE_PROPAGULE);
        this.addCharacterDefaults(kit);
        this.defaultKit = List.copyOf(kit);
        Set<Item> items = new LinkedHashSet<>();
        for (ItemStack stack : kit) {
            items.add(stack.getItem());
        }
        this.defaultItems = Set.copyOf(items);
    }

    protected static void addDefault(List<ItemStack> kit, Item item) {
        kit.add(defaultStack(item));
    }

    private static ItemStack defaultStack(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }

    /** 角色专属的默认武器（例如宵宫的长弓与箭）由子类补充。 */
    protected void addCharacterDefaults(List<ItemStack> kit) {
    }

    public boolean isDefaultItem(Item item) {
        this.ensureDefaultKit();
        return this.defaultItems.contains(item);
    }

    private ItemStack defaultStackOf(Item item) {
        for (ItemStack stack : this.defaultKit) {
            if (stack.is(item)) {
                return stack.copy();
            }
        }
        return defaultStack(item);
    }

    /** Return a picked-up tool to the backpack; default gear simply vanishes. */
    public void stowTool() {
        ItemStack held = this.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        if (!held.is(ItemTags.PICKAXES) && !held.is(ItemTags.AXES)
                && !held.is(ItemTags.SWORDS) && !held.is(ItemTags.SHOVELS)
                && !held.is(ItemTags.HOES) && !(held.getItem() instanceof net.minecraft.world.item.BowItem)) {
            return;
        }
        boolean isDefault = this.isDefaultItem(held.getItem());
        ItemStack copy = held.copy();
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (isDefault) {
            return;
        }
        ItemStack remainder = this.storeInBackpack(copy);
        if (!remainder.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, remainder);
        }
    }

    /**
     * 工作结束时镐和斧还要留在手上把最后一下挥完。原版挥击有固定时长，
     * 工具若在同一 tick 被收回，客户端会立刻看到空手，挖矿与伐木的专属姿势
     * 也就退化成了普通攻击。这里等挥击结束再收。
     */
    public void stowWorkToolSoon() {
        // 12 tick 是最长挥击（STAB 10）的上限；挥击真正结束时提前收。
        this.workStowDelay = 12;
    }

    /**
     * 角色战斗时手持的原版武器。
     * 宵宫用弓，纳西妲用翠叶书卷，哥伦比娅用附魔书，
     * 胡桃与钟离用原版下界合金矛（与突刺动作配套），其余角色用下界合金剑。
     */
    public Item combatWeapon() {
        return switch (this.getCharacter()) {
            case YOIMIYA -> Items.BOW;
            case NAHIDA -> ModItems.VERDANT_CODEX;
            case COLUMBINA -> Items.ENCHANTED_BOOK;
            case HU_TAO, ZHONGLI -> Items.NETHERITE_SPEAR;
            default -> Items.NETHERITE_SWORD;
        };
    }

    /** 抽出战斗武器；工具由调用方的目标按顺序先收好。 */
    public boolean ensureWeapon() {
        return this.ensureMainHand(this.combatWeapon());
    }

    /**
     * 用角色真实的攻击方式选择原版挥击模板，而不是所有人共用同一套劈砍：
     * 长柄武器（胡桃、钟离）用原版突刺 STAB，单手剑（芙宁娜）用劈砍 WHACK，
     * 法器（纳西妲、哥伦比娅、桑多涅）交给骨架模型做施法手势，原版只保留躯干摆动。
     * 镐与斧同样沿用原版劈砍，但时长与作业节拍对齐，连续挖掘时不会半途重置。
     */
    @Override
    public void swingForAttack(InteractionHand hand) {
        this.swing(hand, this.attackSwing(hand), false);
    }

    private SwingAnimation attackSwing(InteractionHand hand) {
        ItemStack held = this.getItemInHand(hand);
        if (held.is(ItemTags.PICKAXES)) {
            return new SwingAnimation(SwingAnimationType.WHACK, 7);
        }
        if (held.is(ItemTags.AXES)) {
            return new SwingAnimation(SwingAnimationType.WHACK, 8);
        }
        return switch (this.getCharacter()) {
            case HU_TAO, ZHONGLI -> new SwingAnimation(SwingAnimationType.STAB, 10);
            case NAHIDA, COLUMBINA, SANDRONE -> new SwingAnimation(SwingAnimationType.NONE, 10);
            default -> new SwingAnimation(SwingAnimationType.WHACK, 6);
        };
    }

    /**
     * 是否允许主动战斗。默认昼夜不限；哥伦比娅白天完全不还手，由子类覆盖。
     */
    public boolean canFightNow() {
        return true;
    }

    /**
     * 是否使用常规的逐块挖矿。钟离改用“地脉吸取”整片取走矿石，
     * 因此关闭共享的挖矿工作，避免同一块矿石被两套行为争抢。
     */
    public boolean canMineOre() {
        return true;
    }

    /**
     * 是否参与共享的伐木工作。纳西妲只会种树，从不砍树，由子类关闭。
     */
    public boolean canChopTrees() {
        return true;
    }

    /**
     * 是否必须待在出生点附近。需要远行巡游的角色（例如纳西妲去荒地造林）
     * 可以关闭常规召回；掉出世界、卡墙和落进岩浆的紧急救援始终保留。
     */
    public boolean shouldStayNearHome() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (!this.pointsInitialized) {
            this.npcSpawnPoint = this.blockPosition();
            this.homePoint = this.npcSpawnPoint;
            this.pointsInitialized = true;
            this.equipStartingKit();
            this.resetDisplayName();
        }
        if (!this.speechBubbleSwept) {
            // Bubbles do not survive a reload as live state. Any leftover stand
            // carrying this character's tag is a stale line from the last session.
            this.speechBubbleSwept = true;
            this.sweepStaleSpeechBubbles();
        }
        if (this.speechTicks > 0 && --this.speechTicks == 0) {
            this.clearSpeechBubble();
        } else if (this.speechBubble != null && !this.speechBubble.isRemoved() && this.tickCount % 4 == 0) {
            this.positionSpeechBubble();
        }
        boolean speakingNow = this.speechTicks > 0;
        if (this.entityData.get(DATA_SPEAKING) != speakingNow) {
            this.entityData.set(DATA_SPEAKING, speakingNow);
        }
        if (this.workStowDelay > 0) {
            this.workStowDelay--;
            // 至少留两 tick 给挥击起步；之后挥击一结束就收，最多等满 12 tick。
            if (this.workStowDelay == 0 || (this.workStowDelay <= 10 && !this.isSwinging())) {
                this.workStowDelay = 0;
                ItemStack held = this.getMainHandItem();
                // 期间若已换上战斗武器，说明被战斗打断，工具早已进背包，这里不要动它。
                if (held.is(ItemTags.AXES) || held.is(ItemTags.PICKAXES)) {
                    this.stowTool();
                }
            }
        }
        this.greetCooldown = Math.max(0, this.greetCooldown - 1);
        this.attackVoiceCooldown = Math.max(0, this.attackVoiceCooldown - 1);
        this.hurtVoiceCooldown = Math.max(0, this.hurtVoiceCooldown - 1);
        this.specialVoiceCooldown = Math.max(0, this.specialVoiceCooldown - 1);
        this.condolenceCooldown = Math.max(0, this.condolenceCooldown - 1);
        // 问候不能交给 Goal 去抢：角色白天几乎一直在干活，
        // 工作目标占着注视与移动两个旗标，问候目标永远轮不到执行，
        // 玩家走近时自然触发的语音就会整段缺失。这里直接按冷却判定。
        if (this.greetCooldown == 0 && this.speechTicks == 0 && this.getTarget() == null
                && this.tickCount % 20 == 0) {
            this.greetNearbyPlayer();
        }
        if (this.chatCooldown > 0) {
            this.chatCooldown--;
        } else if (this.isAlive() && this.getTarget() == null && this.speechTicks == 0) {
            this.speak(VoiceContext.CHAT);
            this.chatCooldown = 1600 + this.getRandom().nextInt(2800);
        }
        if (this.tickCount % 20 == 0) {
            this.validateRetaliationTarget();
            // A target that died or vanished beyond all hope must not keep the
            // revenge flag (and the "stay away from home" exemption) alive.
            LivingEntity target = this.getTarget();
            if (target != null && (!target.isAlive() || this.distanceToSqr(target) > 160.0 * 160.0)) {
                this.setTarget(null);
            }
        }
        // 自然状态下每秒恢复最大生命值的 5%
        if (this.getTarget() == null && this.getHealth() < this.getMaxHealth() && this.tickCount % 20 == 0) {
            this.heal(this.getMaxHealth() * 0.05F);
        }
        if (this.tickCount % 20 == 0) {
            this.updateWeatherVoice();
            this.keepNearHome();
        }
        // 离开水面后恢复站立姿势，避免一直横着身子行走。
        if (this.hasPose(Pose.SWIMMING) && !this.isInWater()) {
            this.stopSwimmingPose();
        }
        if (this.armorCheckCooldown > 0) {
            this.armorCheckCooldown--;
        } else {
            this.armorCheckCooldown = 10;
            this.updateBossArmor();
        }
    }

    /**
     * 强敌当前时自动穿上整套下界合金盔甲，脱离战斗后立刻卸下。
     * 盔甲来自角色的默认装备，不占背包、不掉落、不需要修理。
     */
    private void updateBossArmor() {
        LivingEntity target = this.getTarget();
        boolean bossFight = target != null && target.isAlive() && isBoss(target);
        if (bossFight) {
            if (!this.bossArmorWorn) {
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    this.setItemSlot(slot, defaultStackOf(armorItem(slot)));
                }
                this.bossArmorWorn = true;
            }
            return;
        }
        if (!this.bossArmorWorn) {
            return;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            this.setItemSlot(slot, ItemStack.EMPTY);
        }
        this.bossArmorWorn = false;
    }

    private static Item armorItem(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Items.NETHERITE_HELMET;
            case CHEST -> Items.NETHERITE_CHESTPLATE;
            case LEGS -> Items.NETHERITE_LEGGINGS;
            default -> Items.NETHERITE_BOOTS;
        };
    }

    private void updateWeatherVoice() {
        AmbientWeather current = this.detectWeather();
        if (!this.weatherInitialized) {
            this.weatherInitialized = true;
            this.lastWeather = current;
            if (current != AmbientWeather.CLEAR) {
                this.speakWeather(current);
            }
            return;
        }
        if (current != this.lastWeather) {
            this.lastWeather = current;
            this.speakWeather(current);
        }
    }

    private void speakWeather(AmbientWeather weather) {
        this.speak(weather.voice);
        this.chatCooldown = Math.max(this.chatCooldown, 600);
    }

    private AmbientWeather detectWeather() {
        if (!this.level().isRaining()) {
            return AmbientWeather.CLEAR;
        }
        if (this.level().isThundering()) {
            return AmbientWeather.THUNDER;
        }
        BlockPos pos = this.blockPosition();
        if (this.level().getBiome(pos).value().coldEnoughToSnow(pos, this.level().getSeaLevel())) {
            return AmbientWeather.SNOW;
        }
        return AmbientWeather.RAIN;
    }

    public void speak(VoiceContext context) {
        if (this.level().isClientSide()) {
            return;
        }
        VoiceLine line = ModVoices.pick(this.getCharacter(), context, this.getRandom());
        if (line == null && context == VoiceContext.GREETING_NIGHT) {
            // 没有夜间台词的角色用普通问候，避免夜里突然沉默。
            line = ModVoices.pick(this.getCharacter(), VoiceContext.GREETING, this.getRandom());
        }
        if (line == null && (context == VoiceContext.WEATHER_SUN || context == VoiceContext.WEATHER_RAIN
                || context == VoiceContext.WEATHER_SNOW || context == VoiceContext.WEATHER_THUNDER)) {
            // 个别角色没有录过某种天气的台词；天气变化时改用日常闲聊，
            // 不能因为缺一条录音就让整次自然触发的语音静音。
            line = ModVoices.pick(this.getCharacter(), VoiceContext.CHAT, this.getRandom());
        }
        if (line == null) {
            return;
        }
        // A later line always cuts off the previous one instead of stacking.
        this.stopActiveVoice();
        this.playVoice(line.sound());
        this.activeVoiceSound = line.sound();
        if (line.text() != null) {
            this.showSpeech(line.text(), line.bubbleTicks());
        } else {
            // 没有字幕的官方语音同样要让角色开口：保留一段说话时间与轻点头动作。
            this.speechTicks = Math.max(40, line.bubbleTicks());
        }
    }

    /**
     * 自然开口时角色常常离玩家二十格以上。
     * 原版的 {@code Level.playSound} 只把声音包发给十六格内的玩家，
     * 无论音效自身的传播距离写多大都一样，于是玩家只能看见字幕、听不到声音。
     * 这里按语音的固定距离（六十四格）直接投递声音包。
     */
    private void playVoice(SoundEvent sound) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(holder, SoundSource.VOICE,
                this.getX(), this.getY(), this.getZ(), 1.0F, 1.0F, this.getRandom().nextLong());
        for (ServerPlayer player : serverLevel.getPlayers(
                player -> player.distanceToSqr(this) < VOICE_RANGE * VOICE_RANGE)) {
            player.connection.send(packet);
        }
    }

    private void stopActiveVoice() {
        SoundEvent previous = this.activeVoiceSound;
        this.activeVoiceSound = null;
        if (previous == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(previous.location(), SoundSource.VOICE);
        for (ServerPlayer player : serverLevel.getPlayers(player -> player.distanceToSqr(this) < VOICE_RANGE * VOICE_RANGE)) {
            player.connection.send(stop);
        }
    }

    /**
     * The line floats above the character as its own marker entity. The NPC's
     * custom name is never touched, so the entity keeps showing the character
     * name while she talks.
     */
    public void showSpeech(String text, int ticks) {
        if (text == null || text.isEmpty()) {
            return;
        }
        this.speechTicks = Math.max(20, ticks);
        this.setCustomName(this.getCharacter().displayName());
        this.setCustomNameVisible(true);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.speechBubble == null || this.speechBubble.isRemoved()
                || this.speechBubble.level() != serverLevel) {
            ArmorStand bubble = EntityTypes.ARMOR_STAND.create(serverLevel, EntitySpawnReason.EVENT);
            if (bubble == null) {
                return;
            }
            bubble.setInvisible(true);
            bubble.setNoGravity(true);
            bubble.setSilent(true);
            bubble.setPermanentlyInvulnerable(true);
            bubble.setCustomNameVisible(true);
            bubble.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) ArmorStand.CLIENT_FLAG_MARKER);
            bubble.addTag(this.speechBubbleTag());
            this.speechBubble = bubble;
            this.positionSpeechBubble();
            serverLevel.addFreshEntity(bubble);
        }
        this.speechBubble.setCustomName(Component.literal("「" + text + "」"));
        this.speechBubble.setCustomNameVisible(true);
        this.positionSpeechBubble();
    }

    private void positionSpeechBubble() {
        if (this.speechBubble == null) {
            return;
        }
        double x = this.getX();
        double y = this.getY() + this.getBbHeight() + 0.9;
        double z = this.getZ();
        if (this.speechBubble.distanceToSqr(x, y, z) > 0.01) {
            this.speechBubble.setPos(x, y, z);
        }
    }

    private String speechBubbleTag() {
        return "xinkun_speech_" + this.getUUID().toString().replace("-", "");
    }

    private void sweepStaleSpeechBubbles() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ArmorStand stand : serverLevel.getEntitiesOfClass(ArmorStand.class,
                this.getBoundingBox().inflate(24.0), stand -> stand.entityTags().contains(this.speechBubbleTag()))) {
            stand.discard();
        }
    }

    private void clearSpeechBubble() {
        if (this.speechBubble != null) {
            this.speechBubble.discard();
            this.speechBubble = null;
        }
    }

    public void resetDisplayName() {
        this.setCustomName(this.getCharacter().displayName());
        this.setCustomNameVisible(true);
    }

    public boolean isGreetReady() {
        return this.greetCooldown <= 0;
    }

    /** 附近有玩家且能看见时，主动转头问候一句。 */
    public void greetNearbyPlayer() {
        Player player = this.level().getNearestPlayer(this, 8.0);
        if (player == null || player.isSpectator()) {
            return;
        }
        if (!this.getSensing().hasLineOfSight(player)) {
            return;
        }
        this.getLookControl().setLookAt(player, 30.0F, 30.0F);
        this.speak(this.greetingContext());
        this.markGreeted(900 + this.getRandom().nextInt(600));
    }

    public boolean isSpeaking() {
        return this.speechTicks > 0;
    }

    public void markGreeted(int ticks) {
        this.greetCooldown = ticks;
    }

    public boolean isSpecialVoiceReady() {
        return this.specialVoiceCooldown <= 0;
    }

    public void markSpecialVoice(int ticks) {
        this.specialVoiceCooldown = ticks;
    }

    public boolean isNightTime() {
        long time = this.level().getDefaultClockTime() % 24000L;
        return time >= 13000L && time < 23000L;
    }

    public boolean isDayTime() {
        long time = this.level().getDefaultClockTime() % 24000L;
        return time >= 0L && time < 13000L;
    }

    public BlockPos getNpcSpawnPoint() {
        return this.pointsInitialized ? this.npcSpawnPoint : this.blockPosition();
    }

    public void setNpcSpawnPoint(BlockPos pos) {
        this.npcSpawnPoint = pos.immutable();
        this.pointsInitialized = true;
    }

    public BlockPos getHomePoint() {
        return this.pointsInitialized ? this.homePoint : this.blockPosition();
    }

    public void setHomePoint(BlockPos pos) {
        this.homePoint = pos.immutable();
        this.pointsInitialized = true;
    }

    public boolean isRespawnScheduled() {
        return this.respawnScheduled;
    }

    public void markRespawnScheduled() {
        this.respawnScheduled = true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * 角色属于“永远自定义持久化”的实体：即使某个生成流程漏写了
     * PersistenceRequired 标签，原版的刷怪清理也不会挑中她们。
     */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /**
     * Vanilla's despawn pass must never touch a character. Persistence is
     * re-asserted on load, but this also covers spawn paths that write entity
     * data after construction and any future vanilla change to the rule.
     */
    @Override
    public void checkDespawn() {
    }

    /**
     * Keeps an idle character anchored to the place the player chose as home.
     * Walking back is preferred by the schedule goal; this is the hard safety
     * net for unreachable terrain, being shoved into lava, falling out of the
     * world, or any other way a companion could be stranded far from home.
     */
    private void keepNearHome() {
        if (!this.pointsInitialized || this.isPassenger()) {
            return;
        }
        BlockPos home = this.homePoint;
        double distanceSqr = this.blockPosition().distSqr(home);
        // Chasing a mob or finishing a tree/vein are legitimate reasons to be
        // far from home or deep underground; only recall once she is idle again.
        boolean busy = this.getTarget() != null || this.isWorkGoalActive();
        boolean anchored = this.shouldStayNearHome();
        boolean abandoned = anchored && distanceSqr > 64.0 * 64.0 && !busy;
        boolean tooFar = anchored && distanceSqr > 128.0 * 128.0 && !busy;
        boolean belowWorld = this.getY() < this.level().getMinY() - 24.0;
        boolean belowHome = anchored && this.getY() < home.getY() - 32.0 && !busy;
        this.lavaTicks = this.isInLava() ? this.lavaTicks + 20 : 0;
        this.wallTicks = this.isInWall() ? this.wallTicks + 20 : 0;
        // One second in lava is already fatal for a 20 HP character, so the
        // rescue has to fire on the first check rather than after a delay.
        if (abandoned || tooFar || belowWorld || belowHome
                || this.lavaTicks >= 20 || this.wallTicks >= 100) {
            this.recallHome();
        }
    }

    private boolean isWorkGoalActive() {
        return this.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof NpcWorkGoal);
    }

    /** Returns the character to a verified standing spot at its home. */
    public void recallHome() {
        if (!(this.level() instanceof ServerLevel level) || !this.pointsInitialized) {
            return;
        }
        BlockPos safe = this.findHomeStandingSpot(level);
        this.getNavigation().stop();
        this.clearFire();
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
        this.lavaTicks = 0;
        this.wallTicks = 0;
    }

    private BlockPos findHomeStandingSpot(ServerLevel level) {
        BlockPos home = this.homePoint;
        int[] vertical = {0, 1, -1, 2, -2, 3, -3, -4, -5, -6, -7, -8};
        for (int dy : vertical) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos feet = home.offset(dx, dy, dz);
                    if (!level.isLoaded(feet)) {
                        continue;
                    }
                    if (this.hasStandingRoom(level, feet)) {
                        return feet;
                    }
                }
            }
        }
        return home;
    }

    private boolean hasStandingRoom(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.getFluidState().isEmpty()
                || !ground.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        BlockState at = level.getBlockState(feet);
        BlockState above = level.getBlockState(feet.above());
        return at.getFluidState().isEmpty() && above.getFluidState().isEmpty()
                && at.getCollisionShape(level, feet).isEmpty()
                && above.getCollisionShape(level, feet.above()).isEmpty();
    }

    /**
     * 玩家式的游泳：身体转为水平游泳姿态，并按三维方向直接给速度。
     * 原版的 HumanoidModel 会自动渲染这个姿势，不需要自定义动画。
     */
    public void swimToward(double x, double y, double z, double speed) {
        if (!this.isInWater()) {
            return;
        }
        this.setSwimming(true);
        this.setPose(Pose.SWIMMING);
        Vec3 delta = new Vec3(x - this.getX(), y - (this.getY() + 0.4), z - this.getZ());
        if (delta.lengthSqr() > 0.0001) {
            Vec3 unit = delta.normalize().scale(speed);
            this.setDeltaMovement(unit.x, unit.y, unit.z);
            this.getNavigation().stop();
        }
        this.fallDistance = 0.0F;
    }

    /** 出水后恢复站立/行走姿态。 */
    public void stopSwimmingPose() {
        if (this.isInWater()) {
            return;
        }
        this.setSwimming(false);
        if (this.hasPose(Pose.SWIMMING)) {
            this.setPose(Pose.STANDING);
        }
    }

    /**
     * Pose 只在服务端设置、不会同步到客户端；游泳标记（实体共享标志）会同步。
     * 客户端据此判定为“正在游泳”，才能让原版人形模型播放水平游泳姿态。
     */
    @Override
    public boolean isVisuallySwimming() {
        return this.isSwimming() || super.isVisuallySwimming();
    }

    public List<ItemStack> getBackpack() {
        return this.backpack;
    }

    public boolean hasBackpackSpace() {
        for (ItemStack stack : this.backpack) {
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** True when the stack fits, either in an empty slot or by merging. */
    public boolean canAccept(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemStack existing : this.backpack) {
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public ItemStack storeInBackpack(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        for (int i = 0; i < BACKPACK_SIZE && !stack.isEmpty(); i++) {
            ItemStack existing = this.backpack.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int max = Math.min(existing.getMaxStackSize(), stack.getMaxStackSize());
                int move = Math.min(max - existing.getCount(), stack.getCount());
                if (move > 0) {
                    existing.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < BACKPACK_SIZE && !stack.isEmpty(); i++) {
            if (this.backpack.get(i).isEmpty()) {
                int move = Math.min(stack.getMaxStackSize(), stack.getCount());
                ItemStack copy = stack.copy();
                copy.setCount(move);
                this.backpack.set(i, copy);
                stack.shrink(move);
            }
        }
        return stack;
    }

    public boolean addToBackpack(ItemStack stack) {
        return this.storeInBackpack(stack.copy()).isEmpty();
    }

    public int countItem(Item item) {
        // 默认装备数量无限，永不耗尽。
        if (this.isDefaultItem(item)) {
            return Integer.MAX_VALUE;
        }
        int count = 0;
        for (ItemStack stack : this.backpack) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean consumeItem(Item item, int count) {
        if (count <= 0) {
            return true;
        }
        // 默认装备与树苗无限取用，不写进背包，也不参与死亡掉落。
        if (this.isDefaultItem(item)) {
            return true;
        }
        if (this.countItem(item) < count) {
            return false;
        }
        int remaining = count;
        for (int i = 0; i < BACKPACK_SIZE && remaining > 0; i++) {
            ItemStack stack = this.backpack.get(i);
            if (stack.is(item)) {
                int taken = Math.min(stack.getCount(), remaining);
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        return true;
    }

    public boolean ensureMainHand(Item item) {
        if (this.getMainHandItem().is(item)) {
            return true;
        }
        if (this.isDefaultItem(item)) {
            ItemStack previous = this.getMainHandItem();
            if (!previous.isEmpty() && !this.isDefaultItem(previous.getItem())) {
                ItemStack remainder = this.storeInBackpack(previous.copy());
                if (!remainder.isEmpty()) {
                    return false;
                }
            }
            this.setItemSlot(EquipmentSlot.MAINHAND, this.defaultStackOf(item));
            return true;
        }
        int slot = -1;
        for (int i = 0; i < BACKPACK_SIZE; i++) {
            if (this.backpack.get(i).is(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return false;
        }
        ItemStack tool = this.backpack.get(slot);
        ItemStack previous = this.getMainHandItem();
        this.backpack.set(slot, previous.isEmpty() ? ItemStack.EMPTY : previous);
        this.setItemSlot(EquipmentSlot.MAINHAND, tool);
        return true;
    }

    public void pickUpItem(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remainder = this.storeInBackpack(stack);
        if (remainder.isEmpty()) {
            itemEntity.discard();
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F + this.getRandom().nextFloat() * 0.2F);
        } else {
            itemEntity.setItem(remainder);
        }
    }

    public void reactToDyingPlayer(ServerPlayer player) {
        if (this.condolenceCooldown > 0) {
            return;
        }
        this.condolenceCooldown = 2400;
        this.getLookControl().setLookAt(player, 30.0F, 30.0F);
        this.getNavigation().moveTo(player, 1.25);
        this.speak(VoiceContext.AD);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive()) {
            this.registerRetaliation(source);
            if (this.hurtVoiceCooldown <= 0) {
                this.speak(VoiceContext.HURT);
                this.hurtVoiceCooldown = 140;
            }
        }
        return hurt;
    }

    /**
     * 除玩家和其他原神角色以外的任何生物只要伤到她，就会被锁定并追杀到底。
     * 苦力怕也在内：先拉开距离，再回头补刀，循环到它死亡为止。
     */
    private void registerRetaliation(DamageSource source) {
        if (!this.canFightNow()) {
            return;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity living) || living == this) {
            return;
        }
        if (!this.isValidRetaliationTarget(living)) {
            return;
        }
        this.retaliationTargetId = living.getUUID();
        this.setTarget(living);
    }

    private boolean isValidRetaliationTarget(LivingEntity living) {
        return living.isAlive()
                && !(living instanceof Player)
                && !(living instanceof GenshinNpcEntity)
                && !(living instanceof WaterSpiritEntity);
    }

    /**
     * 放弃当前仇敌：目标死亡、或已被证明追不上（距离过远）时调用。
     */
    public void forgetRetaliationTarget() {
        this.retaliationTargetId = null;
        this.setTarget(null);
    }

    private void validateRetaliationTarget() {
        if (this.retaliationTargetId == null) {
            return;
        }
        // 昼夜分明的角色会在天亮时失去战斗许可：此时必须主动松开仇恨，
        // 否则旧目标会绕过索敌限制继续追杀敌人。
        if (!this.canFightNow()) {
            this.retaliationTargetId = null;
            if (this.getTarget() != null) {
                this.setTarget(null);
            }
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity tracked = serverLevel.getEntityInAnyDimension(this.retaliationTargetId);
        if (!(tracked instanceof LivingEntity living) || !this.isValidRetaliationTarget(living)) {
            this.retaliationTargetId = null;
            LivingEntity target = this.getTarget();
            if (target != null && !target.isAlive()) {
                this.setTarget(null);
            }
            return;
        }
        if (this.getTarget() == null && !this.isPassenger()) {
            this.setTarget(living);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && this.attackVoiceCooldown <= 0) {
            this.speak(VoiceContext.ATTACK);
            this.attackVoiceCooldown = 110;
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        this.speak(VoiceContext.DEATH);
        this.retaliationTargetId = null;
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.clearSpeechBubble();
        super.remove(reason);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        // 默认装备属于角色本身，死亡时凭空消失；被她捡起并拿在手上的东西照常掉落。
        this.dropHeldLootOnly(level);
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        // 只有她真正捡进背包的东西才会掉落；没捡过就不凭空生成。
        for (ItemStack stack : this.backpack) {
            if (!stack.isEmpty()) {
                this.drop(stack.copy(), false, Prediction.SERVER_ONLY);
            }
        }
        this.backpack.clear();
        this.bossArmorWorn = false;
    }

    /** 无论走哪条死亡分支，默认装备都不会作为战利品出现在世界里。 */
    @Override
    protected void dropEquipment(ServerLevel level) {
        this.dropHeldLootOnly(level);
    }

    private void dropHeldLootOnly(ServerLevel level) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = this.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                if (!this.isDefaultItem(stack.getItem())) {
                    this.drop(stack.copy(), false, Prediction.SERVER_ONLY);
                }
                this.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide()) {
            if (player.isShiftKeyDown()) {
                this.setHomePoint(this.blockPosition());
                this.speak(VoiceContext.CHAT);
            } else {
                this.speak(this.greetingContext());
                this.greetCooldown = 600;
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** 夜里只用夜间台词，白天才用早安、午饭这类问候。 */
    public VoiceContext greetingContext() {
        return this.isNightTime() ? VoiceContext.GREETING_NIGHT : VoiceContext.GREETING;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("NpcSpawnPoint", this.npcSpawnPoint.asLong());
        output.putLong("HomePoint", this.homePoint.asLong());
        output.putBoolean("PointsInitialized", this.pointsInitialized);
        output.putString("RetaliationTarget", this.retaliationTargetId == null ? "" : this.retaliationTargetId.toString());
        output.putBoolean("BossArmorWorn", this.bossArmorWorn);
        ValueOutput.TypedOutputList<ItemStack> list = output.list("Backpack", ItemStack.CODEC);
        for (ItemStack stack : this.backpack) {
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.npcSpawnPoint = BlockPos.of(input.getLongOr("NpcSpawnPoint", 0L));
        this.homePoint = BlockPos.of(input.getLongOr("HomePoint", 0L));
        this.pointsInitialized = input.getBooleanOr("PointsInitialized", false);
        String retaliation = input.getStringOr("RetaliationTarget", "");
        try {
            this.retaliationTargetId = retaliation.isBlank() ? null : UUID.fromString(retaliation);
        } catch (IllegalArgumentException ignored) {
            this.retaliationTargetId = null;
        }
        this.bossArmorWorn = input.getBooleanOr("BossArmorWorn", false);
        this.ensureDefaultKit();
        this.backpack.clear();
        int index = 0;
        for (ItemStack stack : input.listOrEmpty("Backpack", ItemStack.CODEC)) {
            if (index >= BACKPACK_SIZE) {
                break;
            }
            // 旧存档里可能把默认工具存在背包中，迁移时直接剔除。
            if (stack.isEmpty() || this.isDefaultItem(stack.getItem())) {
                continue;
            }
            this.backpack.set(index++, stack.copy());
        }
        // Loading entity data can overwrite flags that were set in the
        // constructor. A character read from disk is still a permanent
        // resident of this world.
        this.setPersistenceRequired();
    }
}
