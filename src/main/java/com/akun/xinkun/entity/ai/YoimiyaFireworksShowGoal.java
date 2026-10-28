package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.YoimiyaEntity;
import com.akun.xinkun.voice.VoiceContext;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 夜里，身边的玩家有很小的概率被宵宫邀请看一场烟花。
 *
 * <p>整套表演由真实的烟花火箭完成：火箭在玩家头顶十几格处炸开，声音和
 * 颜色都是原版的，五格的爆炸伤害范围够不到地面，因此不会伤到观众。表演
 * 结束后，她还有不小的概率把随身的小玩意送给玩家。</p>
 */
public class YoimiyaFireworksShowGoal extends Goal {
    private static final double INVITE_RADIUS = 12.0;
    private static final double KEEP_RADIUS = 20.0;
    private static final double THREAT_RADIUS = 12.0;
    private static final int ROLL_INTERVAL = 60;
    private static final int ROLL_THRESHOLD = 6;
    private static final int SHOW_DURATION = 180;
    private static final int ROCKET_INTERVAL_MIN = 14;
    private static final int ROCKET_INTERVAL_MAX = 24;
    private static final int COOLDOWN_MIN = 2400;
    private static final int COOLDOWN_MAX = 4800;
    private static final int INTERRUPTED_COOLDOWN = 1200;
    private static final int[] COLORS = {
            0xFFF3B0, 0xFFC23D, 0xFF8A3D, 0xFF5A5A, 0xFF7FD1, 0xB388FF, 0x66D9FF, 0x8CFF9E
    };
    private static final FireworkExplosion.Shape[] SHAPES = {
            FireworkExplosion.Shape.LARGE_BALL,
            FireworkExplosion.Shape.STAR,
            FireworkExplosion.Shape.BURST,
            FireworkExplosion.Shape.SMALL_BALL
    };

    private final YoimiyaEntity npc;
    private Player audience;
    private int rollTicker = ROLL_INTERVAL;
    private int cooldown;
    private int showTicks;
    private int rocketTicker;
    private boolean finished;
    private boolean giftPlanned;
    private boolean spokeDuringShow;

    public YoimiyaFireworksShowGoal(YoimiyaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (!this.npc.isNightTime() || this.npc.isSpeaking()) {
            return false;
        }
        if (!(this.npc.level() instanceof ServerLevel level) || level.isRaining()) {
            return false;
        }
        // 只要身边没有迫近的敌人就可以表演；远处的战斗目标不会阻止她。
        if (this.isThreatNearby(level)) {
            return false;
        }
        if (--this.rollTicker > 0) {
            return false;
        }
        this.rollTicker = ROLL_INTERVAL;
        Player player = level.getNearestPlayer(this.npc, INVITE_RADIUS);
        if (player == null || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (!this.hasOpenSky(level, player.blockPosition())
                || !this.hasOpenSky(level, this.npc.blockPosition())) {
            return false;
        }
        if (this.npc.getRandom().nextInt(ROLL_THRESHOLD) != 0) {
            return false;
        }
        this.audience = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.showTicks <= 0) {
            return false;
        }
        if (this.npc.level() instanceof ServerLevel level && this.isThreatNearby(level)) {
            return false;
        }
        if (this.audience == null || !this.audience.isAlive()
                || this.audience.level() != this.npc.level()) {
            return false;
        }
        return this.npc.distanceToSqr(this.audience) <= KEEP_RADIUS * KEEP_RADIUS;
    }

    @Override
    public void start() {
        this.showTicks = SHOW_DURATION;
        this.rocketTicker = 12;
        this.finished = false;
        this.spokeDuringShow = false;
        this.giftPlanned = this.npc.getRandom().nextFloat() < 0.6F;
        this.npc.getNavigation().stop();
        this.npc.getLookControl().setLookAt(this.audience, 30.0F, 30.0F);
        this.npc.beginFireworkShow();
        this.npc.speak(VoiceContext.FIREWORK_INVITE);
        this.npc.markSpecialVoice(600);
    }

    @Override
    public void stop() {
        this.npc.endFireworkShow();
        this.npc.getNavigation().stop();
        if (this.finished && this.audience != null && this.audience.isAlive()
                && this.npc.level() instanceof ServerLevel level) {
            if (this.giftPlanned) {
                this.giveGift(level);
            }
            this.cooldown = COOLDOWN_MIN
                    + this.npc.getRandom().nextInt(COOLDOWN_MAX - COOLDOWN_MIN + 1);
        } else {
            this.cooldown = INTERRUPTED_COOLDOWN;
        }
        this.finished = false;
        this.giftPlanned = false;
        this.audience = null;
        this.showTicks = 0;
    }

    @Override
    public void tick() {
        if (!(this.npc.level() instanceof ServerLevel level) || this.audience == null) {
            return;
        }
        this.npc.getLookControl().setLookAt(this.audience, 30.0F, 30.0F);
        this.npc.getNavigation().stop();

        // 表演期间保持双臂举高的姿态；升空的火箭同样沿用这个表演姿势。
        this.npc.keepFireworkShowPose();
        if (--this.rocketTicker <= 0) {
            this.launchRocket(level);
            this.rocketTicker = ROCKET_INTERVAL_MIN
                    + this.npc.getRandom().nextInt(ROCKET_INTERVAL_MAX - ROCKET_INTERVAL_MIN + 1);
        }
        if (!this.spokeDuringShow && this.showTicks < SHOW_DURATION - 60) {
            this.spokeDuringShow = true;
            this.npc.speak(VoiceContext.FIREWORK_SHOW);
        }
        if (--this.showTicks <= 0) {
            this.finished = true;
        }
    }

    private boolean hasOpenSky(ServerLevel level, BlockPos pos) {
        int roof = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return roof <= pos.getY() + 1;
    }

    /** 只有贴近的敌人才会让她放下烟花；远处目标交给战斗 AI 慢慢处理。 */
    private boolean isThreatNearby(ServerLevel level) {
        return !level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                this.npc.getBoundingBox().inflate(THREAT_RADIUS),
                entity -> entity instanceof net.minecraft.world.entity.monster.Enemy
                        && !(entity instanceof net.minecraft.world.entity.monster.Creeper)
                        && !(entity instanceof com.akun.xinkun.entity.WaterSpiritEntity)
                        && entity.isAlive()).isEmpty();
    }

    private void launchRocket(ServerLevel level) {
        RandomSource random = this.npc.getRandom();
        double x = this.audience.getX() + (random.nextDouble() - 0.5) * 8.0;
        double z = this.audience.getZ() + (random.nextDouble() - 0.5) * 8.0;
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));
        double y = Math.max(this.audience.getY(), floor) + 11.0 + random.nextInt(5);

        ItemStack rocketItem = this.rocketStack(1, 1);
        FireworkRocketEntity rocket = new FireworkRocketEntity(level, x, y, z, rocketItem);
        rocket.setDeltaMovement(0.0, 0.06, 0.0);
        level.addFreshEntity(rocket);
        level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.NEUTRAL, 1.0F, 0.95F + random.nextFloat() * 0.1F);
    }

    private void giveGift(ServerLevel level) {
        RandomSource random = this.npc.getRandom();
        ItemStack gift = switch (random.nextInt(3)) {
            case 0 -> new ItemStack(Items.FIRE_CHARGE, 2 + random.nextInt(3));
            case 1 -> this.rocketStack(2 + random.nextInt(3), 2);
            default -> new ItemStack(Items.FLINT_AND_STEEL);
        };
        if (!this.audience.getInventory().add(gift)) {
            this.audience.drop(gift, false, Prediction.SERVER_ONLY);
        }
        this.audience.inventoryMenu.broadcastChanges();
        this.npc.speak(VoiceContext.FIREWORK_GIFT);
        this.npc.markSpecialVoice(600);
        level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.05F);
    }

    private ItemStack rocketStack(int count, int flightDuration) {
        RandomSource random = this.npc.getRandom();
        int first = COLORS[random.nextInt(COLORS.length)];
        int second = COLORS[random.nextInt(COLORS.length)];
        IntList colors = first == second ? IntList.of(first) : IntList.of(first, second);
        IntList fade = IntList.of(COLORS[random.nextInt(COLORS.length)]);
        FireworkExplosion.Shape shape = SHAPES[random.nextInt(SHAPES.length)];
        FireworkExplosion explosion = new FireworkExplosion(shape, colors, fade, true, true);
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET, count);
        stack.set(DataComponents.FIREWORKS, new Fireworks(flightDuration, List.of(explosion)));
        return stack;
    }
}
