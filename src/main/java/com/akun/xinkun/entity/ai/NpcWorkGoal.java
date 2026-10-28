package com.akun.xinkun.entity.ai;

import com.akun.xinkun.entity.GenshinNpcEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Daytime gathering, modelled on what a player actually does.
 *
 * <p>树木与矿脉是两种完全不同的工作。矿石仍然一块一块地挖，保留挖掘时间
 * 与挥镐动作；树木则先完整判定，再一次性放倒：只有确认是自然生成的一整棵
 * 树（树干、同种树叶、长在泥土上，且周围没有木板、楼梯、栅栏等建筑构件）
 * 才会动手，避免把玩家的木屋当成树拆掉。砍倒后原地补种同种树苗。</p>
 *
 * <p>开始工作是随机的：每几秒掷一次骰子，附近有树或露出地表的矿脉才会开工。
 * 未完成的工作会在战斗、夜晚或重载后保留，角色会回到同一处继续。</p>
 */
public class NpcWorkGoal extends Goal {
    private enum Mode {
        NONE,
        TREE,
        ORE
    }

    /** 一棵已经通过完整性校验的自然树。 */
    private record TreeJob(BlockPos base, List<BlockPos> logs, Item sapling, BlockState logState) {
    }

    private static final int CHECK_INTERVAL = 40;
    private static final int TREE_RADIUS = 18;
    private static final int ORE_RADIUS = 16;
    private static final int MAX_TREE_BLOCKS = 160;
    private static final int MAX_VEIN_BLOCKS = 128;
    private static final int MAX_TREE_HEIGHT = 32;
    /** 树冠至少要有这么多片同种树叶，避免把墙角的原木柱当成树。 */
    private static final int MIN_CANOPY_LEAVES = 8;
    /** 树干离角色多远还能一次放倒（水平距离的平方）。 */
    private static final double TREE_REACH_HORIZONTAL_SQR = 30.25;
    private static final int TREE_REACH_UP = 6;
    private static final double ORE_REACH_SQR = 30.25;
    private static final int APPROACH_TIMEOUT = 240;
    /** 与镐的 7 tick 挥击时长一致：下一次起手正好接在上一次收势，连续作业不跳帧。 */
    private static final int SWING_INTERVAL = 7;
    private static final int OBSTACLE_CHECK_INTERVAL = 8;

    private final GenshinNpcEntity npc;
    private Mode mode = Mode.NONE;
    private TreeJob tree;
    private final List<BlockPos> targets = new ArrayList<>();
    private Item tool = Items.AIR;
    private int breakTicks;
    private int breakNeeded;
    private int swingTicks;
    private int approachTicks;
    private int navCooldown;
    private int nextStartTick;

    public NpcWorkGoal(GenshinNpcEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 既不砍树也不挖矿的角色（纳西妲）完全不接这类工作。
        if (!this.npc.canChopTrees() && !this.npc.canMineOre()) {
            this.nextStartTick = CHECK_INTERVAL;
            return false;
        }
        // 工作权限由角色自己决定：哥伦比娅夜间不接新活，其他角色昼夜不限。
        if (this.npc.getTarget() != null) {
            this.nextStartTick = CHECK_INTERVAL;
            return false;
        }
        // 先接着做完没放倒的树或没挖完的矿脉。
        if (this.mode == Mode.TREE && this.tree != null) {
            if (this.isTreeStillValid(this.tree) && this.npc.ensureMainHand(Items.NETHERITE_AXE)) {
                this.approachTicks = 0;
                return true;
            }
            this.abandonJob();
            return false;
        }
        if (!this.targets.isEmpty() && this.mode == Mode.ORE) {
            if (this.npc.ensureMainHand(this.tool)) {
                this.approachTicks = 0;
                return true;
            }
            this.abandonJob();
            return false;
        }
        if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        }
        this.nextStartTick = CHECK_INTERVAL + this.npc.getRandom().nextInt(CHECK_INTERVAL);
        // Work is a dice roll, not a permanent state: she may walk past a tree,
        // but a resource-rich area still keeps her busy most of the time.
        if (this.npc.getRandom().nextInt(3) != 0 || !this.npc.hasBackpackSpace()) {
            return false;
        }
        if (this.findTreeJob()) {
            return true;
        }
        return this.findOreJob();
    }

    // ---------------------------------------------------------------- finding

    private boolean findTreeJob() {
        if (!this.npc.canChopTrees()) {
            return false;
        }
        BlockPos origin = this.npc.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-TREE_RADIUS, -6, -TREE_RADIUS),
                origin.offset(TREE_RADIUS, 12, TREE_RADIUS))) {
            if (this.isTreeBase(pos)) {
                candidates.add(pos.immutable());
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        candidates.sort(Comparator.comparingDouble(origin::distSqr));
        // 只仔细检查最近的若干棵，避免在大森林里每 tick 扫几十棵树。
        int checked = 0;
        for (BlockPos base : candidates) {
            if (++checked > 12) {
                break;
            }
            TreeJob job = this.buildTreeJob(base);
            if (job == null) {
                continue;
            }
            if (!this.npc.ensureMainHand(Items.NETHERITE_AXE)) {
                return false;
            }
            this.mode = Mode.TREE;
            this.tree = job;
            this.tool = Items.NETHERITE_AXE;
            this.targets.clear();
            this.resetProgress();
            return true;
        }
        return false;
    }

    /** 候选树根：自然泥土上的一根原木，上面还连着同种原木。 */
    private boolean isTreeBase(BlockPos pos) {
        BlockState state = this.npc.level().getBlockState(pos);
        String species = logSpecies(state);
        if (species == null) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.AXIS)
                && state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
            return false;
        }
        if (!isNaturalSoil(this.npc.level().getBlockState(pos.below()))) {
            return false;
        }
        return species.equals(logSpecies(this.npc.level().getBlockState(pos.above())));
    }

    /**
     * 完整校验一棵树。任何一项不满足都宁可放弃，也不能误伤建筑。
     */
    private TreeJob buildTreeJob(BlockPos base) {
        var level = this.npc.level();
        BlockState baseState = level.getBlockState(base);
        String species = logSpecies(baseState);
        if (species == null) {
            return null;
        }
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = base.immutable();
        seen.add(start);
        queue.add(start);
        int minY = base.getY();
        int maxY = base.getY();
        int minX = base.getX();
        int maxX = base.getX();
        int minZ = base.getZ();
        int maxZ = base.getZ();
        while (!queue.isEmpty() && logs.size() <= MAX_TREE_BLOCKS) {
            BlockPos pos = queue.removeFirst();
            BlockState state = level.getBlockState(pos);
            if (!species.equals(logSpecies(state))) {
                continue;
            }
            // 不跨越太远的横向距离，防止把隔壁另一棵树或整面木墙卷进来。
            if (Math.abs(pos.getX() - base.getX()) > 4 || Math.abs(pos.getZ() - base.getZ()) > 4
                    || Math.abs(pos.getY() - base.getY()) > MAX_TREE_HEIGHT) {
                continue;
            }
            logs.add(pos);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (seen.add(next)) {
                            queue.addLast(next);
                        }
                    }
                }
            }
        }
        if (logs.isEmpty() || logs.size() > MAX_TREE_BLOCKS) {
            return null;
        }
        int height = maxY - minY + 1;
        if (height < 4 || height > MAX_TREE_HEIGHT) {
            return null;
        }
        // 树干开头三格必须是一根连续的柱子（白桦、橡树、深色橡木都是如此）。
        for (int y = base.getY(); y <= base.getY() + 2; y++) {
            if (!hasLogAtLevel(logs, base, y)) {
                return null;
            }
        }
        // 整棵树不能有断层，否则可能只是两截装饰性原木。
        for (int y = minY; y <= maxY; y++) {
            if (!hasAnyLogAtLevel(logs, y)) {
                return null;
            }
        }
        // 树叶必须是同种树冠，而且长在树的上半段。
        int leaves = this.countCanopyLeaves(logs, species, minY + height / 2, maxY);
        if (leaves < MIN_CANOPY_LEAVES) {
            return null;
        }
        // 任何木板、楼梯、栅栏、玻璃之类的建筑构件出现在树身边，就整棵放弃。
        if (this.hasBuildingPartsNearby(logs)) {
            return null;
        }
        Item sapling = saplingFor(baseState);
        if (sapling == null) {
            return null;
        }
        logs.sort(Comparator.comparingInt(BlockPos::getY));
        return new TreeJob(start, List.copyOf(logs), sapling, baseState);
    }

    private static boolean hasLogAtLevel(List<BlockPos> logs, BlockPos base, int y) {
        for (BlockPos log : logs) {
            if (log.getY() == y
                    && Math.abs(log.getX() - base.getX()) <= 1
                    && Math.abs(log.getZ() - base.getZ()) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyLogAtLevel(List<BlockPos> logs, int y) {
        for (BlockPos log : logs) {
            if (log.getY() == y) {
                return true;
            }
        }
        return false;
    }

    private int countCanopyLeaves(List<BlockPos> logs, String species, int fromY, int toY) {
        // species 已带命名空间（minecraft:oak），这里不能再拼一次 minecraft:。
        String leafId = species + "_leaves";
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos log : logs) {
            minX = Math.min(minX, log.getX());
            minZ = Math.min(minZ, log.getZ());
            maxX = Math.max(maxX, log.getX());
            maxZ = Math.max(maxZ, log.getZ());
        }
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(minX - 6, fromY, minZ - 6),
                new BlockPos(maxX + 6, Math.min(toY + 3, fromY + MAX_TREE_HEIGHT), maxZ + 6))) {
            BlockState state = this.npc.level().getBlockState(pos);
            if (blockId(state).equals(leafId)) {
                count++;
                if (count >= MIN_CANOPY_LEAVES) {
                    return count;
                }
            }
        }
        return count;
    }

    private boolean hasBuildingPartsNearby(List<BlockPos> logs) {
        var level = this.npc.level();
        for (BlockPos log : logs) {
            for (BlockPos pos : BlockPos.betweenClosed(log.offset(-2, -2, -2), log.offset(2, 2, 2))) {
                if (isBuildingPart(level.getBlockState(pos))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 建筑构件判定。树干和树叶本身不算，岩石、泥土、花草也不算。 */
    private static boolean isBuildingPart(BlockState state) {
        if (state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return true;
        }
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS)
                || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCES)
                || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)) {
            return true;
        }
        String id = blockId(state);
        return id.endsWith("_glass") || id.endsWith("_glass_pane")
                || id.endsWith("_bricks") || id.endsWith("_brick")
                || id.endsWith("_sign") || id.endsWith("_wall_sign")
                || id.endsWith("_hanging_sign") || id.endsWith("_wall_hanging_sign")
                || id.endsWith("_carpet") || id.endsWith("_bed")
                || id.endsWith("_door") || id.endsWith("_trapdoor");
    }

    private static boolean isNaturalSoil(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.MUD) || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PODZOL);
    }

    /** 自然树只用 *_log；木板、去皮原木和建筑里的木头不算。 */
    private static String logSpecies(BlockState state) {
        if (!state.is(BlockTags.LOGS)) {
            return null;
        }
        String path = blockId(state);
        if (!path.endsWith("_log")) {
            return null;
        }
        String species = path.substring(0, path.length() - 4);
        return species.isEmpty() ? null : species;
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** 只砍确认过的整棵树，不和地形或建筑发生任何逐块交互。 */
    private void fellTree(TreeJob job) {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        this.npc.getNavigation().stop();
        this.npc.getLookControl().setLookAt(
                job.base().getX() + 0.5, job.base().getY() + 1.0, job.base().getZ() + 0.5);
        this.npc.ensureMainHand(Items.NETHERITE_AXE);
        this.npc.swingForAttack(InteractionHand.MAIN_HAND);
        SoundType sound = job.logState().getSoundType();
        for (BlockPos log : job.logs()) {
            if (level.getBlockState(log).is(BlockTags.LOGS)) {
                level.destroyBlock(log, true, this.npc, 512);
            }
        }
        level.playSound(null, job.base(), sound.getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.85F);
        this.collectTreeDrops(job);
        this.replant(job);
        this.abandonJob();
    }

    private void collectTreeDrops(TreeJob job) {
        AABB area = new AABB(job.base()).inflate(7.0, 36.0, 7.0);
        for (ItemEntity item : this.npc.level().getEntitiesOfClass(ItemEntity.class, area)) {
            if (item.isAlive()) {
                this.npc.pickUpItem(item);
            }
        }
    }

    /** 在原来的树根位置补种同种树苗。 */
    private void replant(TreeJob job) {
        if (!(job.sapling() instanceof BlockItem blockItem)) {
            return;
        }
        var level = this.npc.level();
        BlockPos pos = job.base();
        if (!level.getBlockState(pos).isAir()) {
            return;
        }
        BlockState seedling = blockItem.getBlock().defaultBlockState();
        if (!seedling.canSurvive(level, pos)) {
            return;
        }
        if (!this.npc.consumeItem(job.sapling(), 1)) {
            return;
        }
        level.setBlockAndUpdate(pos, seedling);
        level.playSound(null, pos, seedling.getSoundType().getPlaceSound(),
                SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    private boolean isTreeStillValid(TreeJob job) {
        BlockState base = this.npc.level().getBlockState(job.base());
        return logSpecies(base) != null && job.sapling() != null;
    }

    private boolean findOreJob() {
        if (!this.npc.canMineOre()) {
            return false;
        }
        BlockPos origin = this.npc.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-ORE_RADIUS, -8, -ORE_RADIUS),
                origin.offset(ORE_RADIUS, 8, ORE_RADIUS))) {
            BlockState state = this.npc.level().getBlockState(pos);
            if (!isOre(state) || this.underfoot(pos) || !this.isExposed(pos)) {
                continue;
            }
            double score = origin.distSqr(pos);
            if (score < bestScore) {
                bestScore = score;
                best = pos.immutable();
            }
        }
        if (best == null || !this.npc.ensureMainHand(Items.NETHERITE_PICKAXE)) {
            return false;
        }
        List<BlockPos> vein = this.collectVein(best);
        if (vein.isEmpty()) {
            return false;
        }
        this.mode = Mode.ORE;
        this.tree = null;
        this.tool = Items.NETHERITE_PICKAXE;
        this.targets.clear();
        this.targets.addAll(vein);
        this.resetProgress();
        return true;
    }

    /** A connected ore pocket of any mix of ore types, nearest blocks first. */
    private List<BlockPos> collectVein(BlockPos seed) {
        List<BlockPos> vein = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = seed.immutable();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && vein.size() < MAX_VEIN_BLOCKS) {
            BlockPos pos = queue.removeFirst();
            if (!isOre(this.npc.level().getBlockState(pos)) || this.underfoot(pos)) {
                continue;
            }
            vein.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        vein.sort((a, b) -> Double.compare(
                this.npc.distanceToSqr(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5),
                this.npc.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)));
        return vein;
    }

    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.ORES) && !state.hasBlockEntity();
    }

    private boolean isExposed(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = this.npc.level().getBlockState(pos.relative(direction));
            if (neighbor.isAir() || !neighbor.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Never mine the block she is standing on or the support under it. */
    private boolean underfoot(BlockPos pos) {
        int dx = Math.abs(pos.getX() - this.npc.getBlockX());
        int dz = Math.abs(pos.getZ() - this.npc.getBlockZ());
        return dx <= 1 && dz <= 1 && pos.getY() < this.npc.getBlockY();
    }

    private static Item saplingFor(BlockState log) {
        String id = blockId(log);
        return switch (id) {
            case "minecraft:oak_log" -> Items.OAK_SAPLING;
            case "minecraft:spruce_log" -> Items.SPRUCE_SAPLING;
            case "minecraft:birch_log" -> Items.BIRCH_SAPLING;
            case "minecraft:jungle_log" -> Items.JUNGLE_SAPLING;
            case "minecraft:acacia_log" -> Items.ACACIA_SAPLING;
            case "minecraft:dark_oak_log" -> Items.DARK_OAK_SAPLING;
            case "minecraft:cherry_log" -> Items.CHERRY_SAPLING;
            case "minecraft:pale_oak_log" -> Items.PALE_OAK_SAPLING;
            case "minecraft:mangrove_log" -> Items.MANGROVE_PROPAGULE;
            default -> null;
        };
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public boolean canContinueToUse() {
        // 天黑/失去工作权限就立即收工；未完成的矿脉保留在 targets 里。
        // 树要么已经放倒，要么原样留在那里，不会被拆掉一半。
        return this.mode != Mode.NONE && this.npc.getTarget() == null
                && (this.mode == Mode.TREE ? this.tree != null : !this.targets.isEmpty());
    }

    @Override
    public void start() {
        this.approachTicks = 0;
        this.navCooldown = 0;
        // 再开始前确认目标没有被别人改动过。
        if (this.mode == Mode.TREE && this.tree != null && !this.isTreeStillValid(this.tree)) {
            this.abandonJob();
        }
    }

    @Override
    public void stop() {
        // 放倒整棵树是原子操作，中途停止不会留下半棵树；矿脉进度则保留。
        this.npc.getNavigation().stop();
        // 正在挥的那一下要放完，否则手上的镐/斧会提前消失，挖矿姿势也会被跳过。
        this.npc.stowWorkToolSoon();
    }

    @Override
    public void tick() {
        if (this.mode == Mode.TREE) {
            this.tickTree();
            return;
        }
        this.tickOre();
    }

    private void tickTree() {
        TreeJob job = this.tree;
        if (job == null) {
            this.abandonJob();
            return;
        }
        if (!this.isTreeStillValid(job)) {
            this.abandonJob();
            return;
        }
        BlockPos base = job.base();
        if (this.treeInReach(base)) {
            this.approachTicks = 0;
            this.fellTree(job);
            return;
        }
        this.approachTicks++;
        if (this.npc.getNavigation().isDone() || this.npc.getNavigation().isStuck()) {
            this.navigate(base);
        }
        // 够不到就放弃这棵树，绝不清除挡路的方块，也就不会伤到建筑。
        if (this.approachTicks > APPROACH_TIMEOUT) {
            this.approachTicks = 0;
            this.abandonJob();
        }
    }

    private boolean treeInReach(BlockPos base) {
        double dx = base.getX() + 0.5 - this.npc.getX();
        double dz = base.getZ() + 0.5 - this.npc.getZ();
        int dy = base.getY() - this.npc.getBlockY();
        return dx * dx + dz * dz <= TREE_REACH_HORIZONTAL_SQR
                && dy >= -4 && dy <= TREE_REACH_UP;
    }

    private void tickOre() {
        if (this.targets.isEmpty()) {
            this.completeOreJob();
            return;
        }
        BlockPos target = this.targets.get(0);
        if (!this.validBlock(target)) {
            this.targets.remove(0);
            this.resetProgress();
            return;
        }
        if (!this.inReach(target)) {
            this.approachTicks++;
            // 树叶、泥土、沙砾等挡住视线或去路时先清掉，而不是放弃矿脉。
            if (this.approachTicks % OBSTACLE_CHECK_INTERVAL == 0) {
                this.clearObstacles(target);
            }
            // 目标在水下：以玩家游泳姿势直接游过去；否则正常寻路。
            if (this.npc.isInWater() && this.isWet(target)) {
                this.npc.swimToward(target.getX() + 0.5, target.getY() + 0.5,
                        target.getZ() + 0.5, 0.17);
            } else if (this.npc.getNavigation().isDone() || this.npc.getNavigation().isStuck()) {
                this.navigate(target);
            }
            if (this.approachTicks > APPROACH_TIMEOUT) {
                this.approachTicks = 0;
                // 不再丢弃目标：清障后直接到目标旁最近的可站立/可游泳格继续。
                this.clearObstacles(target);
                this.relocateNearTarget(target);
            }
            return;
        }
        this.approachTicks = 0;
        this.npc.getNavigation().stop();
        this.npc.getLookControl().setLookAt(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        // 水中作业保持玩家的游泳姿势，原版模型会自动渲染。
        if (this.npc.isInWater()) {
            this.npc.setSwimming(true);
            this.npc.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        } else {
            this.npc.stopSwimmingPose();
        }
        if (this.breakNeeded <= 0) {
            this.breakNeeded = miningTicks(this.npc.level().getBlockState(target));
        }
        this.swingTicks++;
        this.breakTicks++;
        if (this.swingTicks >= SWING_INTERVAL) {
            this.swingTicks = 0;
            this.npc.swingForAttack(InteractionHand.MAIN_HAND);
            SoundType sound = this.npc.level().getBlockState(target).getSoundType();
            this.npc.level().playSound(null, target, sound.getHitSound(), SoundSource.BLOCKS,
                    0.22F, 0.9F + this.npc.getRandom().nextFloat() * 0.2F);
        }
        if (this.breakTicks >= this.breakNeeded) {
            this.harvestOre(target);
        }
    }

    private void resetProgress() {
        this.breakTicks = 0;
        this.breakNeeded = 0;
        this.swingTicks = 0;
    }

    /** Vanilla-like block hardness, in ticks, for a netherite tool. */
    private static int miningTicks(BlockState state) {
        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness < 0.0F) {
            return 9999;
        }
        return Math.max(6, Math.min(48, Math.round(4.0F + hardness * 6.0F)));
    }

    private boolean validBlock(BlockPos pos) {
        return isOre(this.npc.level().getBlockState(pos));
    }

    private boolean inReach(BlockPos pos) {
        double dx = pos.getX() + 0.5 - this.npc.getX();
        double dz = pos.getZ() + 0.5 - this.npc.getZ();
        double horizontal = dx * dx + dz * dz;
        double ddy = pos.getY() + 0.5 - (this.npc.getY() + 1.0);
        return horizontal + ddy * ddy <= ORE_REACH_SQR;
    }

    private void navigate(BlockPos target) {
        if (this.navCooldown > 0) {
            this.navCooldown--;
            return;
        }
        this.navCooldown = 10;
        BlockPos spot = this.findApproachSpot(target);
        if (spot == null) {
            // Nothing standable nearby (encased vein): walk at the block
            // itself and let the pathfinder stop at the closest node.
            this.npc.getNavigation().moveTo(
                    target.getX() + 0.5, this.npc.getY(), target.getZ() + 0.5, 1.05);
            return;
        }
        this.npc.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.05);
    }

    /**
     * Block centres are not walkable destinations, so path to a free neighbour
     * of the vein instead. A neighbour can be free yet walled off from her,
     * which used to strand her 25 ticks away for the whole timeout, so the
     * closest few candidates are path-tested before one is chosen.
     */
    private BlockPos findApproachSpot(BlockPos target) {
        List<BlockPos> spots = new ArrayList<>();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos feet = target.offset(dx, dy, dz);
                    if (this.isStandable(feet)) {
                        spots.add(feet.immutable());
                    }
                }
            }
        }
        if (spots.isEmpty()) {
            return null;
        }
        BlockPos origin = this.npc.blockPosition();
        spots.sort((a, b) -> Double.compare(
                approachScore(origin, target, a), approachScore(origin, target, b)));
        int checked = 0;
        for (BlockPos feet : spots) {
            if (this.npc.getNavigation().createPath(feet, 1) != null) {
                return feet;
            }
            if (++checked >= 6) {
                break;
            }
        }
        return spots.get(0);
    }

    private static double approachScore(BlockPos origin, BlockPos target, BlockPos feet) {
        return origin.distSqr(feet) + Math.abs(feet.getY() - target.getY()) * 2.0;
    }

    /** 目标格本身或紧邻格有水，就按水下作业处理。 */
    private boolean isWet(BlockPos pos) {
        var level = this.npc.level();
        return !level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()
                || !level.getFluidState(pos.below()).isEmpty();
    }

    /**
     * 挖矿途中，树叶、泥土、沙砾等各种方块都可能挡住身子或视线。
     * 这里沿视线逐个清除，每次只破坏一个，动作节奏与挖掘一致。
     */
    private void clearObstacles(BlockPos target) {
        this.clearCell(this.npc.blockPosition());
        this.clearCell(this.npc.blockPosition().above());
        Vec3 from = this.npc.getEyePosition();
        Vec3 to = Vec3.atCenterOf(target);
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.01) {
            return;
        }
        Vec3 unit = delta.scale(1.0 / length);
        for (double travelled = 0.6; travelled < length; travelled += 0.4) {
            BlockPos pos = BlockPos.containing(from.add(unit.scale(travelled)));
            if (pos.equals(target) || pos.equals(this.npc.blockPosition())) {
                continue;
            }
            if (this.clearCell(pos)) {
                return;
            }
        }
    }

    private boolean clearCell(BlockPos pos) {
        var level = this.npc.level();
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(Blocks.BEDROCK) || state.hasBlockEntity()
                || state.getBlock().defaultDestroyTime() < 0.0F) {
            return false;
        }
        level.destroyBlock(pos, true, this.npc, 512);
        this.npc.swingForAttack(InteractionHand.MAIN_HAND);
        this.collectDrops(pos);
        return true;
    }

    /**
     * 寻路完全走不通时的兜底：把角色送到目标旁最近的空位（水下矿洞就是水里），
     * 之后继续用真实的游泳姿势与挖掘动作完成工作，而不是放弃留下烂尾。
     */
    private void relocateNearTarget(BlockPos target) {
        var level = this.npc.level();
        BlockPos origin = this.npc.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = target.offset(dx, dy, dz);
                    if (pos.equals(target) || !level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER)) {
                        continue;
                    }
                    if (!state.getCollisionShape(level, pos).isEmpty()) {
                        continue;
                    }
                    BlockState above = level.getBlockState(pos.above());
                    if (!above.getCollisionShape(level, pos.above()).isEmpty()
                            && above.getFluidState().isEmpty()) {
                        continue;
                    }
                    double score = origin.distSqr(pos)
                            + (state.getFluidState().is(FluidTags.WATER) ? 0.0 : 2.0);
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos.immutable();
                    }
                }
            }
        }
        if (best == null) {
            return;
        }
        this.npc.getNavigation().stop();
        this.npc.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        this.npc.setDeltaMovement(Vec3.ZERO);
        this.npc.fallDistance = 0.0F;
        this.approachTicks = 0;
    }

    private boolean isStandable(BlockPos feet) {
        var level = this.npc.level();
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.getFluidState().isEmpty()
                || !ground.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        BlockState at = level.getBlockState(feet);
        BlockState above = level.getBlockState(feet.above());
        if (at.getFluidState().is(FluidTags.LAVA) || above.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        // 脚和头可以在水里：水下矿脉就是要站在海床/河床上作业。
        return at.getCollisionShape(level, feet).isEmpty()
                && above.getCollisionShape(level, feet.above()).isEmpty();
    }

    // -------------------------------------------------------------- harvesting

    private void harvestOre(BlockPos pos) {
        BlockState state = this.npc.level().getBlockState(pos);
        if (state.isAir()) {
            this.targets.remove(pos);
            this.resetProgress();
            return;
        }
        BlockPos immutable = pos.immutable();
        // Real drops, not a silent delete: what the block yields must be
        // available to her, exactly like a player breaking it.
        this.npc.level().destroyBlock(immutable, true, this.npc, 512);
        this.npc.swingForAttack(InteractionHand.MAIN_HAND);
        this.targets.remove(immutable);
        this.resetProgress();
        this.collectDrops(immutable);
        if (this.targets.isEmpty()) {
            this.completeOreJob();
            return;
        }
        if (!this.inReach(this.targets.get(0))) {
            this.navigate(this.targets.get(0));
        }
    }

    private void collectDrops(BlockPos pos) {
        AABB area = new AABB(pos).inflate(2.5);
        for (ItemEntity item : this.npc.level().getEntitiesOfClass(ItemEntity.class, area)) {
            if (item.isAlive()) {
                this.npc.pickUpItem(item);
            }
        }
    }

    private void completeOreJob() {
        this.abandonJob();
    }

    private void abandonJob() {
        this.mode = Mode.NONE;
        this.tree = null;
        this.targets.clear();
        this.tool = Items.AIR;
        this.resetProgress();
        this.navCooldown = 0;
        this.nextStartTick = CHECK_INTERVAL;
        // 伐木与最后一块矿的挥击刚触发，工具要留在手上放完整套动作再收。
        this.npc.stowWorkToolSoon();
    }
}
