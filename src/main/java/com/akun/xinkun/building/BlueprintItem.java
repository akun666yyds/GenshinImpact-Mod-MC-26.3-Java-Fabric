package com.akun.xinkun.building;

import com.akun.xinkun.XinkunGenshinMod;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 建筑道具：拿在手里右键任意方块，就会把对应蓝图从该处开始逐层盖起来。
 * 施工是强制的——不检查脚下地形，也不检查范围内有没有实体。
 * 道具本身不依赖任何角色，也不占用角色 AI。
 */
public class BlueprintItem extends Item {
    private final BuildingType type;

    public BlueprintItem(Item.Properties properties, BuildingType type) {
        super(properties);
        this.type = type;
    }

    public BuildingType buildingType() {
        return this.type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        BuildBlueprint blueprint = BlueprintLibrary.create(serverLevel, this.type);
        if (blueprint == null) {
            player.sendOverlayMessage(Component.translatable(
                    "item.xinkun_genshin.building.broken").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        BlockPos clicked = context.getClickedPos().above();
        int turns = quarterTurns(player.getDirection());
        int width = turns % 2 == 0 ? blueprint.width() : blueprint.depth();
        int depth = turns % 2 == 0 ? blueprint.depth() : blueprint.width();
        int baseY = clicked.getY();
        if (baseY < serverLevel.getMinY() || baseY + blueprint.height() - 1 > serverLevel.getMaxY() - 1) {
            player.sendOverlayMessage(Component.translatable(
                    "item.xinkun_genshin.building.too_tall").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        BlockPos origin = new BlockPos(clicked.getX() - width / 2, baseY, clicked.getZ() - depth / 2);
        // 建筑道具是强制施工：不检查地形，也不检查范围内是否有实体。
        // 施工过程中落在实体身上的方块会把人/生物顶到方块上方，不会中途跳过任何一块。
        BuildingPlacementManager.start(serverLevel, this.type, blueprint, origin, turns, player);
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable(this.type.tooltipKey()).withStyle(ChatFormatting.DARK_GRAY));
        lines.accept(Component.translatable("item.xinkun_genshin.building.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 玩家朝向对应的 90° 旋转次数。 */
    public static int quarterTurns(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }
}
