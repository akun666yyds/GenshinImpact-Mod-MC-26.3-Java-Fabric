package com.akun.xinkun.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 芙宁娜的手办方块：外观是芙宁娜的 3D 模型。 */
public class FurinaFigurineBlock extends AbstractFigurineBlock {
    public FurinaFigurineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FurinaFigurineBlockEntity(pos, state);
    }
}
