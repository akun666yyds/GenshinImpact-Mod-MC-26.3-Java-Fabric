package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.SandroneEntity;
import com.akun.xinkun.fx.NpcFx;
import com.akun.xinkun.voice.VoiceContext;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 桑多涅的冰霜射线。
 *
 * <p>她浮在半空，双手向前推，从掌心连续射出粗壮的淡蓝与雪白粒子流。
 * 射线无重力、射速极高，会贯穿沿途所有敌人，并让对方持续冰冻减速；
 * 命中方块时按材质转化：水结冰、火熄灭、岩浆凝成黑曜石，普通方块表面
 * 则会一层层积雪。</p>
 */
public class SandroneFrostRayGoal extends Goal {
    /** 射线最大长度：远到能覆盖半个战场，但不会穿过未加载区块。 */
    private static final double RANGE = 48.0;
    /** 每两刻一发：视觉上就是一条连续不断的粒子流。 */
    private static final int SHOT_INTERVAL = 2;
    /** 射线判定半径：比视觉核心略粗，贯穿时不容易擦边漏掉。 */
    private static final double HIT_RADIUS = 0.85;
    /** 超出这个距离就向前飘一段，靠近到舒适射程后停住。 */
    private static final double ADVANCE_SQR = 22.0 * 22.0;
    /** 贴得太近会往后退，保持浮空施法的距离。 */
    private static final double RETREAT_SQR = 7.0 * 7.0;
    private static final double CAST_RANGE_SQR = 52.0 * 52.0;

    private final SandroneEntity npc;
    private int shotCooldown;
    private int navCooldown;
    private int shotCounter;

    public SandroneFrostRayGoal(SandroneEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.npc.canFightNow()) {
            return false;
        }
        LivingEntity target = this.npc.getTarget();
        if (!this.isValidTarget(target)) {
            return false;
        }
        return this.npc.distanceToSqr(target) <= CAST_RANGE_SQR
                && this.npc.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.npc.getTarget();
        if (!this.npc.canFightNow() || !this.isValidTarget(target)) {
            return false;
        }
        if (this.npc.distanceToSqr(target) > (CAST_RANGE_SQR + 64.0)) {
            return false;
        }
        return this.npc.getSensing().hasLineOfSight(target)
                || this.npc.distanceToSqr(target) > ADVANCE_SQR;
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive()
                && !(target instanceof Player)
                && !(target instanceof com.akun.xinkun.entity.GenshinNpcEntity);
    }

    @Override
    public void start() {
        this.shotCooldown = 0;
        this.navCooldown = 0;
        // 双手要向前推出，手里不能还握着剑；战斗结束后共享目标会重新取回武器。
        this.npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.npc.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        this.npc.setCastingFrost(true);
        this.npc.getNavigation().stop();
        if (this.npc.isSpecialVoiceReady()) {
            this.npc.speak(VoiceContext.FROST);
            this.npc.markSpecialVoice(420);
        }
    }

    @Override
    public void stop() {
        this.npc.setCastingFrost(false);
        this.npc.getNavigation().stop();
        this.shotCounter = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.npc.getTarget();
        if (!(this.npc.level() instanceof ServerLevel level) || target == null) {
            return;
        }
        this.npc.setCastingFrost(true);
        this.npc.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ(), 80.0F, 80.0F);

        this.keepDistance(level, target);

        if (this.shotCooldown > 0) {
            this.shotCooldown--;
        }
        if (this.shotCooldown == 0 && this.npc.getSensing().hasLineOfSight(target)) {
            this.shotCooldown = SHOT_INTERVAL;
            this.fire(level, target);
        }
    }

    /** 远则飘近、近则后撤，其余时间悬停原地持续射击。 */
    private void keepDistance(ServerLevel level, LivingEntity target) {
        if (this.navCooldown > 0) {
            this.navCooldown--;
            return;
        }
        double distanceSqr = this.npc.distanceToSqr(target);
        if (distanceSqr > ADVANCE_SQR) {
            Vec3 aim = target.position().subtract(this.npc.position());
            double length = Math.hypot(aim.x, aim.z);
            if (length > 0.01) {
                double x = target.getX() - aim.x / length * 12.0;
                double z = target.getZ() - aim.z / length * 12.0;
                this.npc.getNavigation().moveTo(x, target.getY(), z, 1.05);
                this.navCooldown = 20;
            }
        } else if (distanceSqr < RETREAT_SQR) {
            Vec3 away = this.npc.position().subtract(target.position());
            double length = Math.hypot(away.x, away.z);
            if (length > 0.01) {
                double x = this.npc.getX() + away.x / length * 7.0;
                double z = this.npc.getZ() + away.z / length * 7.0;
                this.npc.getNavigation().moveTo(x, this.npc.getY(), z, 0.9);
                this.navCooldown = 14;
            }
        } else if (!this.npc.getNavigation().isDone()) {
            this.npc.getNavigation().stop();
        }
    }

    /**
     * 一发完整的射线：先做方块射线拿到真实落点，再捡起线段附近的所有敌人
     * （贯穿），最后按命中方块做结冰 / 灭火 / 黑曜石 / 积雪的转化。
     */
    private void fire(ServerLevel level, LivingEntity target) {
        this.shotCounter++;
        Vec3 origin = this.handOrigin();
        Vec3 aim = target.getEyePosition().subtract(origin);
        if (aim.lengthSqr() < 1.0E-4) {
            aim = this.npc.getLookAngle();
        }
        Vec3 direction = aim.normalize();
        Vec3 end = origin.add(direction.scale(RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this.npc));
        Vec3 impact = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        // 把真实落点同步给客户端，连续光束才能严丝合缝地停在方块或目标身上。
        this.npc.setBeamTarget(impact);

        this.pierceEntities(level, origin, impact);
        // 火与岩浆没有完整碰撞盒，射线会直接穿过火焰；沿整条射线逐格检查，
        // 才能把挡在弹道上的火苗扑灭、让水与岩浆相变。
        this.freezeAlongRay(level, origin, impact);
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            this.freezeBlock(level, blockHit, direction);
        }

        NpcFx.frostRay(level, origin, impact, this.shotCounter);
        this.palmHalos(level, direction);
        this.playFrostSound(level, origin);
    }

    /** 双手向前推时，射线从掌心之间约胸口高度射出。 */
    private Vec3 handOrigin() {
        Vec3 flat = Vec3.directionFromRotation(0.0F, this.npc.getYRot());
        return new Vec3(this.npc.getX(), this.npc.getY() + 1.58, this.npc.getZ())
                .add(flat.scale(0.62));
    }

    /** 两只手掌的位置，用来挂载旋转的机械光环。 */
    private void palmHalos(ServerLevel level, Vec3 direction) {
        Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = Vec3.directionFromRotation(0.0F, this.npc.getYRot());
        }
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
        Vec3 base = new Vec3(this.npc.getX(), this.npc.getY() + 1.52, this.npc.getZ())
                .add(flat.scale(0.58));
        Vec3 left = base.add(side.scale(0.36));
        Vec3 right = base.add(side.scale(-0.36));
        NpcFx.frostPalmHalo(level, left, right, direction, this.shotCounter);
    }

    /** 冰霜射线的伤害来源：沿用原版魔法伤害类型，但把击杀归到桑多涅名下。 */
    private DamageSource frostDamage(ServerLevel level) {
        Holder<DamageType> type = level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(DamageTypes.MAGIC);
        return new DamageSource(type, this.npc, this.npc);
    }

    private void pierceEntities(ServerLevel level, Vec3 from, Vec3 to) {
        AABB area = new AABB(from, to).inflate(HIT_RADIUS + 0.4);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != this.npc
                        && candidate.isAlive()
                        && !(candidate instanceof Player)
                        && !(candidate instanceof com.akun.xinkun.entity.GenshinNpcEntity)
                        && distanceToSegmentSqr(candidate.position().add(0.0, candidate.getBbHeight() * 0.5, 0.0),
                        from, to) <= HIT_RADIUS * HIT_RADIUS);
        float damage = (float) Math.max(1.5, this.npc.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.32);
        for (LivingEntity victim : victims) {
            // 冰冻：持续减速，并让身体出现冻结白霜；火焰也会被雪雾扑灭。
            victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 160));
            victim.setRemainingFireTicks(0);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2,
                    false, true, true), this.npc);
            // 射线每两刻落一发，但原版受击冷却（damageCooldownTime）是二十刻，
            // 会把持续伤害吞掉八成。冰霜射线是贯穿的持续流，这里清掉受击冷却，
            // 让每一发都按完整伤害结算。
            victim.damageCooldownTime = 0;
            victim.setInvulnerableTime(0);
            victim.hurtTime = 0;
            // 伤害类型用原版 minecraft:magic：它在原版 no_knockback 标签里，
            // 受击者不会被射线推着后退；若用 indirect_magic 则每次命中都会
            // 施加 0.4 的随机方向击退，把目标推出射线判定范围。
            victim.hurtServer(level, this.frostDamage(level), damage);
            NpcFx.frostBurst(level, victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0), 0.8F);
        }
    }

    /** 沿射线逐格处理火焰、水与岩浆：火焰熄灭，水凝成冰，岩浆凝成黑曜石。 */
    private void freezeAlongRay(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double length = segment.length();
        if (length < 1.0E-4) {
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(length / 0.5));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos previous = null;
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.add(segment.scale((double) i / steps));
            cursor.set(point.x, point.y, point.z);
            if (cursor.equals(previous)) {
                continue;
            }
            previous = cursor.immutable();
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                NpcFx.frostBurst(level, Vec3.atCenterOf(cursor), 0.45F);
            } else if (state.is(Blocks.WATER)) {
                level.setBlock(cursor, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
                NpcFx.frostBurst(level, Vec3.atCenterOf(cursor), 0.5F);
            } else if (state.is(Blocks.LAVA)) {
                level.setBlock(cursor, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                NpcFx.frostBurst(level, Vec3.atCenterOf(cursor), 0.8F);
            }
        }
    }

    /** 方块转化：水→冰、岩浆→黑曜石、火→熄灭，其余表面逐层积雪。 */
    private void freezeBlock(ServerLevel level, BlockHitResult hit, Vec3 direction) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        // 水源与流动的水都是 WATER 方块，命中后整体凝成冰。
        if (state.is(Blocks.WATER)) {
            level.setBlock(pos, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
            NpcFx.frostBurst(level, hit.getLocation(), 0.7F);
            return;
        }
        if (state.is(Blocks.LAVA)) {
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            NpcFx.frostBurst(level, hit.getLocation(), 1.0F);
            return;
        }
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            NpcFx.frostBurst(level, hit.getLocation(), 0.45F);
            return;
        }
        // 已经是积雪层：继续加厚，直到八层。
        if (state.is(Blocks.SNOW) && state.hasProperty(SnowLayerBlock.LAYERS)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers < SnowLayerBlock.MAX_HEIGHT) {
                level.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
            }
            this.scatterSnow(level, pos);
            return;
        }
        // 普通方块：在命中面上铺一层薄雪。
        Direction face = hit.getDirection();
        BlockPos above = pos.relative(face);
        if (face == Direction.UP && state.isFaceSturdy(level, pos, face)
                && level.getBlockState(above).isAir()) {
            level.setBlock(above, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
        }
        this.scatterSnow(level, pos);
    }

    /**
     * 命中点附近随机让几格露天方块铺上薄雪。射速很高，所以即使每发只落一点点，
     * 连续射击后整片地面也会慢慢变白。
     */
    private void scatterSnow(ServerLevel level, BlockPos center) {
        for (int i = 0; i < 3; i++) {
            int dx = level.getRandom().nextInt(5) - 2;
            int dz = level.getRandom().nextInt(5) - 2;
            BlockPos base = center.offset(dx, 0, dz);
            BlockPos top = this.findSnowSurface(level, base);
            if (top == null) {
                continue;
            }
            BlockState current = level.getBlockState(top);
            if (current.is(Blocks.SNOW)) {
                if (current.getValue(SnowLayerBlock.LAYERS) < 5 && level.getRandom().nextBoolean()) {
                    level.setBlock(top, current.setValue(SnowLayerBlock.LAYERS,
                            current.getValue(SnowLayerBlock.LAYERS) + 1), Block.UPDATE_ALL);
                }
            } else if (current.isAir() && level.getRandom().nextFloat() < 0.55F) {
                level.setBlock(top, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** 从命中高度上下各找三格，返回能承托积雪的露天空气格。 */
    private BlockPos findSnowSurface(ServerLevel level, BlockPos base) {
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos candidate = base.offset(0, dy, 0);
            BlockState below = level.getBlockState(candidate.below());
            if (level.getBlockState(candidate).isAir() && below.isFaceSturdy(level, candidate.below(), Direction.UP)) {
                return candidate;
            }
        }
        return null;
    }

    private void playFrostSound(ServerLevel level, Vec3 origin) {
        if (this.shotCounter % 4 == 1) {
            level.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55F, 1.85F);
        }
        if (this.shotCounter % 2 == 0) {
            level.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 0.7F, 1.45F);
        }
        if (this.shotCounter % 12 == 0) {
            level.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.35F, 2.0F);
        }
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6) {
            return point.distanceToSqr(from);
        }
        double t = point.subtract(from).dot(segment) / lengthSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        return point.distanceToSqr(from.add(segment.scale(t)));
    }
}
