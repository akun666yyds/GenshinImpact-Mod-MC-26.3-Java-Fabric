package com.akun.xinkun.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 桑多涅的手办方块：外观是「木偶」的 3D 模型。 */
public class SandroneFigurineBlock extends AbstractFigurineBlock {
    public SandroneFigurineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SandroneFigurineBlockEntity(pos, state);
    }
}
