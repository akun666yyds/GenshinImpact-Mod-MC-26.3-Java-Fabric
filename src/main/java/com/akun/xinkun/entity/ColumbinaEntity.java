package com.akun.xinkun.entity;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.entity.ai.ColumbinaCompanionGoal;
import com.akun.xinkun.entity.ai.ColumbinaHealGoal;
import com.akun.xinkun.entity.ai.ColumbinaMoonSpellGoal;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.registry.ModItems;
import com.akun.xinkun.voice.VoiceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 哥伦比娅。白天只工作、不还手；入夜后收起工具，改用附魔书与月影作战。
 * 她永远照看身边的友方，夜深无敌人时则会走到玩家身侧并肩散步，
 * 每晚送给同一位玩家一枚亲手做的月饼。
 *
 * <p>昼夜切换通过基类的 {@link GenshinNpcEntity#canFightNow()} 钩子统一作用到
 * 索敌、反击与战斗目标上，避免各处再各自判断时间。</p>
 */
public class ColumbinaEntity extends GenshinNpcEntity {
    /** 每位玩家每晚只能收到一枚月饼，记录“玩家 UUID → 第几个夜晚”。 */
    private final Map<UUID, Long> giftedNights = new HashMap<>();

    public ColumbinaEntity(EntityType<? extends ColumbinaEntity> type, Level level) {
        super(type, level);
        var followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            // 月影的施法距离很远，她需要先看见更远处的敌人。
            followRange.setBaseValue(48.0);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GenshinNpcEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0);
    }

    @Override
    public GenshinCharacter getCharacter() {
        return GenshinCharacter.COLUMBINA;
    }

    @Override
    public boolean canFightNow() {
        return this.isNightTime();
    }

    /** 战斗时手持「不灭月华」：月影术的媒介，也是近身时的钝击物。 */
    @Override
    public boolean ensureWeapon() {
        return super.ensureWeapon();
    }

    @Override
    protected void addCharacterDefaults(List<ItemStack> kit) {
        addDefault(kit, Items.ENCHANTED_BOOK);
    }

    @Override
    protected void registerCharacterGoals() {
        // 月影术只在意目标，不抢移动；近身仍有共享战斗目标接管。
        this.goalSelector.addGoal(2, new ColumbinaMoonSpellGoal(this));
        // 夜间散步是休闲行为，优先级低于战斗、工作与拾取。
        this.goalSelector.addGoal(7, new ColumbinaCompanionGoal(this));
        // 治疗不占用移动，随时可以打断自己的空闲行为去照看伤员。
        this.goalSelector.addGoal(8, new ColumbinaHealGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        // 天亮后立即放下夜间锁定的目标；白天她绝不主动攻击任何敌人。
        if (!this.canFightNow() && this.getTarget() != null) {
            this.setTarget(null);
        }
    }

    /** 当前是第几个夜晚，用来判定“今晚”是否已经送过月饼。 */
    private long currentNight() {
        return this.level().getDefaultClockTime() / 24000L;
    }

    public boolean canGiftTonight(net.minecraft.world.entity.player.Player player) {
        return !Long.valueOf(this.currentNight()).equals(this.giftedNights.get(player.getUUID()));
    }

    /**
     * 把一枚月饼放到玩家手上。背包满了就掉在脚边，绝不吞掉礼物。
     * 只有真正送出去才写入“今晚已赠”，并触发赠礼语音。
     */
    public boolean giveMooncake(net.minecraft.world.entity.player.Player player) {
        if (this.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!this.canGiftTonight(serverPlayer)) {
            return false;
        }
        ItemStack cake = new ItemStack(ModItems.MOONCAKE);
        if (!serverPlayer.getInventory().add(cake)) {
            serverPlayer.drop(cake, false, Prediction.SERVER_ONLY);
        }
        this.giftedNights.put(serverPlayer.getUUID(), this.currentNight());
        this.pruneGiftRecords();
        this.speak(VoiceContext.GIFT);
        this.getLookControl().setLookAt(serverPlayer, 30.0F, 30.0F);
        if (this.level() instanceof ServerLevel level) {
            NpcFx.moonHeal(level, serverPlayer.position());
            level.sendParticles(ParticleTypes.HEART,
                    serverPlayer.getX(), serverPlayer.getY() + serverPlayer.getBbHeight() + 0.35,
                    serverPlayer.getZ(), 3, 0.25, 0.2, 0.25, 0.0);
        }
        return true;
    }

    /** 旧夜晚的记录没有作用，顺手清掉，避免长期游玩时无限增长。 */
    private void pruneGiftRecords() {
        long oldestUseful = this.currentNight() - 1L;
        Iterator<Map.Entry<UUID, Long>> iterator = this.giftedNights.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < oldestUseful) {
                iterator.remove();
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        List<String> records = new ArrayList<>(this.giftedNights.size());
        for (Map.Entry<UUID, Long> entry : this.giftedNights.entrySet()) {
            records.add(entry.getKey() + ":" + entry.getValue());
        }
        output.putString("GiftNights", String.join(",", records));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.giftedNights.clear();
        String raw = input.getStringOr("GiftNights", "");
        for (String record : raw.split(",")) {
            String trimmed = record.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int split = trimmed.lastIndexOf(':');
            if (split <= 0 || split >= trimmed.length() - 1) {
                continue;
            }
            try {
                UUID player = UUID.fromString(trimmed.substring(0, split));
                long night = Long.parseLong(trimmed.substring(split + 1));
                this.giftedNights.put(player, night);
            } catch (IllegalArgumentException ignored) {
                // 损坏的记录直接跳过，不影响角色继续工作。
            }
        }
    }

    /** 供战斗逻辑检查她是否处于“有敌人”的状态。 */
    public boolean hasHostileTarget() {
        LivingEntity target = this.getTarget();
        return target != null && target.isAlive();
    }
}
