package com.akun.xinkun.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 宵宫的手办方块：外观是宵宫的 3D 模型。 */
public class YoimiyaFigurineBlock extends AbstractFigurineBlock {
    public YoimiyaFigurineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new YoimiyaFigurineBlockEntity(pos, state);
    }
}
