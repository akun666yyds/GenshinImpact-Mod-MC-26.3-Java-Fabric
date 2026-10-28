package com.akun.xinkun.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 钟离的手办方块：外观是钟离的 3D 模型。 */
public class ZhongliFigurineBlock extends AbstractFigurineBlock {
    public ZhongliFigurineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZhongliFigurineBlockEntity(pos, state);
    }
}
