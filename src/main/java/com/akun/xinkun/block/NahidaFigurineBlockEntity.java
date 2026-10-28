package com.akun.xinkun.block;

import com.akun.xinkun.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 纳西妲的手办方块实体：自身不保存任何数据，外观由客户端渲染器按方块朝向绘制。
 */
public class NahidaFigurineBlockEntity extends BlockEntity {
    public NahidaFigurineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIGURINE_NAHIDA, pos, state);
    }
}
