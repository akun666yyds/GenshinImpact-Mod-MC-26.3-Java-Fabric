package com.akun.xinkun.voice;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

public final class ModVoices {
    private static final Map<GenshinCharacter, Map<VoiceContext, List<VoiceLine>>> LINES = new EnumMap<>(GenshinCharacter.class);

    private ModVoices() {
    }

    public static void initialize() {
        // 类初始化时完成语音注册
    }

    static {
        Map<VoiceContext, List<VoiceLine>> furina = lines();
        line(furina, GenshinCharacter.FURINA, VoiceContext.GREETING, "greeting1", "早安。咦…居然都这个时间了？");
        line(furina, GenshinCharacter.FURINA, VoiceContext.GREETING, "greeting2", "唉，人气太高也是一种苦恼，谁让我这么受欢迎呢？");
        line(furina, GenshinCharacter.FURINA, VoiceContext.GREETING, "greeting3", "…嗯？你这样看着我…难道欢迎仪式进行到这里还不够？我应该说点什么吗？");
        // 夜间不播“早安”；使用官方问候中不含时段指向的台词。
        line(furina, GenshinCharacter.FURINA, VoiceContext.GREETING_NIGHT, "greeting_night1", "唉，人气太高也是一种苦恼，谁让我这么受欢迎呢？");
        line(furina, GenshinCharacter.FURINA, VoiceContext.GREETING_NIGHT, "greeting_night2", "…嗯？你这样看着我…难道欢迎仪式进行到这里还不够？我应该说点什么吗？");
        line(furina, GenshinCharacter.FURINA, VoiceContext.CHAT, "chat1", "茶会是淑女的必修课。如果你想学习茶会礼仪的话，我可以教你喔。");
        line(furina, GenshinCharacter.FURINA, VoiceContext.CHAT, "chat2", "你平时只吃这些吗？唉，我的通心粉分你一些好了。");
        line(furina, GenshinCharacter.FURINA, VoiceContext.CHAT, "chat3", "啊，谢谢。茶和点心都非常美味。");
        SoundEvent furinaTeaParty = line(furina, GenshinCharacter.FURINA, VoiceContext.CHAT, "chat4", "茶会吗？好呀。那我就不客气啦。");
        line(furina, GenshinCharacter.FURINA, VoiceContext.ATTACK, "attack1", "为我歌颂！");
        line(furina, GenshinCharacter.FURINA, VoiceContext.ATTACK, "attack2", "亮相啦！");
        line(furina, GenshinCharacter.FURINA, VoiceContext.ATTACK, "attack3", "开幕。");
        line(furina, GenshinCharacter.FURINA, VoiceContext.HURT, "hurt1", "干什么啊！");
        line(furina, GenshinCharacter.FURINA, VoiceContext.HURT, "hurt2", "别打了！");
        line(furina, GenshinCharacter.FURINA, VoiceContext.DEATH, "death1", "算是…一种解脱吗？");
        line(furina, GenshinCharacter.FURINA, VoiceContext.DEATH, "death2", "我还是没能做到…");
        line(furina, GenshinCharacter.FURINA, VoiceContext.DEATH, "death3", "别看我…丢人的样子…");
        line(furina, GenshinCharacter.FURINA, VoiceContext.FIRE, "fire1", "真是一派荒芜的景象，让我赐予你们水的恩泽吧！");
        line(furina, GenshinCharacter.FURINA, VoiceContext.CAKE, "cake1", "来陪我喝下午茶吧，谢贝蕾妲小姐会为我们把蛋糕端来的。");
        line(furina, GenshinCharacter.FURINA, VoiceContext.WEATHER_RAIN, "weather_rain1", "好大的雨！等等，水应该不会淹上来吧？");
        // 官方没有单独的雷天台词，雷雨沿用下雨语音。
        line(furina, GenshinCharacter.FURINA, VoiceContext.WEATHER_THUNDER, "weather_thunder1", "好大的雨！等等，水应该不会淹上来吧？");
        line(furina, GenshinCharacter.FURINA, VoiceContext.WEATHER_SNOW, "weather_snow1", "哇哦，这地方很适合映影取景。");
        line(furina, GenshinCharacter.FURINA, VoiceContext.WEATHER_SUN, "weather_sun1", "这阳光的热度，都快赶上我平时用的影室灯了。还好太阳只有一个。");
        furina.get(VoiceContext.CAKE).add(new VoiceLine(furinaTeaParty, "茶会吗？好呀。那我就不客气啦。", 80));
        LINES.put(GenshinCharacter.FURINA, furina);

        Map<VoiceContext, List<VoiceLine>> huTao = lines();
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.GREETING, "greeting1", "你好，我是往生堂的堂主，胡桃。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.GREETING, "greeting2", "你好，有没有兴趣了解一下往生堂的业务呢？");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.GREETING, "greeting3", "但是往生堂依然欢迎你的光临哦。");
        SoundEvent huTaoReturnGreeting = line(huTao, GenshinCharacter.HU_TAO, VoiceContext.GREETING, "greeting4", "哦——！回来啦？比我想的还快！");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.GREETING, "greeting5", "早——");
        // 夜间不播“早——”；其余问候不含时段指向，可安全复用于夜里。
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.GREETING_NIGHT, "greeting_night1", "你好，我是往生堂的堂主，胡桃。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.GREETING_NIGHT, "greeting_night2", "你好，有没有兴趣了解一下往生堂的业务呢？");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.GREETING_NIGHT, "greeting_night3", "但是往生堂依然欢迎你的光临哦。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.CHAT, "chat1", "人这一辈子这么短，趁着还活着抓紧时间好好享受生活。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.CHAT, "chat2", "村里的气氛还不错，我随便转转，就有了诗兴！");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.CHAT, "chat3", "唔…在这种地方都能碰上，还真巧啊。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.CHAT, "chat4", "只要我不忙，随时欢迎你来哦。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.CHAT, "chat5", "不过，我很喜欢这门生意，往生堂也是老字号了…");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.CHAT, "chat6", "往生堂非常欢迎预定服务，我们是你后顾无忧的最佳选择。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.ATTACK, "attack1", "本堂主可不会留情，现在求饶还来得及哟。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.ATTACK, "attack2", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.ATTACK, "attack3", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.ATTACK, "attack4", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.ATTACK, "attack5", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.ATTACK, "attack6", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.HURT, "hurt1", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.HURT, "hurt2", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.HURT, "hurt3", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.DEATH, "death1", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.DEATH, "death2", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.DEATH, "death3", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.AD, "ad1", "「往生堂定时大酬宾，购一送一，购二送三，多购多得！」");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.AD, "ad2", "没关系，了解一下嘛，我们最近推出了新的优惠活动。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.AD, "ad3", "这位客官，想照顾我们往生堂的生意，也不必这么心急嘛！你没什么事吧？");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.AD, "ad4", "喂喂，等等！那、那再打个折怎么样！如果一次满十个人可以有七折优惠！");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.FIRE_MAGIC, "firemagic1", "对嘛对嘛，而且你这么想，谁都会有被烧掉的一天。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.FIRE_MAGIC, "firemagic2", "虽说确实有些麻烦…老是跑来跑去的。要是能一次多烧几个…");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.FIRE_MAGIC, "firemagic3", null);
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.RETURN, "return1", "谢谢你们，还是你们最理解我的想法！");
        huTao.get(VoiceContext.RETURN).add(new VoiceLine(huTaoReturnGreeting, "哦——！回来啦？比我想的还快！", 70));
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.WEATHER_RAIN, "weather_rain1", "哎呀…麻烦喽。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.WEATHER_THUNDER, "weather_thunder1", "打雷啦，起身啦。");
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.WEATHER_SNOW, "weather_snow1", "哇，是雪！");
        // 胡桃没有独立的晴天语音，使用官方“闲聊·偷懒”中的晒太阳台词。
        line(huTao, GenshinCharacter.HU_TAO, VoiceContext.WEATHER_SUN, "weather_sun1", "咳咳…太阳出来我晒太阳，月亮出来我晒月亮咯~");
        LINES.put(GenshinCharacter.HU_TAO, huTao);

        Map<VoiceContext, List<VoiceLine>> yoimiya = lines();
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.GREETING, "greeting1", "欢迎光临「长野原」！我叫宵宫，有什么需要的都可以告诉我！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.GREETING, "greeting2", "啊——早上好啊，早上的空气最新鲜啦，多吸两口就会变聪明哦。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.GREETING, "greeting3", "嗯，该吃午饭啦，欸…今天的鸡蛋，是双黄蛋呢。嘿嘿，运气真好。");
        // 夜间问候单独成组，绝不在晚上播放“早上好”或“该吃午饭了”。
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.GREETING_NIGHT, "greeting_night1", "现在是最适合测试烟花效果的时间，无论是在天上还是在地上炸开都会很好看。嘿嘿，开玩笑的，我会注意安全啦。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.GREETING_NIGHT, "greeting_night2", "提问时间——！请问，当看完一整天的烟花之后，接下来要做什么？嘿嘿，答案揭晓：当然是去梦里看烟花啦！晚安，明天见。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.CHAT, "chat1", "烟花易逝，人情长存。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.CHAT, "chat2", "没事做的话，我来给你讲故事吧！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.CHAT, "chat3", "你养过金鱼吗？很乖巧很可爱哦。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.CHAT, "chat4", "我喜欢的，应该就是和大家一起玩吧！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.ATTACK, "attack1", null);
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.ATTACK, "attack2", null);
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.ATTACK, "attack3", null);
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.HURT, "hurt1", null);
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.HURT, "hurt2", null);
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.HURT, "hurt3", null);
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.HURT, "hurt4", null);
        // 官方 life_die_01..03，与受伤的音效彻底分开。
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.DEATH, "death1", "一不小心就…");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.DEATH, "death2", "玩过头了…");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.DEATH, "death3", "祭典…结束了。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.WEATHER_RAIN, "weather_rain1", "呜哇，烟花要湿透了！得找地方躲躲，啊，要来不及了！");
        // 官方没有单独的雷天语音，雷雨沿用下雨语音。
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.WEATHER_THUNDER, "weather_thunder1", "呜哇，烟花要湿透了！得找地方躲躲，啊，要来不及了！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.WEATHER_SNOW, "weather_snow1", "哈哈，看我搓个雪球，咻——");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.WEATHER_SUN, "weather_sun1", "全身上下都暖暖的，好舒服啊。嗯，感觉要飘起来了。");
        // 宵宫专属的烟花战斗与互动语音，全部取自角色原声。
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK, "firework1", "烟花来喽！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK, "firework2", "祭典重现！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK, "firework3", "小心火烛~");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK, "firework4", "想看烟花吗？");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK, "firework5", "点燃引信！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK_INVITE, "invite1", "对了对了，你们接下来有什么事吗？没事的话，就一起来参加吧，我来给你们准备一个超好看的烟花！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK_SHOW, "show1", "好了，跟我走吧，我带你去一个最适合看烟花会的地方。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK_SHOW, "show2", "叫上我们的伙伴都来看烟花吧，就当是我送你的祝贺礼！");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK_SHOW, "show4", "烟花易逝，人情长存。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK_GIFT, "gift1", "等下等下，这个送给你。");
        line(yoimiya, GenshinCharacter.YOIMIYA, VoiceContext.FIREWORK_GIFT, "gift2", "刚刚我也去买了一个，送给你。");
        LINES.put(GenshinCharacter.YOIMIYA, yoimiya);

        // 哥伦比娅：字幕逐条对应原神官方中文语音，说话时照常浮出气泡。
        Map<VoiceContext, List<VoiceLine>> columbina = lines();
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.GREETING, "greeting1", "你可以叫我哥伦比娅，也可以叫我库塔尔、月神大人…选你喜欢的吧。我习惯了有很多名字的日子。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.GREETING, "greeting2", "这里是你的庭院。所以这次轮到我主动打招呼了。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.GREETING, "greeting3", "呼…月亮还没升起来，我想先抱着月灵们睡个午觉。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.GREETING_NIGHT, "greeting_night1", "夜幕落下…不觉得这种说法很有趣吗？就好像「夜晚」是被月亮绊了一跤才掉下来的一样。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.GREETING_NIGHT, "greeting_night2", "晚安，我会尽量不在你睡着之后唱歌的。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.CHAT, "chat1", "我的歌并不为谁而唱。但如果有人驻足聆听，我希望他能从中获得片刻的安宁。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.CHAT, "chat2", "想要赏月吗？你可以坐到我的摇椅上，找个舒服的角度。或者我们一起去月亮上，赏提瓦特也行。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.CHAT, "chat3", "我过去每天都会在银月之庭漫步很久，不用担心我跟不上你的步伐。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.ATTACK, "attack1", "漫流。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.ATTACK, "attack2", "盈满。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.ATTACK, "attack3", "垂映。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.HURT, "hurt1", "好痛…！");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.HURT, "hurt2", "…为什么？");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.DEATH, "death1", "夜空…再次黯淡…");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.DEATH, "death2", "月亮…坠落了…");
        // 月影术吟唱：官方元素爆发语音。
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.MOON_ATTACK, "moon1", "新月终临。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.MOON_ATTACK, "moon2", "三月交辉之刻。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.MOON_ATTACK, "moon3", "将此月夜，献予众生。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.COMPANION, "companion1", "要我借你一束月光吗？");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.COMPANION, "companion2", "会出去很久吗？");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.COMPANION, "companion3", "嗯，我跟你走。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.SHY, "shy1", "唔…其实不太习惯。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.SHY, "shy2", "只是以前都是你到我住的地方来做客，现在我突然变成了客人，有些不习惯。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.GIFT, "gift1", "是吗？你喜欢听就好。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.GIFT, "gift2", "真的吗？唔…那，我可以在附近一边散步一边唱歌吗？");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.HEAL, "heal1", "你不休息吗？");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.HEAL, "heal2", "夜色变深了…");
        // 白天与桑多涅并肩散步时的回应，全部取自官方「关于桑多涅」语音。
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.TO_SANDRONE, "to_sandrone1",
                "她背后的发条会一直转吗？是永远朝同一个方向转吗？睡觉的时候，她会把发条拆下来吗？翻身要怎么办？刚见到桑多涅的时候，我就对她的发条产生了好奇。但那时和她还不熟，直接去碰似乎也不太好。所以我只要有机会就一直跟在她身后，想找到她发条转动的规律。但有一天，她突然转过身，看起来很生气地质问我为什么总要跟着她。我老老实实告诉她之后，她对我说，「你那么好奇的话，我给你背后安一个试试得了」。于是我就背着发条生活了几天，唔…的确很辛苦，桑多涅的生活真不容易。自那之后，我就更关心她了。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.TO_SANDRONE, "to_sandrone2",
                "在至冬作为「愚人众」执行官第三席的时候，能够享受到许多生活中的便利。坐列车不用买票、去科洛列夫茨基剧团也不用排队…啊，但去餐厅吃饭的话，虽然能够…「报销」？但那好像很复杂，我搞不懂。所以我都是和桑多涅，或者阿蕾奇诺一起去吃。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.TO_SANDRONE, "to_sandrone3",
                "听说他和家人的感情很好，休假的时候会举家外出，去湖边露营烧烤。桑多涅说他是执行官当中生活得最像普通人的那一个，我不了解普通人，但我觉得那样的日子听上去倒是不错。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.WEATHER_RAIN, "weather_rain1", "避雨的时候倾听雨水冲刷大地，会给我一种在「家」的安全感。");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.WEATHER_THUNDER, "weather_thunder1", "唔…！看不到闪电的话，很容易被雷声吓到…");
        line(columbina, GenshinCharacter.COLUMBINA, VoiceContext.WEATHER_SNOW, "weather_snow1", "我喜欢雪。美丽又安静，还能和月灵们打雪仗。你想现在来玩玩吗？");
        LINES.put(GenshinCharacter.COLUMBINA, columbina);

        // 钟离：字幕逐条对应原神官方中文语音。
        Map<VoiceContext, List<VoiceLine>> zhongli = lines();
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.GREETING, "greeting1", "好久不见，旅行者。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.GREETING, "greeting2", "最近过得如何？");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.GREETING, "greeting3", "希望今日也有好景气。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.GREETING_NIGHT, "greeting_night1", "虽然这种时候已经错过了云先生的戏，但夜晚的璃月仍有许多好去处。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.GREETING_NIGHT, "greeting_night2", "集市已收，喧闹的码头也已沉睡。去休息吧。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.CHAT, "chat1", "不以规矩，不能成方圆。不管是常人还是仙人，惟有各司其职，方能保璃月长久太平。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.CHAT, "chat2", "怎么，难得闲暇，不好好休息却反来找我。是想听我讲故事吗？");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.CHAT, "chat3", "「交易」的规矩是「契约」，准则是「公平」。这是订立、遵循「契约」者绝不可忘却之事：倘若失了公平，那契约便成了欺瞒的凭据。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.ATTACK, "attack1", "壁立千仞。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.ATTACK, "attack2", "震天撼地。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.ATTACK, "attack3", "靡坚不摧。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.HURT, "hurt1", "大意了吗。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.HURT, "hurt2", "不足为惧。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.DEATH, "death1", "啊，略感疲惫…");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.DEATH, "death2", "让你看到，难堪的一面了啊。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.COMPANION, "companion1", "也好。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.COMPANION, "companion2", "就当是散心了。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.COMPANION, "companion3", "做些什么好呢…");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.SHY, "shy1", "「神之眼」…也是一种「契约」。须知一切力量皆有其代价，一千种权力伴随着一千种责任。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.SHY, "shy2", "生日快乐。这是曾在你出生的那天盛开的，「琉璃百合」的干花。很久以前的璃月人会说，这种花承载着大地中的美好记忆与祈愿而盛开。我愿意相信，你的诞生也是同样的道理。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.GIFT, "gift1", "你若去往「绝云间」，便替我采来一束「清心」吧。一束就好。此行的旅费…啊，差些忘了，「契约」生效期间，旅费由你代为垫付。那就有劳了。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.GIFT, "gift2", "很久以前，与朋友共饮的酒。是因为气候变了吗？唉，过去的滋味，或许再也不能重现了吧。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.HEAL, "heal1", "下去休息。这里交给我。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.HEAL, "heal2", "哈，有点意思。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.WEATHER_RAIN, "weather_rain1", "「琉璃袋」这种植物，它偏爱潮湿的环境，最适合雨后采摘。如果发现的话，适当储备些吧。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.WEATHER_THUNDER, "weather_thunder1", "嗯…不适合度假的天气。去听听戏吧。");
        line(zhongli, GenshinCharacter.ZHONGLI, VoiceContext.WEATHER_SNOW, "weather_snow1", "找个地方避避雪吧。我不会有事，但你可能着凉。");
        LINES.put(GenshinCharacter.ZHONGLI, zhongli);

        // 纳西妲：官方中文语音，绿化时另有专属吟唱。
        Map<VoiceContext, List<VoiceLine>> nahida = lines();
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.GREETING, "greeting1", "初次见面，我已经关注你很久了。我叫纳西妲，别看我像个孩子，我比任何一位大人都了解这个世界。所以，我可以用我的知识，换取你路上的见闻吗？");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.GREETING, "greeting2", "你带来新的故事了吗？我用我亲手做的枣椰蜜糖和你交换。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.GREETING, "greeting3", "早上好，我们赶快出发吧，这世上有太多的东西都是「过时不候」的呢。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.GREETING_NIGHT, "greeting_night1", "太阳落山啦，我们也该把舞台让给夜行的大家族了。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.GREETING_NIGHT, "greeting_night2", "快去睡吧，放心，我已经为你准备好甜甜的梦啦。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.CHAT, "chat1", "不知道干什么的话，要不要我带你去转转呀？");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.CHAT, "chat2", "又有心事吗？我来陪你一起想吧。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.CHAT, "chat3", "果然要亲眼去看，才能感受到世界的美。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.ATTACK, "attack1", "记住你了。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.ATTACK, "attack2", "蔓延吧。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.ATTACK, "attack3", "全都看见咯。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.HURT, "hurt1", "好痛啊。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.HURT, "hurt2", "被打晕了…");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.DEATH, "death1", "生命…终有尽时…");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.DEATH, "death2", "枯萎之刻…");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.DEATH, "death3", "我…不明白…");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.HEAL, "heal1", "让我来想想办法。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.HEAL, "heal2", "别再硬撑了。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.WEATHER_RAIN, "weather_rain1", "快去避雨吧，小心头顶上长出蘑菇哦。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.WEATHER_THUNDER, "weather_thunder1", "你知道吗？雷鸣是生命的前奏，很快这片大地就会充满生机。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.WEATHER_SNOW, "weather_snow1", "听见了吗？天空正对万物轻语：「睡觉时间到了哦。」");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.WEATHER_SUN, "weather_sun1", "天气真好啊，暖洋洋的，我们的身边马上也要热闹起来了。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.ECOLOGY, "ecology1", "知识，与你分享。");
        line(nahida, GenshinCharacter.NAHIDA, VoiceContext.ECOLOGY, "ecology2", "此即智慧之殿堂。");
        LINES.put(GenshinCharacter.NAHIDA, nahida);

        // 桑多涅：官方中文语音，战斗吟唱与对哥伦比娅的台词单独成组。
        Map<VoiceContext, List<VoiceLine>> sandrone = lines();
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.GREETING, "greeting1",
                "我讨厌做自我介绍。该认识我的自然会认识，不需要认识的我何必去费这力气？不过，好吧。出于淑女的礼仪，我就只说这一次——好好看清楚了。在你面前的，是「愚人众」执行官第七席——「木偶」。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.GREETING, "greeting2",
                "早，要来杯热咖啡吗？很提神。嗯？你问我喝咖啡有什么用？啧，我单纯喜欢这味道不行吗？");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.GREETING, "greeting3",
                "已经这个时间了？我去准备些茶点。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.GREETING_NIGHT, "greeting_night1",
                "晚饭也来找我？我这里不是食堂…算了，来都来了，让法洁欧随便做点吧。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.GREETING_NIGHT, "greeting_night2",
                "你去睡吧。我要趁晚上安静没人打扰的时候搞些研究。我没有作息不规律！");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.CHAT, "chat1",
                "差不多该约一次茶会了吧？也不知道其他人有没有空。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.CHAT, "chat2",
                "我喜欢音乐，自学过很多乐器。你不信？哈，要是哪里有钢琴的话，我让你见识见识。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.CHAT, "chat3",
                "又通知要回至冬宫？哈…怎么总是有这么多会要开？");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.CHAT, "chat4",
                "这种天气就适合泡杯浓浓的咖啡，吃个点心，然后舒舒服服靠在阳台上看一下午的书。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.CHAT, "chat5",
                "除了音乐和机械数理之外，我还喜欢在有条件的时候种些花草，记录它们成长的过程。它们大多不需要什么干预就能生长得很好，独立而顽强。我以前就觉得这和我很像。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.CHAT, "chat6",
                "想抱着什么毛茸茸的小动物狠狠揉一揉…但它们不喜欢机械，经常见到我就躲远远的。喂！别用那种怜悯的眼神看着我！抱不到就抱不到好了！也没…没那么可惜…");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.ATTACK, "attack1", "歇一歇。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.ATTACK, "attack2", "跟上。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.ATTACK, "attack3", "无聊。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.ATTACK, "attack4", "哼。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.ATTACK, "attack5", "呵呵。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.ATTACK, "attack6", "再快点。");
        // 冰霜射线的施法吟唱：官方元素爆发语音。
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.FROST, "frost1", "演算，开始。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.FROST, "frost2", "茶间余兴。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.FROST, "frost3", "哼，可笑。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.HURT, "hurt1", "别碰我。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.HURT, "hurt2", "没礼貌。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.HURT, "hurt3", "给我等着…！");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.HURT, "hurt4", "想死吗？");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.DEATH, "death1", "演算…出错了吗…");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.DEATH, "death2", "茶会…该结束了…");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.DEATH, "death3", "很遗憾…但这就是结局…");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.COMPANION, "companion1", "该验证算式了。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.COMPANION, "companion2", "我就好心帮帮你。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.COMPANION, "companion3", "我们走，法洁欧。");
        // 白天与哥伦比娅同行时的固定开场，官方「关于哥伦比娅」语音。
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.TO_COLUMBINA, "to_columbina1",
                "哥伦比娅以前就很烦，现在也没好多少。亏我还觉得没她在会清净，谁知道见不到她的时候更烦！真受不了。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.TO_COLUMBINA, "to_columbina2",
                "这家伙现在的生活可幸福了，家人和朋友都在身边，没事还能外出旅行…我怎么就没有这种好运气？算了，我也不是真的羡慕她。我们都知道，幸福是这个家伙应得的。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.GIFT, "gift1",
                "哼…哼。我就好心听听你是怎么做的，帮你提提意见吧。不浪费是淑女的礼仪！");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.GIFT, "gift2",
                "哦？味道可以呀。下次茶会上我准备一份回礼给你。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.GIFT, "gift3", "法洁欧，清扫时间到了。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.HEAL, "heal1", "喂！没事吧？");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.HEAL, "heal2", "小心点啊！我只会修机械！");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.WEATHER_RAIN, "weather_rain1", "…烦人。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.WEATHER_THUNDER, "weather_thunder1",
                "防护系统…嗯，运转正常。");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.WEATHER_SNOW, "weather_snow1",
                "又到了那家伙会扑到雪里的时候…");
        line(sandrone, GenshinCharacter.SANDRONE, VoiceContext.WEATHER_SUN, "weather_sun1",
                "这种天气就适合泡杯浓浓的咖啡，吃个点心，然后舒舒服服靠在阳台上看一下午的书。");
        LINES.put(GenshinCharacter.SANDRONE, sandrone);
    }

    private static Map<VoiceContext, List<VoiceLine>> lines() {
        Map<VoiceContext, List<VoiceLine>> map = new EnumMap<>(VoiceContext.class);
        for (VoiceContext context : VoiceContext.values()) {
            map.put(context, new ArrayList<>());
        }
        return map;
    }

    private static SoundEvent sound(GenshinCharacter character, String slug) {
        return ModSounds.registerVoice("voice/" + character.id() + "/" + slug);
    }

    private static SoundEvent line(Map<VoiceContext, List<VoiceLine>> map, GenshinCharacter character, VoiceContext context, String slug, String text) {
        int ticks = text == null ? 0 : Math.min(240, 40 + text.length() * 6);
        SoundEvent sound = sound(character, slug);
        map.get(context).add(new VoiceLine(sound, text, ticks));
        return sound;
    }

    public static VoiceLine pick(GenshinCharacter character, VoiceContext context, RandomSource random) {
        Map<VoiceContext, List<VoiceLine>> map = LINES.get(character);
        if (map == null) {
            return null;
        }
        List<VoiceLine> list = map.get(context);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(random.nextInt(list.size()));
    }
}
