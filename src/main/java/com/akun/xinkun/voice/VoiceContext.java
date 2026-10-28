package com.akun.xinkun.voice;

public enum VoiceContext {
    GREETING,
    /** 夜间专用的问候；没有夜间台词的角色会回退到普通问候。 */
    GREETING_NIGHT,
    CHAT,
    ATTACK,
    HURT,
    DEATH,
    FIRE,
    CAKE,
    AD,
    FIRE_MAGIC,
    FIREWORK,
    FIREWORK_INVITE,
    FIREWORK_SHOW,
    FIREWORK_GIFT,
    RETURN,
    WEATHER_RAIN,
    WEATHER_THUNDER,
    WEATHER_SNOW,
    WEATHER_SUN,
    /** 夜间并肩散步时的亲密台词。 */
    COMPANION,
    /** 召唤月影时的吟唱。 */
    MOON_ATTACK,
    /** 玩家望向月亮时的害羞反应。 */
    SHY,
    /** 夜间赠送月饼。 */
    GIFT,
    /** 治疗周围友方时的安抚。 */
    HEAL,
    /** 纳西妲巡游绿化、催熟草木时的吟唱。 */
    ECOLOGY,
    /** 桑多涅浮空施展冰霜射线时的吟唱。 */
    FROST,
    /** 桑多涅对哥伦比娅说的话。 */
    TO_COLUMBINA,
    /** 哥伦比娅回应桑多涅的话。 */
    TO_SANDRONE
}
