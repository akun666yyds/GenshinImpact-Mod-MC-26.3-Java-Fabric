package com.akun.xinkun.entity;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.entity.ai.NahidaCombatGoal;
import com.akun.xinkun.entity.ai.NahidaEcologyGoal;
import com.akun.xinkun.registry.ModItems;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 纳西妲。须弥的草之神，以小吉祥草王的模样行走人间。
 *
 * <p>她不会伐木，也不会挖矿：双手只用来播种。背包里的树苗、花草、骨粉和泥土
 * 取之不尽，死亡也不会掉落。她会一边走一边以自己为圆心，把半径十六格内的
 * 沙、砂砾、陶瓦与枯木化成草方块和泥土，随即种上花草、树苗并当场催熟；
 * 走过的草地也会持续补种和施肥。巡游时双手高举，绿色叶尘与催熟粒子环绕全身，
 * 并会念出草木相关的台词。</p>
 *
 * <p>战斗时手中浮现一本绿色书卷，翻页扫出一圈草木叶片，命中身边六格内的敌人。</p>
 */
public class NahidaEntity extends GenshinNpcEntity {
    /** 正在绿化：客户端据此摆出双手举起的施术姿态。 */
    private static final EntityDataAccessor<Boolean> DATA_BLOOMING =
            SynchedEntityData.defineId(NahidaEntity.class, EntityDataSerializers.BOOLEAN);

    public NahidaEntity(EntityType<? extends NahidaEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BLOOMING, false);
    }

    public void setBlooming(boolean blooming) {
        if (this.level().isClientSide()) {
            return;
        }
        this.entityData.set(DATA_BLOOMING, blooming);
    }

    public boolean isBlooming() {
        return this.entityData.get(DATA_BLOOMING);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GenshinNpcEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 36.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0);
    }

    @Override
    public GenshinCharacter getCharacter() {
        return GenshinCharacter.NAHIDA;
    }

    /** 战斗时手持五星专武「千夜浮梦」；翠叶书卷仍作为随身道具保留。 */
    @Override
    public boolean ensureWeapon() {
        return super.ensureWeapon();
    }

    @Override
    protected void addCharacterDefaults(List<ItemStack> kit) {
        addDefault(kit, ModItems.VERDANT_CODEX);
        // 取之不尽的泥土、骨粉与草木种子。
        addDefault(kit, Items.DIRT);
        addDefault(kit, Items.BONE_MEAL);
        addDefault(kit, Items.MOSS_BLOCK);
        addDefault(kit, Items.SHORT_GRASS);
        addDefault(kit, Items.FERN);
        addDefault(kit, Items.AZALEA);
        addDefault(kit, Items.FLOWERING_AZALEA);
        addDefault(kit, Items.OAK_SAPLING);
        addDefault(kit, Items.BIRCH_SAPLING);
        addDefault(kit, Items.JUNGLE_SAPLING);
        addDefault(kit, Items.ACACIA_SAPLING);
        addDefault(kit, Items.CHERRY_SAPLING);
        addDefault(kit, Items.DARK_OAK_SAPLING);
        addDefault(kit, Items.MANGROVE_PROPAGULE);
        addDefault(kit, Items.DANDELION);
        addDefault(kit, Items.POPPY);
        addDefault(kit, Items.BLUE_ORCHID);
        addDefault(kit, Items.ALLIUM);
        addDefault(kit, Items.AZURE_BLUET);
        addDefault(kit, Items.RED_TULIP);
        addDefault(kit, Items.ORANGE_TULIP);
        addDefault(kit, Items.WHITE_TULIP);
        addDefault(kit, Items.PINK_TULIP);
        addDefault(kit, Items.OXEYE_DAISY);
        addDefault(kit, Items.CORNFLOWER);
        addDefault(kit, Items.LILY_OF_THE_VALLEY);
        addDefault(kit, Items.SUNFLOWER);
        addDefault(kit, Items.LILAC);
        addDefault(kit, Items.ROSE_BUSH);
        addDefault(kit, Items.PEONY);
    }

    @Override
    protected void registerCharacterGoals() {
        // 草叶横扫优先于共享近战；生态巡游低于战斗与拾取。
        this.goalSelector.addGoal(2, new NahidaCombatGoal(this));
        this.goalSelector.addGoal(4, new NahidaEcologyGoal(this));
    }

    /** 不砍树。 */
    @Override
    public boolean canChopTrees() {
        return false;
    }

    /** 不挖矿。 */
    @Override
    public boolean canMineOre() {
        return false;
    }

    /**
     * 她会离开出生地去寻找荒地造林；离家三十格就召回会让她永远走不到沙漠。
     * 掉出世界、卡进墙里和落进岩浆的紧急救援仍然生效。
     */
    @Override
    public boolean shouldStayNearHome() {
        return false;
    }
}
