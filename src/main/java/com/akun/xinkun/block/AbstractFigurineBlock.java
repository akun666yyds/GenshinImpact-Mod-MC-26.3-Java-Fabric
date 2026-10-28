package com.akun.xinkun.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 角色手办的公共方块行为：没有碰撞、没有原版模型、只留一个瞄准轮廓。
 *
 * <p>外观由客户端方块实体渲染器读取随模组内置的 GLB 网格绘制。
 * 朝向使用原版的 16 档旋转（每档 22.5°），放置时正面朝向玩家，
 * 因此可以摆出任意方向，而不再局限于四个正方向。</p>
 */
public abstract class AbstractFigurineBlock extends BaseEntityBlock {
    /**
     * 16 档水平朝向，0 档正面朝南。旋转角度就是档位乘以 22.5°，
     * 与原版告示牌使用同一套属性，资源包与指令都能直接使用。
     */
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    /** 选中轮廓：盖住手办的高度与底座，方便左键点到。 */
    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 12.0D, 12.0D);

    protected AbstractFigurineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    /** 放下时正面朝向玩家，精确到 22.5°。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0F;
        // 模型 0 档朝南（+Z）；玩家视角的反方向再加半档取整，就得到背对玩家的档位。
        int rotation = (Mth.floor(yaw * 16.0F / 360.0F + 0.5F) + 8) & 15;
        return this.defaultBlockState().setValue(ROTATION, rotation);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 轮廓用于瞄准和左键破坏。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /** 没有碰撞，玩家可以直接走过去。 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }
}
