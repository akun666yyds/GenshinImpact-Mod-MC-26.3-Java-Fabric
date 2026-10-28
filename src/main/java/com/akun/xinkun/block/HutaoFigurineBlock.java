package com.akun.xinkun.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 胡桃的手办方块：外观是胡桃的 3D 模型。 */
public class HutaoFigurineBlock extends AbstractFigurineBlock {
    public HutaoFigurineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HutaoFigurineBlockEntity(pos, state);
    }
}
