package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.ColumbinaEntity;
import com.akun.xinkun.entity.SandroneEntity;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * 白天的桑多涅会走到附近的哥伦比娅身侧，两人并肩同行。
 *
 * <p>她们每隔一段时间会用官方语音互相说上一两句：先是桑多涅的抱怨与
 * 关心，过一会儿哥伦比娅再回应。入夜后自动停止，各自回到原本的作息；
 * 任何一方进入战斗、附近出现敌人或玩家时，这段同行也会立刻中断。</p>
 */
public class SandroneCompanionGoal extends Goal {
    /** 只和附近二十四格以内的哥伦比娅结伴。 */
    private static final double FIND_RANGE = 24.0;
    /** 拉开到四十格以上就结束这次同行。 */
    private static final double LEAVE_RANGE = 40.0;
    /** 附近十八格内有敌人时，休闲同行必须让位。 */
    private static final double ENEMY_ALERT = 18.0;
    /** 两人说话时至少保持这么近，远距离喊话不像并肩散步。 */
    private static final double TALK_RANGE_SQR = 6.0 * 6.0;
    private static final int REPLY_DELAY_MIN = 55;
    private static final int REPLY_DELAY_MAX = 85;
    private static final int TALK_MIN = 900;
    private static final int TALK_MAX = 1500;

    private final SandroneEntity npc;
    private ColumbinaEntity partner;
    private int talkCooldown;
    private int replyTicks;
    private boolean awaitingReply;

    public SandroneCompanionGoal(SandroneEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.npc.isDayTime() || this.npc.getTarget() != null) {
            return false;
        }
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return false;
        }
        if (this.hasNearbyEnemy(level) || this.hasNearbyPlayer(level)) {
            return false;
        }
        ColumbinaEntity found = this.findPartner(level);
        if (found == null) {
            return false;
        }
        this.partner = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.npc.isDayTime() || this.npc.getTarget() != null) {
            return false;
        }
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return false;
        }
        if (this.partner == null || !this.partner.isAlive() || this.partner.isRemoved()
                || this.partner.getTarget() != null) {
            return false;
        }
        if (this.npc.distanceToSqr(this.partner) > LEAVE_RANGE * LEAVE_RANGE) {
            return false;
        }
        return !this.hasNearbyEnemy(level) && !this.hasNearbyPlayer(level);
    }

    private ColumbinaEntity findPartner(ServerLevel level) {
        List<ColumbinaEntity> found = level.getEntitiesOfClass(ColumbinaEntity.class,
                this.npc.getBoundingBox().inflate(FIND_RANGE),
                candidate -> candidate.isAlive() && !candidate.isRemoved()
                        && candidate.getTarget() == null);
        ColumbinaEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ColumbinaEntity candidate : found) {
            double distance = this.npc.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private boolean hasNearbyEnemy(ServerLevel level) {
        AABB area = this.npc.getBoundingBox().inflate(ENEMY_ALERT);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area,
                mob -> mob instanceof Enemy && mob.isAlive());
        return !mobs.isEmpty();
    }

    /** 玩家在旁边时优先照顾玩家，而不是把同伴晾在一边。 */
    private boolean hasNearbyPlayer(ServerLevel level) {
        Player player = level.getNearestPlayer(this.npc, 10.0);
        return player != null && !player.isSpectator();
    }

    @Override
    public void start() {
        this.talkCooldown = 100 + this.npc.getRandom().nextInt(140);
        this.replyTicks = 0;
        this.awaitingReply = false;
        this.npc.getLookControl().setLookAt(this.partner, 30.0F, 30.0F);
    }

    @Override
    public void stop() {
        this.npc.getNavigation().stop();
        this.partner = null;
        this.awaitingReply = false;
        this.replyTicks = 0;
    }

    @Override
    public void tick() {
        if (this.partner == null) {
            return;
        }
        this.npc.getLookControl().setLookAt(this.partner, 24.0F, 24.0F);
        if (this.npc.tickCount % 12 == 0 && this.npc.distanceToSqr(this.partner) <= TALK_RANGE_SQR) {
            this.partner.getLookControl().setLookAt(this.npc, 24.0F, 24.0F);
        }
        if (this.talkCooldown > 0) {
            this.talkCooldown--;
        }
        this.tickDialogue();
        this.walkBesidePartner();
    }

    /** 桑多涅先开口，隔一小会儿哥伦比娅回应，像真正的对话而不是同时念白。 */
    private void tickDialogue() {
        if (this.awaitingReply) {
            if (this.replyTicks > 0) {
                this.replyTicks--;
                return;
            }
            this.awaitingReply = false;
            if (!this.partner.isSpeaking()) {
                this.partner.speak(VoiceContext.TO_SANDRONE);
            }
            this.talkCooldown = TALK_MIN + this.npc.getRandom().nextInt(TALK_MAX - TALK_MIN + 1);
            return;
        }
        if (this.talkCooldown > 0) {
            return;
        }
        if (this.npc.distanceToSqr(this.partner) > TALK_RANGE_SQR) {
            return;
        }
        if (this.npc.isSpeaking() || this.partner.isSpeaking()) {
            return;
        }
        this.npc.speak(VoiceContext.TO_COLUMBINA);
        this.awaitingReply = true;
        this.replyTicks = REPLY_DELAY_MIN + this.npc.getRandom().nextInt(REPLY_DELAY_MAX - REPLY_DELAY_MIN + 1);
    }

    /** 走在哥伦比娅左侧半步处；她停下时桑多涅也停在旁边，而不是原地打转。 */
    private void walkBesidePartner() {
        float yaw = this.partner.getYRot() * ((float) Math.PI / 180.0F);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double sideX = forwardZ;
        double sideZ = -forwardX;
        double distance = 1.8;
        double x = this.partner.getX() + sideX * distance;
        double z = this.partner.getZ() + sideZ * distance;
        double y = this.partner.getY();
        double distanceSqr = this.npc.distanceToSqr(x, y, z);
        boolean partnerMoving = this.partner.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
        if (distanceSqr > 2.2 * 2.2 || (partnerMoving && this.npc.getNavigation().isDone())) {
            double speed = this.partner.isSprinting() ? 1.35 : 1.02;
            this.npc.getNavigation().moveTo(x, y, z, speed);
        } else if (!partnerMoving && this.npc.getNavigation().isDone()) {
            this.npc.getNavigation().stop();
        }
    }
}
