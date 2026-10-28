package com.akun.xinkun.registry;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.ColumbinaFigurineBlock;
import com.akun.xinkun.block.FurinaFigurineBlock;
import com.akun.xinkun.block.HutaoFigurineBlock;
import com.akun.xinkun.block.NahidaFigurineBlock;
import com.akun.xinkun.block.SandroneFigurineBlock;
import com.akun.xinkun.block.YoimiyaFigurineBlock;
import com.akun.xinkun.block.ZhongliFigurineBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    /** 「少女的手办」：没有碰撞的装饰方块，外观由方块实体渲染器绘制。 */
    public static final Block FIGURINE_COLUMBINA = register("figurine_columbina",
            ColumbinaFigurineBlock::new, figurineProperties());
    /** 芙宁娜的手办：与少女手办同一套方块行为，换成芙宁娜的模型。 */
    public static final Block FIGURINE_FURINA = register("figurine_furina",
            FurinaFigurineBlock::new, figurineProperties());
    /** 胡桃的手办。 */
    public static final Block FIGURINE_HUTAO = register("figurine_hutao",
            HutaoFigurineBlock::new, figurineProperties());
    /** 宵宫的手办。 */
    public static final Block FIGURINE_YOIMIYA = register("figurine_yoimiya",
            YoimiyaFigurineBlock::new, figurineProperties());
    /** 钟离的手办。 */
    public static final Block FIGURINE_ZHONGLI = register("figurine_zhongli",
            ZhongliFigurineBlock::new, figurineProperties());
    /** 纳西妲的手办。 */
    public static final Block FIGURINE_NAHIDA = register("figurine_nahida",
            NahidaFigurineBlock::new, figurineProperties());
    /** 桑多涅的手办。 */
    public static final Block FIGURINE_SANDRONE = register("figurine_sandrone",
            SandroneFigurineBlock::new, figurineProperties());

    private ModBlocks() {
    }

    public static void initialize() {
    }

    private static BlockBehaviour.Properties figurineProperties() {
        return BlockBehaviour.Properties.of()
                .strength(0.2F)
                .sound(SoundType.STONE)
                .noCollision()
                .noOcclusion()
                .pushReaction(PushReaction.POPPED);
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        Identifier id = XinkunGenshinMod.id(name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        Block block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }
}
