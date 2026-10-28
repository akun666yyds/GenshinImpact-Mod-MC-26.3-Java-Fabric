package com.akun.xinkun.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 纳西妲的手办方块：外观是纳西妲的 3D 模型。 */
public class NahidaFigurineBlock extends AbstractFigurineBlock {
    public NahidaFigurineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NahidaFigurineBlockEntity(pos, state);
    }
}
