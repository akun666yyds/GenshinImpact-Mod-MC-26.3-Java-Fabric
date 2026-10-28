package com.akun.xinkun.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * 角色渲染状态。使用原版人形状态链（而不是玩家专用的 AvatarRenderState），
 * 这样实体渲染调度器会正常走本模组的渲染器，3D 皮肤层和自定义饰品层才会被提交。
 * id 用于让每名角色的眨眼节奏互不相同。
 */
public class GenshinNpcRenderState extends HumanoidRenderState {
    public int id;
    /** 宵宫的烟花表演动作：0 无，1 双手举高。拉弓不走这里，用原版手臂姿势模板。 */
    public int fireworkPose;
    /**
     * 钟离的动作：0 无，1 双臂举起召唤岩枪（唤魔者式），
     * 2 双手抱胸凝聚天星，3 双臂张开汲取地脉，4 挺矛突刺。
     */
    public int zhongliPose;
    /** 纳西妲正在绿化：双手举向天空，掌心落下草木与催熟粒子。 */
    public boolean nahidaBlooming;
    /** 桑多涅正在施展冰霜射线：身体浮空、双腿微曲分开、双手向前推出。 */
    public boolean sandroneCasting;
    /** 施法权重 0~1：离地与光束淡入淡出都按它平滑过渡，避免开关式的瞬间跳变。 */
    public float castWeight;
    /** 金色机械傀儡的生长权重 0~1，比光束略慢，像是从身后慢慢升起展开。 */
    public float robotWeight;
    /** 光束落点（相对实体脚底的世界坐标）；未开火时接近 0。 */
    public float beamX;
    public float beamY;
    public float beamZ;
    /** 正在念台词（有语音或字幕），骨架模型据此做很轻的点头动作。 */
    public boolean speaking;
    /** 眨眼进度 0~1：骨架模型按这个值把眼睛网格压扁；皮肤模型仍用整张眨眼贴图。 */
    public float blink;
    /**
     * 招式姿势的平滑权重：0 烟花、1~4 钟离动作、5 纳西妲绿化。
     * 招式开关之间用交叉淡入淡出，避免手臂在一帧之内从站姿跳到技能姿势。
     */
    public final float[] poseWeights = new float[6];
    /**
     * 左右手的握持程度 0~1：手上有物品时手指卷起握住柄，收手后缓慢张开。
     * 平滑在渲染器里跨帧进行，这里只放本帧的最终值。
     */
    public float gripLeft;
    public float gripRight;
}
