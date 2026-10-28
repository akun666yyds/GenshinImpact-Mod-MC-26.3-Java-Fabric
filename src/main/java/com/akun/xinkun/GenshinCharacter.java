package com.akun.xinkun;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelType;

public enum GenshinCharacter {
    FURINA("furina", PlayerModelType.SLIM),
    HU_TAO("hu_tao", PlayerModelType.SLIM),
    YOIMIYA("yoimiya", PlayerModelType.SLIM),
    COLUMBINA("columbina", PlayerModelType.SLIM),
    /** 「木偶」桑多涅：与哥伦比娅同为愚人众执行官，白天会并肩散步。 */
    SANDRONE("sandrone", PlayerModelType.SLIM),
    /** 钟离：成年男性的宽肩模型，整体再放大一成。 */
    ZHONGLI("zhongli", PlayerModelType.WIDE),
    /** 纳西妲：体型小巧，整体缩到四分之三。 */
    NAHIDA("nahida", PlayerModelType.SLIM);

    private final String id;
    private final PlayerModelType modelType;

    GenshinCharacter(String id, PlayerModelType modelType) {
        this.id = id;
        this.modelType = modelType;
    }

    public String id() {
        return this.id;
    }

    public PlayerModelType modelType() {
        return this.modelType;
    }

    /**
     * 渲染放大倍数。骨架模型的标准身高是 32 像素（两个方块），
     * 这里按各自碰撞箱换算：女角色 1.8 格、钟离 1.98 格、纳西妲 1.35 格，
     * 让头发顶端正好落在碰撞箱顶部，不会出现脚陷进地面或头顶穿出碰撞箱。
     */
    public float modelScale() {
        if (this == ZHONGLI) {
            return 0.99F;
        }
        if (this == NAHIDA) {
            return 0.675F;
        }
        return 0.9F;
    }

    public String nameKey() {
        return "entity.xinkun_genshin." + this.id;
    }

    public Component displayName() {
        return Component.translatable(this.nameKey());
    }
}
