package com.akun.xinkun.item;

import com.akun.xinkun.entity.GenshinNpcEntity;
import com.akun.xinkun.entity.MoonRabbitEntity;
import com.akun.xinkun.entity.WaterSpiritEntity;
import com.akun.xinkun.particle.GlowDustOptions;
import com.akun.xinkun.registry.ModItems;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 纳西妲的绿色书卷。翻页时会有一圈草叶从书页里卷出，
 * 横扫身边六格内的敌人：造成较高的范围伤害，并让被命中的敌人
 * 通体泛绿、染上十六秒的霉运。
 */
public class VerdantCodexItem extends Item {
    /** 书页横扫的半径。 */
    public static final double SWEEP_RADIUS = 6.0;
    /** 书页向上卷起的高度：头顶飞行的敌人同样在横扫范围内。 */
    public static final double SWEEP_VERTICAL_REACH = 32.0;
    /** 玩家挥书与纳西妲施法的冷却（tick）。 */
    public static final int SWEEP_COOLDOWN = 50;
    /** 命中后的霉运持续时间：十六秒。 */
    public static final int UNLUCK_TICKS = 320;
    /** 敌人身上的绿色叶尘颜色。 */
    public static final int LEAF_COLOR = 0x46B96A;
    private static final GlowDustOptions GLOW_LEAF = new GlowDustOptions(LEAF_COLOR, 1.6F);

    public VerdantCodexItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        player.getCooldowns().addCooldown(stack, SWEEP_COOLDOWN);
        sweep(serverLevel, player, 16.0F);
        player.swing(hand, SwingAnimation.DEFAULT, true);
        return InteractionResult.CONSUME;
    }

    /**
     * 以施法者为中心横扫一圈。玩家与纳西妲共用同一套表现：
     * 叶片从中心向外扩散，命中者被草叶缠住、泛绿并陷入霉运。
     */
    public static void sweep(ServerLevel level, LivingEntity caster, float damage) {
        Vec3 center = caster.position();
        double y = caster.getY() + caster.getBbHeight() * 0.55;
        // 三圈草叶从脚下扫出，最外圈正好是六格。
        for (int ring = 1; ring <= 3; ring++) {
            double radius = ring * (SWEEP_RADIUS / 3.0);
            int count = 18 + ring * 6;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0 * i / count + ring * 0.22;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                level.sendParticles(GLOW_LEAF,
                        x, y + Math.sin(angle * 3.0 + ring) * 0.35, z,
                        1, 0.08, 0.12, 0.08, 0.02);
            }
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, y + 0.4, center.z,
                16, 2.2, 0.5, 2.2, 0.0);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.8F);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 0.8F, 1.5F);

        AABB area = caster.getBoundingBox().inflate(SWEEP_RADIUS, SWEEP_VERTICAL_REACH, SWEEP_RADIUS);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
                target -> isValidTarget(caster, target));
        Vec3 eye = caster.getEyePosition();
        for (LivingEntity victim : victims) {
            // 只按水平距离判定，头顶的目标不会被高度差推出横扫范围。
            double dx = victim.getX() - caster.getX();
            double dz = victim.getZ() - caster.getZ();
            if (dx * dx + dz * dz > SWEEP_RADIUS * SWEEP_RADIUS + 0.5) {
                continue;
            }
            if (Math.abs(victim.getY() - caster.getY()) > SWEEP_VERTICAL_REACH) {
                continue;
            }
            if (victim.getY() > caster.getY() + 2.5) {
                // 高处的敌人：草叶沿视线向上卷起，命中表现与地面目标一致。
                Vec3 centerOfVictim = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
                for (int step = 1; step <= 12; step++) {
                    Vec3 point = eye.lerp(centerOfVictim, step / 12.0);
                    level.sendParticles(GLOW_LEAF, point.x, point.y, point.z,
                            2, 0.12, 0.12, 0.12, 0.01);
                }
            }
            var source = caster instanceof Player player
                    ? level.damageSources().playerAttack(player)
                    : level.damageSources().mobAttack(caster);
            victim.hurtServer(level, source, damage);
            victim.addEffect(new MobEffectInstance(MobEffects.UNLUCK, UNLUCK_TICKS, 1, false, true, true));
            // 泛绿：发光轮廓加上一圈草叶，即使躲进暗处也看得见。
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, UNLUCK_TICKS, 0, false, false, true));
            level.sendParticles(GLOW_LEAF,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    18, 0.35, 0.55, 0.35, 0.04);
        }
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 0.35F, 1.35F);
    }

    private static boolean isValidTarget(LivingEntity caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player player) {
            // 角色之间、玩家之间不互相误伤；只打真正的敌人。
            return caster instanceof Player && !player.isCreative() && !player.isSpectator();
        }
        if (target instanceof GenshinNpcEntity
                || target instanceof WaterSpiritEntity
                || target instanceof MoonRabbitEntity) {
            return false;
        }
        return true;
    }

    /** 让 AI 也能判断手上拿的是不是这本书。 */
    public static boolean isCodex(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.VERDANT_CODEX);
    }
}
