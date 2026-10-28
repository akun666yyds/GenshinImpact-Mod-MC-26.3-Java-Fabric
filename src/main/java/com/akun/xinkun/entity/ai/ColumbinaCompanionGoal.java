package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.ColumbinaEntity;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 没有敌人的夜晚，哥伦比娅会走到玩家身侧，与玩家并排散步。
 * 她会时不时说些亲密的话；如果玩家抬头望向月亮，她会害羞；
 * 每晚第一次并肩走上一段路后，她会把亲手做的月饼送给玩家。
 */
public class ColumbinaCompanionGoal extends Goal {
    private static final double FOLLOW_START = 26.0;
    private static final double FOLLOW_STOP = 44.0;
    private static final double ENEMY_ALERT = 18.0;
    private static final int GIFT_WALK_TICKS = 120;
    private static final int SHY_COOLDOWN = 1200;
    private static final int SPEAK_MIN = 230;
    private static final int SPEAK_MAX = 360;

    private final ColumbinaEntity npc;
    private Player player;
    private int walkTicks;
    private int speakCooldown;
    private int shyCooldown;

    public ColumbinaCompanionGoal(ColumbinaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.npc.isNightTime() || !this.npc.canFightNow()) {
            return false;
        }
        if (this.npc.getTarget() != null) {
            return false;
        }
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return false;
        }
        Player found = level.getNearestPlayer(this.npc, FOLLOW_START);
        if (found == null) {
            return false;
        }
        if (!this.isAvailable(found)) {
            return false;
        }
        if (this.hasNearbyEnemy(level)) {
            return false;
        }
        this.player = found;
        return true;
    }

    private boolean isAvailable(Player candidate) {
        return candidate != null && candidate.isAlive() && !candidate.isSpectator()
                && !candidate.isPassenger() && !candidate.isFallFlying()
                && this.npc.getSensing().hasLineOfSight(candidate);
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.npc.isNightTime() || this.npc.getTarget() != null) {
            return false;
        }
        if (!this.isAvailable(this.player)) {
            return false;
        }
        if (this.npc.distanceToSqr(this.player) > FOLLOW_STOP * FOLLOW_STOP) {
            return false;
        }
        return !this.hasNearbyEnemy((ServerLevel) this.npc.level());
    }

    private boolean hasNearbyEnemy(ServerLevel level) {
        AABB area = this.npc.getBoundingBox().inflate(ENEMY_ALERT);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area,
                mob -> mob instanceof Enemy && mob.isAlive());
        return !mobs.isEmpty();
    }

    @Override
    public void start() {
        this.walkTicks = 0;
        this.speakCooldown = 100;
        this.shyCooldown = 200;
        this.npc.getLookControl().setLookAt(this.player, 30.0F, 30.0F);
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
        this.player = null;
        this.walkTicks = 0;
    }

    @Override
    public void tick() {
        if (this.player == null) {
            return;
        }
        this.walkTicks++;
        if (this.shyCooldown > 0) {
            this.shyCooldown--;
        }
        if (this.speakCooldown > 0) {
            this.speakCooldown--;
        }
        this.npc.getLookControl().setLookAt(this.player, 22.0F, 22.0F);
        if (this.npc.tickCount % 12 == 0) {
            this.npc.getLookControl().setLookAt(this.player, 30.0F, 30.0F);
        }

        // 玩家抬头望月时，她会突然害羞。
        if (this.shyCooldown <= 0 && this.isLookingAtMoon(this.player)) {
            this.npc.speak(VoiceContext.SHY);
            this.shyCooldown = SHY_COOLDOWN;
            this.speakCooldown = Math.max(this.speakCooldown, 220);
        }

        // 每晚固定的月饼：并肩走过一小段路后才送，显得像散步时的自然举动。
        if (this.walkTicks >= GIFT_WALK_TICKS
                && this.npc.distanceToSqr(this.player) <= 49.0
                && this.npc.giveMooncake(this.player)) {
            this.speakCooldown = Math.max(this.speakCooldown, 260);
        }

        if (this.speakCooldown <= 0 && !this.npc.isSpeaking()) {
            this.npc.speak(VoiceContext.COMPANION);
            this.speakCooldown = SPEAK_MIN + this.npc.getRandom().nextInt(SPEAK_MAX - SPEAK_MIN + 1);
        }

        this.walkBesidePlayer();
    }

    private void walkBesidePlayer() {
        float yaw = this.player.getYRot() * ((float) Math.PI / 180.0F);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        // 玩家右侧的肩并肩位置。
        double sideX = -forwardZ;
        double sideZ = forwardX;
        double distance = 1.9;
        double x = this.player.getX() + sideX * distance;
        double z = this.player.getZ() + sideZ * distance;
        double y = this.player.getY();
        double distanceSqr = this.npc.distanceToSqr(x, y, z);
        boolean playerMoving = this.player.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
        if (distanceSqr > 2.2 * 2.2 || (playerMoving && this.npc.getNavigation().isDone())) {
            double speed = this.player.isSprinting() ? 1.45 : 1.08;
            this.npc.getNavigation().moveTo(x, y, z, speed);
        } else if (!playerMoving && this.npc.getNavigation().isDone()) {
            this.npc.getNavigation().stop();
        }
    }

    /** 玩家抬头、头顶露天、且没有下雨时，视为正在看月亮。 */
    private boolean isLookingAtMoon(Player player) {
        if (player.getXRot() > -28.0F) {
            return false;
        }
        if (this.npc.distanceToSqr(player) > 14.0 * 14.0) {
            return false;
        }
        if (player.level().isRaining()) {
            return false;
        }
        return player.level().canSeeSky(player.blockPosition());
    }
}
