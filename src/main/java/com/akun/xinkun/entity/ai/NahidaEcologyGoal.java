package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.NahidaEntity;
import com.akun.xinkun.particle.GlowDustOptions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 纳西妲的草木巡游。
 *
 * <p>她始终以自己为圆心，把半径 16 格内的一切死寂地貌化成草原：沙、红沙、
 * 砂岩、砂砾、陶瓦、灵魂沙、玄武岩、末地石都会被换成草方块与泥土，枯木、
 * 枯草和仙人掌会被清走，随后补上花草、树苗并把树苗催熟。巡游途中她不会站定，
 * 而是一路走一路开花，让绿色沿着她的脚步向前蔓延。</p>
 *
 * <p>双手举起的姿态、绿色叶尘与骨粉的催熟粒子都由这里驱动；她还会定期
 * 给范围内的作物、树苗和草施肥。</p>
 */
public class NahidaEcologyGoal extends Goal {
    /** 绿化半径：以纳西妲自身为圆心，16 格内的圆形范围。 */
    public static final int AURA_RADIUS = 16;
    /** 只要这个半径里还有活着的怪物，就先停下绿化去战斗。 */
    private static final double HOSTILE_RADIUS = AURA_RADIUS + 4.0;
    private static final int AURA_RADIUS_SQ = AURA_RADIUS * AURA_RADIUS;
    private static final int GRID_SIZE = AURA_RADIUS * 2 + 1;
    private static final int GRID_CELLS = GRID_SIZE * GRID_SIZE;
    /** 每 tick 处理的列数：约十余 tick 扫完一整个圆，走一路绿一路。 */
    private static final int COLUMN_BUDGET = 56;
    private static final int FERTILIZE_BUDGET = 14;
    private static final int REPATH_TICKS = 40;
    private static final int VOICE_COOLDOWN = 1100;
    private static final int LEAF_COLOR = 0x5FBF6A;
    /** 夜里也清楚的草木光尘：草绿主色加一层浅绿高光。 */
    private static final GlowDustOptions GLOW_GREEN = new GlowDustOptions(LEAF_COLOR, 1.7F);
    private static final GlowDustOptions GLOW_SPROUT = new GlowDustOptions(0xA8F0B0, 1.2F);
    /** 从地面以上的最高点往下最多找多少格：足以穿过整棵树冠。 */
    private static final int MAX_GROUND_DEPTH = 28;
    private static final int MAX_SAND_DEPTH = 8;
    private static final int MAX_BIOME_CHUNKS_PER_PASS = 2;

    private final NahidaEntity npc;
    private BlockPos scanCenter = BlockPos.ZERO;
    private int scanIndex = GRID_CELLS;
    private int fertilizeIndex;
    private int walkCooldown;
    private int voiceCooldown;
    private int particleTicks;
    private int biomeTicks;
    private int stuckTicks;
    /** 每 tick 在普通草地上最多补种几株，避免一格接一格铺成花毯。 */
    private int grassPlantsThisTick;
    private boolean initialized;
    private Vec3 lastPos = Vec3.ZERO;
    private Holder<Biome> plains;
    private Holder<Biome> forest;
    private Holder<Biome> flowerForest;
    private Holder<Biome> jungle;
    private long worldSeed;

    public NahidaEcologyGoal(NahidaEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public boolean canUse() {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return false;
        }
        // 附近还有怪物时先打，不许一边挨打一边种花。
        return this.npc.getTarget() == null
                && !this.npc.isPassenger()
                && !this.hasNearbyHostile(level);
    }

    @Override
    public boolean canContinueToUse() {
        return this.npc.getTarget() == null
                && !this.npc.isPassenger()
                && this.npc.level() instanceof ServerLevel level
                && !this.hasNearbyHostile(level);
    }

    /** 周围一圈里是否还有会开战的敌对生物。 */
    private boolean hasNearbyHostile(ServerLevel level) {
        return !level.getEntitiesOfClass(Mob.class,
                this.npc.getBoundingBox().inflate(HOSTILE_RADIUS),
                mob -> mob.isAlive() && mob instanceof Enemy
                        // 苦力怕不在她的索敌范围内；把它算进来只会让她一直停工。
                        && !(mob instanceof net.minecraft.world.entity.monster.Creeper)).isEmpty();
    }

    @Override
    public void start() {
        this.npc.setBlooming(true);
        this.walkCooldown = 0;
        this.stuckTicks = 0;
        this.lastPos = this.npc.position();
        // 与玩家打招呼或短暂战斗会让这个目标停下再重启。扫描进度和开场语音
        // 只在第一次启动时初始化，避免每次重启都从圆心重扫、重复吟唱。
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        this.scanCenter = this.npc.blockPosition();
        this.scanIndex = 0;
        this.fertilizeIndex = 0;
        this.voiceCooldown = 0;
        this.particleTicks = 0;
        this.biomeTicks = 0;
        if (this.npc.level() instanceof ServerLevel level) {
            level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                    SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.NEUTRAL, 0.8F, 0.75F);
            this.sendAuraBurst(level);
        }
        if (!this.npc.isSpeaking()) {
            this.npc.speak(com.akun.xinkun.voice.VoiceContext.ECOLOGY);
        }
        this.voiceCooldown = VOICE_COOLDOWN;
    }

    @Override
    public void stop() {
        this.npc.setBlooming(false);
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        this.npc.setBlooming(true);
        this.resolveBiomes(level);
        this.grassPlantsThisTick = 0;

        this.tickMovement(level);
        this.tickColumns(level);
        this.tickFertilize(level);
        this.tickParticles(level);
        this.tickBiome(level);

        if (this.voiceCooldown > 0) {
            this.voiceCooldown--;
        } else if (this.npc.getRandom().nextInt(160) == 0) {
            this.npc.speak(com.akun.xinkun.voice.VoiceContext.ECOLOGY);
            this.voiceCooldown = VOICE_COOLDOWN + this.npc.getRandom().nextInt(600);
        }
    }

    // -------------------------------------------------------------- movement

    private void tickMovement(ServerLevel level) {
        if (--this.walkCooldown <= 0 || this.npc.getNavigation().isDone()) {
            boolean planned = this.chooseWalkTarget(level);
            this.walkCooldown = planned
                    ? 24 + this.npc.getRandom().nextInt(24)
                    : 10;
        }
        // 独立的“原地不动”看门狗：不管导航自认为有没有任务，只要半秒内
        // 几乎没挪窝就开始挣扎，两秒还不动就直接闪到旁边能站的地方。
        if (this.npc.tickCount % 10 == 0) {
            double moved = this.npc.position().distanceToSqr(this.lastPos);
            this.lastPos = this.npc.position();
            if (moved < 0.04) {
                this.stuckTicks += 10;
                if (this.stuckTicks >= 60) {
                    this.stuckTicks = 0;
                    boolean escaped = this.escape(level);
                    if (!escaped) {
                        this.npc.getJumpControl().jump();
                    }
                    this.walkCooldown = 0;
                } else if (this.stuckTicks >= 30) {
                    this.npc.getJumpControl().jump();
                    this.npc.getNavigation().stop();
                    this.chooseWalkTarget(level);
                }
            } else {
                this.stuckTicks = 0;
            }
        }
    }

    /** 一路走一路绿：在荒地就继续往荒地深处走，在草地就往荒地找。 */
    private boolean chooseWalkTarget(ServerLevel level) {
        BlockPos origin = this.npc.blockPosition();
        boolean hereBarren = this.isBarren(level.getBiome(origin));
        var random = this.npc.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 8 + random.nextInt(18);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, origin.getY(), z))) {
                continue;
            }
            // 忽略树叶，避免她一路踩着树冠走；落到真正的落脚面上。
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos target = new BlockPos(x, y, z);
            if (!this.isWalkable(level, target)) {
                continue;
            }
            // 身处沙漠时优先继续深入沙漠，而不是走回绿洲原地打转。
            if (hereBarren && !this.isBarren(level.getBiome(target))) {
                continue;
            }
            // 只有真正规划出路径才算选好；到不了的地方继续换一个。
            if (this.npc.getNavigation().moveTo(x + 0.5, y, z + 0.5, 1.05)) {
                return true;
            }
        }
        // 附近没有合适的落脚点：朝最近的荒地迈一小步。
        var found = level.findClosestBiome3d(this::isBarren, origin, 24, 64, 128);
        if (found != null) {
            BlockPos barren = found.getFirst();
            Vec3 from = this.npc.position();
            Vec3 direction = Vec3.atCenterOf(barren).subtract(from);
            double length = direction.length();
            if (length > 1.0) {
                Vec3 waypoint = from.add(direction.scale(Math.min(24.0, length) / length));
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Mth.floor(waypoint.x), Mth.floor(waypoint.z));
                if (this.npc.getNavigation().moveTo(waypoint.x, y, waypoint.z, 1.05)) {
                    return true;
                }
            }
        }
        // 兜底：朝任意一个没被挡住的方向迈一小步，绝不把目标设成脚下。
        // 树太密、寻路彻底失败时不再原地重算，直接交给移动控制走过去。
        for (int attempt = 0; attempt < 16; attempt++) {
            Direction direction = Direction.from2DDataValue(random.nextInt(4));
            BlockPos step = origin.relative(direction, 1 + random.nextInt(4));
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    step.getX(), step.getZ());
            BlockPos target = new BlockPos(step.getX(), y, step.getZ());
            if (!this.isWalkable(level, target)) {
                continue;
            }
            if (this.npc.getNavigation().moveTo(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9)) {
                return true;
            }
            this.npc.getMoveControl().setWantedPosition(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9);
            return true;
        }
        this.escape(level);
        return false;
    }

    /** 被围死时的最后手段：挪到六格内最近的能站立处。 */
    private boolean escape(ServerLevel level) {
        BlockPos origin = this.npc.blockPosition();
        var random = this.npc.getRandom();
        for (int attempt = 0; attempt < 32; attempt++) {
            int dx = random.nextInt(13) - 6;
            int dz = random.nextInt(13) - 6;
            if (dx * dx + dz * dz < 4) {
                continue;
            }
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos target = new BlockPos(x, y, z);
            if (!this.isWalkable(level, target)) {
                continue;
            }
            this.sendGreenBurst(level, this.npc.getX(), this.npc.getY() + 1.0, this.npc.getZ(), 6);
            this.npc.teleportTo(x + 0.5, y, z + 0.5);
            this.sendGreenBurst(level, x + 0.5, y + 1.0, z + 0.5, 8);
            this.npc.getNavigation().stop();
            this.walkCooldown = 0;
            return true;
        }
        return false;
    }

    /**
     * 能站立的一格：脚和头都没有碰撞体积（花、草丛可以穿过），
     * 脚下是实心地面。只把真正挡路的方块当作障碍。
     */
    private boolean isWalkable(ServerLevel level, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        if (!feet.getCollisionShape(level, pos).isEmpty() || feet.liquid()) {
            return false;
        }
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (ground.isAir() || ground.liquid()) {
            return false;
        }
        if (ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS)) {
            return false;
        }
        return ground.isSolid();
    }

    // --------------------------------------------------------------- columns

    private void tickColumns(ServerLevel level) {
        if (this.scanIndex >= GRID_CELLS
                || this.scanCenter.distSqr(this.npc.blockPosition()) > 32 * 32) {
            // 扫描窗口跟着她走：每一圈都以她当前站的位置为圆心重新铺开。
            this.scanCenter = this.npc.blockPosition();
            this.scanIndex = 0;
        }
        int processed = 0;
        while (processed < COLUMN_BUDGET && this.scanIndex < GRID_CELLS) {
            int index = this.scanIndex++;
            int dx = index % GRID_SIZE - AURA_RADIUS;
            int dz = index / GRID_SIZE - AURA_RADIUS;
            if (dx * dx + dz * dz > AURA_RADIUS_SQ) {
                continue;
            }
            processed++;
            this.convertColumn(level,
                    this.scanCenter.getX() + dx, this.scanCenter.getZ() + dz);
        }
    }

    /**
     * 处理一列：向下找到真正的土层，把连续的沙土、砂石、陶瓦等换成草方块和
     * 泥土。植物不会被误删，只会检查它们脚下的土地；建筑方块不会被当成地形。
     *
     * <p>扫描时跳过树叶和原木：树冠、甚至伸到半空的粗树枝底下，沙地同样能
     * 被翻成草地；而木板、石砖这类玩家搭建的方块会立刻终止整列。</p>
     */
    private boolean convertColumn(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (top <= level.getMinY()) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        boolean changed = false;
        boolean hadDryPlant = false;

        for (int step = 0; step < MAX_GROUND_DEPTH && pos.getY() > level.getMinY(); step++) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                pos.move(0, -1, 0);
                continue;
            }
            if (state.liquid()) {
                return changed;
            }
            if (this.isDryPlant(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                hadDryPlant = true;
                changed = true;
                pos.move(0, -1, 0);
                continue;
            }
            if (this.isSoftPlant(state)) {
                // 花草、树苗、作物保持原样，只继续往下检查它们脚下的土地。
                pos.move(0, -1, 0);
                continue;
            }
            break;
        }

        int groundY = pos.getY();
        BlockState surface = level.getBlockState(pos);
        if (groundY <= level.getMinY()) {
            return changed;
        }
        if (!this.isBarrenBlock(surface)) {
            // 已经是草地、泥土、苔藓这类能种东西的地面：不必换土，
            // 但要在空草地上补种花草和树苗，让普通的绿色原野也热闹起来。
            if (this.isPlantableGround(surface)
                    && this.grassPlantsThisTick < 3
                    && this.npc.getRandom().nextInt(4) == 0) {
                BlockPos above = new BlockPos(x, groundY + 1, z);
                if (this.clearingReplaceablePlant(level, above)
                        && this.plantAt(level, above, true)) {
                    this.grassPlantsThisTick++;
                    changed = true;
                }
            }
            return changed;
        }

        boolean first = true;
        for (int depth = 0; depth < MAX_SAND_DEPTH && pos.getY() > level.getMinY(); depth++) {
            BlockState layer = level.getBlockState(pos);
            if (!this.isBarrenBlock(layer)) {
                break;
            }
            // 只改方块、不触发邻居更新：一整个圆同时换土时不会连锁刷屏。
            level.setBlock(pos, first
                    ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            first = false;
            changed = true;
            pos.move(0, -1, 0);
        }
        int plantChance = hadDryPlant ? 3 : 9;
        if (this.npc.getRandom().nextInt(100) < plantChance) {
            this.plantAt(level, new BlockPos(x, groundY + 1, z), true);
        }
        if (changed && this.npc.getRandom().nextInt(3) == 0) {
            this.sendGreenBurst(level, x + 0.5, groundY + 1.1, z + 0.5, 2);
        }
        return changed;
    }

    // -------------------------------------------------------------- planting

    private void tickFertilize(ServerLevel level) {
        this.fertilizeIndex = (this.fertilizeIndex + FERTILIZE_BUDGET) % GRID_CELLS;
        int grown = 0;
        int checked = 0;
        int index = this.fertilizeIndex;
        while (checked < FERTILIZE_BUDGET && grown < 3) {
            int dx = index % GRID_SIZE - AURA_RADIUS;
            int dz = index / GRID_SIZE - AURA_RADIUS;
            index = (index + 1) % GRID_CELLS;
            checked++;
            if (dx * dx + dz * dz > AURA_RADIUS_SQ) {
                continue;
            }
            int x = this.npc.blockPosition().getX() + dx;
            int z = this.npc.blockPosition().getZ() + dz;
            if (!level.isLoaded(new BlockPos(x, this.npc.blockPosition().getY(), z))) {
                continue;
            }
            // 用不考虑树叶与花草的高度图：直接落在作物、树苗所在的那一格，
            // 避免树冠把高度图抬高后永远检查不到下面的作物。
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, surface, z);
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.SAPLINGS) && !state.is(BlockTags.CROPS)
                    && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.FERN)
                    && !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.GLOW_LICHEN)) {
                continue;
            }
            if (this.grow(level, pos)) {
                grown++;
            }
        }
        // 一边巡游一边随手撒花种树，让草地也持续变得热闹。
        if (this.npc.tickCount % 4 == 0) {
            var random = this.npc.getRandom();
            BlockPos origin = this.npc.blockPosition();
            for (int attempt = 0; attempt < 24; attempt++) {
                int x = origin.getX() + random.nextInt(AURA_RADIUS * 2 + 1) - AURA_RADIUS;
                int z = origin.getZ() + random.nextInt(AURA_RADIUS * 2 + 1) - AURA_RADIUS;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (this.isOpenGrass(level, pos) && this.plantAt(level, pos, true)) {
                    break;
                }
            }
        }
    }

    private boolean grow(ServerLevel level, BlockPos pos) {
        BlockState before = level.getBlockState(pos);
        if (!BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
            return false;
        }
        BlockState after = level.getBlockState(pos);
        if (after != before) {
            this.sendGrowthBurst(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.35F, 1.25F);
        }
        return true;
    }

    /** 可以直接播种的地面：草、泥土、苔藓、灰化土与缠根泥土。 */
    private boolean isPlantableGround(BlockState ground) {
        return ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT)
                || ground.is(Blocks.MOSS_BLOCK) || ground.is(Blocks.PODZOL)
                || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.ROOTED_DIRT);
    }

    /**
     * 清理一格杂草，为新的花或树苗腾位置。只清草丛、蕨、枯灌木这类
     * 可替换植物；已经开好的花不动，玩家放的方块也不动。
     */
    private boolean clearingReplaceablePlant(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return true;
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.SNOW)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }

    /** 在给定的空格里种一株植物；allowTree 为真时可能种树苗并当场催熟。 */
    private boolean plantAt(ServerLevel level, BlockPos pos, boolean allowTree) {
        if (!this.clearingReplaceablePlant(level, pos)) {
            return false;
        }
        BlockState ground = level.getBlockState(pos.below());
        if (!ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.DIRT)
                && !ground.is(Blocks.MOSS_BLOCK) && !ground.is(Blocks.PODZOL)
                && !ground.is(Blocks.COARSE_DIRT) && !ground.is(Blocks.ROOTED_DIRT)) {
            return false;
        }
        var random = this.npc.getRandom();
        Block plant;
        boolean tree = false;
        double roll = random.nextDouble();
        if (allowTree && roll < 0.22 && this.canPlaceTree(level, pos)) {
            plant = switch (random.nextInt(8)) {
                case 0 -> Blocks.OAK_SAPLING;
                case 1 -> Blocks.BIRCH_SAPLING;
                case 2 -> Blocks.CHERRY_SAPLING;
                case 3 -> Blocks.JUNGLE_SAPLING;
                case 4 -> Blocks.ACACIA_SAPLING;
                case 5 -> Blocks.SPRUCE_SAPLING;
                case 6 -> Blocks.FLOWERING_AZALEA;
                default -> Blocks.AZALEA;
            };
            tree = true;
        } else if (roll < 0.80) {
            plant = switch (random.nextInt(18)) {
                case 0 -> Blocks.DANDELION;
                case 1 -> Blocks.POPPY;
                case 2 -> Blocks.BLUE_ORCHID;
                case 3 -> Blocks.ALLIUM;
                case 4 -> Blocks.AZURE_BLUET;
                case 5 -> Blocks.RED_TULIP;
                case 6 -> Blocks.ORANGE_TULIP;
                case 7 -> Blocks.WHITE_TULIP;
                case 8 -> Blocks.PINK_TULIP;
                case 9 -> Blocks.OXEYE_DAISY;
                case 10 -> Blocks.CORNFLOWER;
                case 11 -> Blocks.LILY_OF_THE_VALLEY;
                case 12 -> Blocks.TORCHFLOWER;
                case 13 -> Blocks.PINK_PETALS;
                case 14 -> Blocks.SUNFLOWER;
                case 15 -> Blocks.LILAC;
                case 16 -> Blocks.ROSE_BUSH;
                default -> Blocks.PEONY;
            };
        } else {
            plant = random.nextBoolean() ? Blocks.SHORT_GRASS : Blocks.FERN;
        }
        if (!this.placePlant(level, pos, plant)) {
            return false;
        }
        level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_PLACE,
                SoundSource.BLOCKS, 0.32F, 1.25F + random.nextFloat() * 0.3F);
        if (tree) {
            // 种下就催熟：反复使用骨粉，直到树苗长成或无法继续。
            for (int attempt = 0; attempt < 5; attempt++) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(BlockTags.SAPLINGS)) {
                    break;
                }
                BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos);
            }
            BlockState grown = level.getBlockState(pos);
            if (grown.is(BlockTags.LOGS) || grown.is(BlockTags.LEAVES)) {
                this.sendGrowthBurst(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
            }
        }
        return true;
    }

    /** 种下植物本体；向日葵、丁香、玫瑰丛、牡丹要同时补齐上半株。 */
    private boolean placePlant(ServerLevel level, BlockPos pos, Block plant) {
        BlockState seedling = plant.defaultBlockState();
        if (plant == Blocks.SUNFLOWER || plant == Blocks.LILAC
                || plant == Blocks.ROSE_BUSH || plant == Blocks.PEONY) {
            if (!level.getBlockState(pos.above()).isAir()) {
                return false;
            }
            BlockState lower = seedling.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
            if (!lower.canSurvive(level, pos)) {
                return false;
            }
            level.setBlock(pos, lower, Block.UPDATE_CLIENTS);
            level.setBlock(pos.above(),
                    seedling.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
            return true;
        }
        if (!seedling.canSurvive(level, pos)) {
            return false;
        }
        level.setBlock(pos, seedling, Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * 树只种在她六格之外，且周围五格内不能已有树干。否则一路催熟的树
     * 会很快长成一片密不透风的林子，把她自己困在中间。
     */
    private boolean canPlaceTree(ServerLevel level, BlockPos pos) {
        if (this.npc.blockPosition().distSqr(pos) < 36.0) {
            return false;
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    if (level.getBlockState(pos.offset(dx, dy, dz)).is(BlockTags.LOGS)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isOpenGrass(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        // 空草地常常铺着一层矮草或蕨；这些可以清掉后补种，不能算“已被占用”。
        if (!here.isAir() && !here.is(Blocks.SHORT_GRASS) && !here.is(Blocks.FERN)
                && !here.is(Blocks.TALL_GRASS) && !here.is(Blocks.LARGE_FERN)
                && !here.is(Blocks.DEAD_BUSH)) {
            return false;
        }
        if (!this.isPlantableGround(level.getBlockState(pos.below()))) {
            return false;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockState state = level.getBlockState(pos.offset(dx, dy, dz));
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------- particles

    private void tickParticles(ServerLevel level) {
        if (--this.particleTicks > 0) {
            return;
        }
        this.particleTicks = 2;
        var random = this.npc.getRandom();
        BlockPos center = this.npc.blockPosition();
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 3.0 + random.nextDouble() * (AURA_RADIUS - 4.0);
            double x = this.npc.getX() + Math.cos(angle) * radius;
            double z = this.npc.getZ() + Math.sin(angle) * radius;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));
            level.sendParticles(GLOW_GREEN,
                    x, y + 0.7, z, 1, 0.12, 0.18, 0.12, 0.01);
        }
        // 她身边持续有绿色光点，像草木从掌心落下。
        level.sendParticles(GLOW_GREEN,
                this.npc.getX(), this.npc.getY() + 1.9, this.npc.getZ(),
                6, 0.7, 0.3, 0.7, 0.02);
        level.sendParticles(GLOW_SPROUT,
                this.npc.getX(), this.npc.getY() + 1.6, this.npc.getZ(),
                3, 0.5, 0.4, 0.5, 0.01);
        if (this.npc.tickCount % 6 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radius = random.nextDouble() * AURA_RADIUS;
                double x = this.npc.getX() + Math.cos(angle) * radius;
                double z = this.npc.getZ() + Math.sin(angle) * radius;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.4, z,
                        2, 0.25, 0.25, 0.25, 0.0);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    this.npc.getX(), this.npc.getY() + 2.1, this.npc.getZ(),
                    3, 0.45, 0.2, 0.45, 0.0);
        }
        if (this.npc.tickCount % 40 == 0) {
            this.sendAuraBurst(level);
        }
    }

    private void sendAuraBurst(ServerLevel level) {
        for (int i = 0; i < 64; i++) {
            double angle = Math.PI * 2.0 * i / 64.0;
            double x = this.npc.getX() + Math.cos(angle) * AURA_RADIUS;
            double z = this.npc.getZ() + Math.sin(angle) * AURA_RADIUS;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));
            level.sendParticles(GLOW_GREEN,
                    x, y + 0.5, z, 1, 0.1, 0.15, 0.1, 0.01);
        }
    }

    private void sendGreenBurst(ServerLevel level, double x, double y, double z, int count) {
        level.sendParticles(GLOW_GREEN,
                x, y, z, count, 0.25, 0.2, 0.25, 0.01);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.3, 0.2, 0.3, 0.0);
    }

    private void sendGrowthBurst(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 8, 0.35, 0.3, 0.35, 0.0);
        level.sendParticles(GLOW_GREEN,
                x, y + 0.3, z, 3, 0.25, 0.25, 0.25, 0.01);
    }

    // ----------------------------------------------------------------- biome

    private void tickBiome(ServerLevel level) {
        if (++this.biomeTicks < 30) {
            return;
        }
        this.biomeTicks = 0;
        int centerChunkX = this.npc.blockPosition().getX() >> 4;
        int centerChunkZ = this.npc.blockPosition().getZ() >> 4;
        List<LevelChunk> touched = new ArrayList<>();
        int repainted = 0;
        for (int cx = centerChunkX - 1; cx <= centerChunkX + 1; cx++) {
            for (int cz = centerChunkZ - 1; cz <= centerChunkZ + 1; cz++) {
                if (repainted >= MAX_BIOME_CHUNKS_PER_PASS) {
                    break;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                if (this.repaintChunk(level, chunk)) {
                    touched.add(chunk);
                    repainted++;
                }
            }
        }
        if (!touched.isEmpty()) {
            level.getChunkSource().chunkMap.resendBiomesForChunks(new ArrayList<>(touched));
        }
    }

    private boolean repaintChunk(ServerLevel level, LevelChunk chunk) {
        int minQx = chunk.getPos().getMinBlockX() >> 2;
        int minQz = chunk.getPos().getMinBlockZ() >> 2;
        int minQy = level.getMinY() >> 2;
        int maxQy = (level.getMaxY() - 1) >> 2;
        int centerX = this.npc.blockPosition().getX();
        int centerZ = this.npc.blockPosition().getZ();
        Map<Integer, Holder<Biome>> snapshot = new HashMap<>();
        boolean anyBarren = false;
        for (int qy = minQy; qy <= maxQy; qy++) {
            for (int lx = 0; lx < 4; lx++) {
                for (int lz = 0; lz < 4; lz++) {
                    Holder<Biome> current = chunk.getNoiseBiome(minQx + lx, qy, minQz + lz);
                    snapshot.put(((qy - minQy) << 4) | (lx << 2) | lz, current);
                    if (this.isBarren(current)) {
                        anyBarren = true;
                    }
                }
            }
        }
        if (!anyBarren) {
            return false;
        }
        chunk.fillBiomesFromNoise((qx, qy, qz) -> {
            int lx = qx - minQx;
            int lz = qz - minQz;
            int index = ((qy - minQy) << 4) | (lx << 2) | lz;
            Holder<Biome> current = snapshot.get(index);
            if (current == null) {
                return level.getUncachedNoiseBiome(qx, qy, qz);
            }
            if (!this.isBarren(current)) {
                return current;
            }
            int bx = (qx << 2) + 2;
            int bz = (qz << 2) + 2;
            double dx = bx - centerX;
            double dz = bz - centerZ;
            if (dx * dx + dz * dz > AURA_RADIUS_SQ) {
                return current;
            }
            return this.targetBiome(bx, bz);
        });
        chunk.markUnsaved();
        return true;
    }

    private Holder<Biome> targetBiome(int x, int z) {
        double n = valueNoise(this.seed(), x / 56.0, z / 56.0);
        if (n < 0.30) {
            return this.plains;
        }
        if (n < 0.55) {
            return this.forest;
        }
        if (n < 0.78) {
            return this.flowerForest;
        }
        return this.jungle;
    }

    private long seed() {
        return this.worldSeed ^ 0x5EED5EEDL;
    }

    private void resolveBiomes(ServerLevel level) {
        if (this.plains != null) {
            return;
        }
        this.worldSeed = level.getSeed();
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        this.plains = registry.getOrThrow(Biomes.PLAINS);
        this.forest = registry.getOrThrow(Biomes.FOREST);
        this.flowerForest = registry.getOrThrow(Biomes.FLOWER_FOREST);
        this.jungle = registry.getOrThrow(Biomes.JUNGLE);
    }

    // ---------------------------------------------------------------- blocks

    private boolean isDryPlant(BlockState state) {
        return state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SHORT_DRY_GRASS)
                || state.is(Blocks.TALL_DRY_GRASS)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CHORUS_PLANT)
                || state.is(Blocks.CHORUS_FLOWER);
    }

    /** 花草、树苗这类柔软植物：不删除，只跳过它们继续检查脚下的土。 */
    private boolean isSoftPlant(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.VINE)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.SNOW)
                || state.is(BlockTags.REPLACEABLE);
    }

    /**
     * 只认自然生成的死寂地形。工作台、楼梯、台阶、砖块等建筑构件一律不碰，
     * 避免把沙岩神殿或玩家的沙屋当成沙丘改掉。
     */
    private boolean isBarrenBlock(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.SUSPICIOUS_SAND || block == Blocks.SUSPICIOUS_GRAVEL) {
            return false;
        }
        if (state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)) {
            return true;
        }
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.END_STONE) || state.is(Blocks.BASALT)
                || state.is(Blocks.POLISHED_BASALT) || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.BLACKSTONE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.NETHERRACK) || state.is(Blocks.TUFF)) {
            return true;
        }
        return block == Blocks.SANDSTONE || block == Blocks.RED_SANDSTONE;
    }

    private boolean isBarren(Holder<Biome> biome) {
        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        if (key.isEmpty()) {
            return false;
        }
        String path = key.get().identifier().getPath();
        return path.contains("desert") || path.contains("badlands") || path.contains("mesa")
                || path.contains("wasteland") || path.contains("dead")
                || path.contains("soul_sand") || path.contains("basalt_deltas")
                || path.contains("nether_wastes")
                || path.contains("deep_dark") || path.contains("dripstone")
                || path.contains("eroded") || path.contains("stony_shore")
                || path.equals("the_end") || path.startsWith("end_")
                || path.contains("end_barrens") || path.contains("end_highlands")
                || path.contains("end_midlands") || path.contains("small_end_islands");
    }

    // ----------------------------------------------------------------- noise

    private static double valueNoise(long seed, double x, double z) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fz = fz * fz * (3.0 - 2.0 * fz);
        double v00 = hash01(seed, x0, z0);
        double v10 = hash01(seed, x0 + 1, z0);
        double v01 = hash01(seed, x0, z0 + 1);
        double v11 = hash01(seed, x0 + 1, z0 + 1);
        double a = v00 + (v10 - v00) * fx;
        double b = v01 + (v11 - v01) * fx;
        return a + (b - a) * fz;
    }

    private static double hash01(long seed, int x, int z) {
        long h = seed + x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
