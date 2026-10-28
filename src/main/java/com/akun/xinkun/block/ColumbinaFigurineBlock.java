package com.akun.xinkun.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 「少女的手办」方块：外观是哥伦比娅的 3D 模型。 */
public class ColumbinaFigurineBlock extends AbstractFigurineBlock {
    public ColumbinaFigurineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColumbinaFigurineBlockEntity(pos, state);
    }
}
