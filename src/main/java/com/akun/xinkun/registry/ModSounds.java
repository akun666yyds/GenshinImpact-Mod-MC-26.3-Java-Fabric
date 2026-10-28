package com.akun.xinkun.registry;

import com.akun.xinkun.XinkunGenshinMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final SoundEvent WATER_CAST = register("spell/water_cast");
    public static final SoundEvent WATER_SUMMON = register("spell/water_summon");
    public static final SoundEvent WATER_EXTINGUISH = register("spell/water_extinguish");
    public static final SoundEvent FIRE_CAST = register("spell/fire_cast");
    /** 月影降临：低沉的银白钟声与上升的空气感。 */
    public static final SoundEvent MOON_SUMMON = register("spell/moon_summon");
    /** 月光脉冲：柔和的低频扩散。 */
    public static final SoundEvent MOON_PULSE = register("spell/moon_pulse");
    /** 治愈月光：清亮的玻璃风铃。 */
    public static final SoundEvent MOON_HEAL = register("spell/moon_heal");
    public static final SoundEvent SPIRIT_AMBIENT = register("spirit/ambient");
    public static final SoundEvent SPIRIT_HURT = register("spirit/hurt");
    public static final SoundEvent SPIRIT_DEATH = register("spirit/death");
    public static final SoundEvent SPIRIT_ATTACK = register("spirit/attack");

    private ModSounds() {
    }

    public static void initialize() {
        // 类加载时完成所有音效事件注册
    }

    public static SoundEvent register(String path) {
        Identifier id = XinkunGenshinMod.id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /**
     * 角色语音使用固定传播距离。
     * 默认的可变距离在音量 1.0 时只有 16 格，角色在二十格外自然开口时
     * 玩家只能看见字幕却听不到声音。语音本身不该随施法音量缩放，
     * 这里固定为 64 格，和停止语音时通知玩家的半径保持一致。
     */
    public static SoundEvent registerVoice(String path) {
        Identifier id = XinkunGenshinMod.id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, 64.0F));
    }
}
