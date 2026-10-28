package com.akun.xinkun.client.rig;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.XinkunGenshinMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

/**
 * 由 PMX 导出的骨骼蒙皮网格（.xkm）。
 *
 * <p>格式：文件头 XKRG + 版本/骨骼数/部件数/JSON 长度，随后是 JSON 元数据，
 * 再按部件依次排列顶点、法线、UV、四组骨骼索引与权重、三角形索引。
 * 坐标与玩家模型一致：x 向角色左侧，y 向上，z 向后，脚底 y = 0，身高 32 像素。</p>
 *
 * <p>六个角色的模型都只读取一次，之后按角色缓存。</p>
 */
public final class RigModel {
    public static final int BONE_COUNT = 19;
    public static final float HEIGHT = 32.0F;

    /**
     * 肤色暖色系数。只作用在皮肤与脸部网格上，逐顶点统一相乘，
     * 因此不会出现逐像素处理那种冷暖不均的色块；头发、眼睛与衣物保持原色。
     */
    private static final float SKIN_TINT_R = 1.0F;
    private static final float SKIN_TINT_G = 0.94F;
    private static final float SKIN_TINT_B = 0.885F;

    private static final Map<GenshinCharacter, RigModel> CACHE = new EnumMap<>(GenshinCharacter.class);
    private static final Map<GenshinCharacter, Boolean> FAILED = new EnumMap<>(GenshinCharacter.class);

    public final @Nullable String name;
    public final String[] boneNames;
    public final int[] boneParents;
    public final float[][] bonePivots;
    public final Part[] parts;

    public final int hips;
    public final int spine;
    public final int chest;
    public final int neck;
    public final int head;
    public final int[] arm = new int[2];
    public final int[] elbow = new int[2];
    public final int[] wrist = new int[2];
    public final int[] leg = new int[2];
    public final int[] knee = new int[2];
    public final int[] ankle = new int[2];
    /**
     * 左右手掌中心在绑定姿势下的位置（骨架空间）。手腕骨位于腕部，掌心在它下游
     * 约一指宽处，直接拿手腕骨当握把挂点会让武器悬在手掌上方，所以按手腕骨权重
     * 统计掌心。没有可用顶点时该侧为 null，调用方回退到手腕骨枢轴。
     */
    public final float[][] palm = new float[2][3];
    /** 握拳时手指绕指根轴卷向掌心的最大角度（弧度）。 */
    public static final float GRIP_ANGLE = 1.62F;
    /** 卷曲轴：过 {@link #gripPivot}、方向为 {@link #gripAxis} 的直线（骨架绑定空间）。 */
    public final float[][] gripAxis = new float[2][3];
    public final float[][] gripPivot = new float[2][3];

    private RigModel(@Nullable String name, String[] boneNames, int[] boneParents, float[][] bonePivots, Part[] parts) {
        this.name = name;
        this.boneNames = boneNames;
        this.boneParents = boneParents;
        this.bonePivots = bonePivots;
        this.parts = parts;
        this.hips = role("hips");
        this.spine = role("spine");
        this.chest = role("chest");
        this.neck = role("neck");
        this.head = role("head");
        this.arm[0] = role("arm.L");
        this.arm[1] = role("arm.R");
        this.elbow[0] = role("elbow.L");
        this.elbow[1] = role("elbow.R");
        this.wrist[0] = role("wrist.L");
        this.wrist[1] = role("wrist.R");
        this.leg[0] = role("leg.L");
        this.leg[1] = role("leg.R");
        this.knee[0] = role("knee.L");
        this.knee[1] = role("knee.R");
        this.ankle[0] = role("ankle.L");
        this.ankle[1] = role("ankle.R");
        for (int side = 0; side < 2; side++) {
            this.palm[side] = palmCenter(this.parts, this.wrist[side], this.bonePivots[this.wrist[side]]);
        }
        for (int side = 0; side < 2; side++) {
            this.computeGrip(side);
        }
    }

    /**
     * 握拳几何：模型没有指骨，手指是手掌网格的一部分，所以把指根以远的顶点
     * 绕一条“指根轴”卷向掌心。
     *
     * <p>手指方向取前臂方向（手腕 − 肘），掌心法线取手部顶点在垂直于手指的平面内
     * 分布最薄的方向，再翻到身体内侧——静止姿势下双手掌心相对。旋转轴取
     * {@code 手指 × 掌心法线}，绕它正向旋转正好把指尖带向掌心。</p>
     *
     * <p>每个顶点按沿手指的距离得到 0~1 的权重：指根处不动、指尖最大，
     * 于是手指逐节弯成弧线而不是整块平推。权重在腕骨权重接近一半的混合区淡出，
     * 与袖子、前臂的蒙皮边界保持连贯。</p>
     */
    private void computeGrip(int side) {
        int wrist = this.wrist[side];
        int elbow = this.elbow[side];
        if (wrist < 0 || elbow < 0) {
            return;
        }
        float[] wristPivot = this.bonePivots[wrist];
        float[] elbowPivot = this.bonePivots[elbow];
        float hx = wristPivot[0] - elbowPivot[0];
        float hy = wristPivot[1] - elbowPivot[1];
        float hz = wristPivot[2] - elbowPivot[2];
        float length = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (length < 1.0E-4F) {
            return;
        }
        hx /= length;
        hy /= length;
        hz /= length;

        // 垂直于手指的平面基底 u、v。
        float ux = hy;
        float uy = -hx;
        float uz = 0.0F;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy);
        if (uLen < 1.0E-4F) {
            ux = -hz;
            uy = 0.0F;
            uz = hx;
            uLen = (float) Math.sqrt(ux * ux + uz * uz);
        }
        ux /= uLen;
        uy /= uLen;
        uz /= uLen;
        float vx = hy * uz - hz * uy;
        float vy = hz * ux - hx * uz;
        float vz = hx * uy - hy * ux;

        // 手部顶点的平面内分布：一阶矩与二阶矩。
        int count = 0;
        double sumU = 0.0D;
        double sumV = 0.0D;
        double sumUU = 0.0D;
        double sumVV = 0.0D;
        double sumUV = 0.0D;
        for (Part part : this.parts) {
            for (int vertex = 0; vertex < part.vertexCount(); vertex++) {
                if (dominantBone(part, vertex) != wrist || dominantWeight(part, vertex) <= 0.5F) {
                    continue;
                }
                float dx = part.positions[vertex * 3] - wristPivot[0];
                float dy = part.positions[vertex * 3 + 1] - wristPivot[1];
                float dz = part.positions[vertex * 3 + 2] - wristPivot[2];
                double cu = dx * ux + dy * uy + dz * uz;
                double cv = dx * vx + dy * vy + dz * vz;
                sumU += cu;
                sumV += cv;
                sumUU += cu * cu;
                sumVV += cv * cv;
                sumUV += cu * cv;
                count++;
            }
        }
        if (count < 16) {
            return;
        }
        double meanU = sumU / count;
        double meanV = sumV / count;
        float a = (float)(sumUU / count - meanU * meanU);
        float c = (float)(sumVV / count - meanV * meanV);
        float b = (float)(sumUV / count - meanU * meanV);
        // 2x2 对称矩阵最小特征值对应的特征向量：手在“手掌到掌背”方向最薄。
        float half = 0.5F * (a - c);
        float lambda = 0.5F * (a + c) - (float) Math.sqrt(half * half + b * b);
        float px = b;
        float py = lambda - a;
        if (px * px + py * py < 1.0E-8F) {
            px = lambda - c;
            py = b;
        }
        float pLen = (float) Math.sqrt(px * px + py * py);
        if (pLen < 1.0E-6F) {
            return;
        }
        px /= pLen;
        py /= pLen;
        float nx = ux * px + vx * py;
        float ny = uy * px + vy * py;
        float nz = uz * px + vz * py;
        // 掌心朝向身体内侧：静止姿势下左右手掌心相对。
        float inward = side == 0 ? -1.0F : 1.0F;
        if (nx * inward < 0.0F) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        float ax = hy * nz - hz * ny;
        float ay = hz * nx - hx * nz;
        float az = hx * ny - hy * nx;
        float aLen = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (aLen < 1.0E-6F) {
            return;
        }
        ax /= aLen;
        ay /= aLen;
        az /= aLen;

        // 指尖位置决定卷曲范围：以最远的 2% 顶点为终点，指根取全程的 42%。
        float maxS = 0.0F;
        for (Part part : this.parts) {
            for (int vertex = 0; vertex < part.vertexCount(); vertex++) {
                if (dominantBone(part, vertex) != wrist || dominantWeight(part, vertex) <= 0.5F) {
                    continue;
                }
                float s = (part.positions[vertex * 3] - wristPivot[0]) * hx
                        + (part.positions[vertex * 3 + 1] - wristPivot[1]) * hy
                        + (part.positions[vertex * 3 + 2] - wristPivot[2]) * hz;
                maxS = Math.max(maxS, s);
            }
        }
        float span = maxS * 0.58F;
        if (span < 0.5F) {
            return;
        }
        float rootS = maxS - span;
        this.gripAxis[side][0] = ax;
        this.gripAxis[side][1] = ay;
        this.gripAxis[side][2] = az;
        this.gripPivot[side][0] = wristPivot[0] + hx * rootS;
        this.gripPivot[side][1] = wristPivot[1] + hy * rootS;
        this.gripPivot[side][2] = wristPivot[2] + hz * rootS;

        for (Part part : this.parts) {
            int vertices = part.vertexCount();
            float[] weights = new float[vertices];
            boolean any = false;
            for (int vertex = 0; vertex < vertices; vertex++) {
                float dominant = dominantWeight(part, vertex);
                if (dominantBone(part, vertex) != wrist || dominant <= 0.5F) {
                    continue;
                }
                float s = (part.positions[vertex * 3] - wristPivot[0]) * hx
                        + (part.positions[vertex * 3 + 1] - wristPivot[1]) * hy
                        + (part.positions[vertex * 3 + 2] - wristPivot[2]) * hz;
                float t = Math.clamp((s - rootS) / span, 0.0F, 1.0F);
                if (t <= 0.0F) {
                    continue;
                }
                // 平滑起步，并在腕骨权重刚过半的混合区淡入，避免手背出现折痕。
                float weight = t * t * (3.0F - 2.0F * t);
                weight *= Math.clamp((dominant - 0.5F) * 2.0F, 0.0F, 1.0F);
                if (weight > 0.002F) {
                    weights[vertex] = weight;
                    any = true;
                }
            }
            if (any) {
                if (side == 0) {
                    part.gripLeft = weights;
                } else {
                    part.gripRight = weights;
                }
            }
        }
    }

    private static int dominantBone(Part part, int vertex) {
        int dominant = -1;
        float best = 0.0F;
        for (int slot = 0; slot < 4; slot++) {
            float weight = part.weights[vertex * 4 + slot];
            if (weight > best) {
                best = weight;
                dominant = part.joints[vertex * 4 + slot] & 0xFF;
            }
        }
        return dominant;
    }

    private static float dominantWeight(Part part, int vertex) {
        float best = 0.0F;
        for (int slot = 0; slot < 4; slot++) {
            best = Math.max(best, part.weights[vertex * 4 + slot]);
        }
        return best;
    }

    /**
     * 按手腕骨的蒙皮权重找出掌心：只统计权重过半、离手腕枢轴 4 像素以内的顶点，
     * 取平均位置。手腕骨本身在指尖端，掌心大致位于它和肘部之间，正是握把所在。
     */
    private static @Nullable float[] palmCenter(Part[] parts, int bone, float[] pivot) {
        if (bone < 0) {
            return null;
        }
        double sumX = 0.0D;
        double sumY = 0.0D;
        double sumZ = 0.0D;
        int count = 0;
        for (Part part : parts) {
            for (int vertex = 0; vertex < part.vertexCount(); vertex++) {
                int dominant = -1;
                float best = 0.0F;
                for (int slot = 0; slot < 4; slot++) {
                    float weight = part.weights[vertex * 4 + slot];
                    if (weight > best) {
                        best = weight;
                        dominant = part.joints[vertex * 4 + slot] & 0xFF;
                    }
                }
                if (dominant != bone || best <= 0.5F) {
                    continue;
                }
                float x = part.positions[vertex * 3];
                float y = part.positions[vertex * 3 + 1];
                float z = part.positions[vertex * 3 + 2];
                float dx = x - pivot[0];
                float dy = y - pivot[1];
                float dz = z - pivot[2];
                if (dx * dx + dy * dy + dz * dz > 16.0F) {
                    continue;
                }
                sumX += x;
                sumY += y;
                sumZ += z;
                count++;
            }
        }
        if (count < 8) {
            return null;
        }
        return new float[] {
                (float)(sumX / count), (float)(sumY / count), (float)(sumZ / count)
        };
    }

    private int role(String name) {
        for (int i = 0; i < this.boneNames.length; i++) {
            if (name.equals(this.boneNames[i])) {
                return i;
            }
        }
        return -1;
    }

    /** 当前资源包中的模型；首次调用时读取，失败后返回 null 并由调用方回退到皮肤渲染。 */
    public static @Nullable RigModel of(GenshinCharacter character) {
        RigModel cached = CACHE.get(character);
        if (cached != null) {
            return cached;
        }
        if (FAILED.containsKey(character)) {
            return null;
        }
        RigModel loaded = load(character);
        if (loaded == null) {
            FAILED.put(character, Boolean.TRUE);
        } else {
            CACHE.put(character, loaded);
        }
        return loaded;
    }

    private static @Nullable RigModel load(GenshinCharacter character) {
        Identifier id = XinkunGenshinMod.id("xkm/" + character.id().replace("_", "") + ".xkm");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            XinkunGenshinMod.LOGGER.warn("缺少角色网格资源 {}", id);
            return null;
        }
        try (InputStream stream = resource.get().open()) {
            return parse(stream.readAllBytes());
        } catch (IOException | RuntimeException exception) {
            XinkunGenshinMod.LOGGER.warn("读取角色网格 {} 失败", id, exception);
            return null;
        }
    }

    private static RigModel parse(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.get() != 'X' || buffer.get() != 'K' || buffer.get() != 'R' || buffer.get() != 'G') {
            throw new IllegalArgumentException("不是 XKRG 网格文件");
        }
        int version = buffer.getInt();
        if (version != 1) {
            throw new IllegalArgumentException("不支持的网格版本 " + version);
        }
        int boneCount = buffer.getInt();
        int partCount = buffer.getInt();
        int jsonLength = buffer.getInt();
        buffer.getInt();
        byte[] json = new byte[jsonLength];
        buffer.get(json);

        JsonObject meta = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray boneArray = meta.getAsJsonArray("bones");
        String[] names = new String[boneCount];
        int[] parents = new int[boneCount];
        float[][] pivots = new float[boneCount][3];
        for (int i = 0; i < boneCount; i++) {
            JsonObject bone = boneArray.get(i).getAsJsonObject();
            names[i] = bone.get("name").getAsString();
            parents[i] = bone.get("parent").getAsInt();
            JsonArray pivot = bone.getAsJsonArray("pivot");
            for (int axis = 0; axis < 3; axis++) {
                pivots[i][axis] = pivot.get(axis).getAsFloat();
            }
        }

        JsonArray partArray = meta.getAsJsonArray("parts");
        // 口型只作用在脸部最大的那一块网格上，避免把眉毛、睫毛等小部件也带走。
        int mouthPart = -1;
        int mouthVertices = 0;
        for (int i = 0; i < partArray.size(); i++) {
            JsonObject part = partArray.get(i).getAsJsonObject();
            String partName = part.get("name").getAsString();
            int vertices = part.get("vertexCount").getAsInt();
            if (partName.contains("_face") && vertices > mouthVertices) {
                mouthVertices = vertices;
                mouthPart = i;
            }
        }

        Part[] parts = new Part[partCount + (mouthPart >= 0 ? 1 : 0)];
        float patchCenterX = 0.0F;
        float patchCenterY = 0.0F;
        float patchCenterZ = 0.0F;
        float patchHalfWidth = 0.0F;
        float patchHalfHeight = 0.0F;
        for (int i = 0; i < partCount; i++) {
            JsonObject part = partArray.get(i).getAsJsonObject();
            String partName = part.get("name").getAsString();
            int vertices = part.get("vertexCount").getAsInt();
            int indices = part.get("indexCount").getAsInt();
            float[] positions = new float[vertices * 3];
            float[] normals = new float[vertices * 3];
            float[] uvs = new float[vertices * 2];
            byte[] joints = new byte[vertices * 4];
            float[] weights = new float[vertices * 4];
            readFloats(buffer, positions);
            readFloats(buffer, normals);
            readFloats(buffer, uvs);
            buffer.get(joints);
            readFloats(buffer, weights);
            int[] triangles = new int[indices];
            for (int index = 0; index < indices; index++) {
                triangles[index] = buffer.getInt();
            }
            orientTriangles(positions, normals, triangles);
            float[] tint = new float[4];
            JsonElement tintElement = part.get("tint");
            if (tintElement != null && !tintElement.isJsonNull()) {
                JsonArray tintArray = tintElement.getAsJsonArray();
                for (int channel = 0; channel < Math.min(4, tintArray.size()); channel++) {
                    tint[channel] = tintArray.get(channel).getAsFloat();
                }
            }
            String texture = part.get("texture").getAsString();
            if (partName.endsWith("_skin") || partName.endsWith("_face")) {
                tint[0] = Math.min(1.0F, tint[0] * SKIN_TINT_R);
                tint[1] = Math.min(1.0F, tint[1] * SKIN_TINT_G);
                tint[2] = Math.min(1.0F, tint[2] * SKIN_TINT_B);
            }
            boolean translucent = part.has("translucent") && part.get("translucent").getAsBoolean();
            boolean eyes = part.has("eyes") && part.get("eyes").getAsBoolean();
            // 眨眼时以眼睛网格的竖直中心为轴压扁，两端不会跑到别处。
            float eyeCenterY = 0.0F;
            if (eyes) {
                float minY = Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                for (int vertex = 1; vertex < positions.length; vertex += 3) {
                    minY = Math.min(minY, positions[vertex]);
                    maxY = Math.max(maxY, positions[vertex]);
                }
                eyeCenterY = (minY + maxY) * 0.5F;
            }
            Identifier textureId = XinkunGenshinMod.id("textures/entity/rig/" + texture + ".png");
            RenderType renderType = translucent
                    ? RenderTypes.entityTranslucent(textureId)
                    : RenderTypes.entityCutout(textureId);

            // 衣裙与长发随风轻摆：越靠近下摆的顶点权重越大；布料在脚面附近淡出，
            // 免得鞋子跟着漂移。权重在解析时一次算好，渲染循环里只做乘法。
            float[] sway = null;
            float swayScale = 0.0F;
            float swayPhase = 0.0F;
            boolean hair = partName.contains("_hair");
            if (hair || partName.contains("_cloth")) {
                float minY = Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                for (int vertex = 1; vertex < positions.length; vertex += 3) {
                    minY = Math.min(minY, positions[vertex]);
                    maxY = Math.max(maxY, positions[vertex]);
                }
                float span = maxY - minY;
                if (span > 3.0F && maxY > 3.0F) {
                    sway = new float[vertices];
                    for (int vertex = 0; vertex < vertices; vertex++) {
                        float y = positions[vertex * 3 + 1];
                        float lower = Math.clamp((maxY - y) / span, 0.0F, 1.0F);
                        float weight = lower * lower * (float) Math.sqrt(lower);
                        if (!hair) {
                            weight *= Math.clamp(y / 4.0F, 0.0F, 1.0F);
                        }
                        sway[vertex] = weight;
                    }
                    swayScale = hair ? 0.55F : 0.32F;
                    swayPhase = Math.abs(partName.hashCode() % 628) / 100.0F;
                }
            }

            // 口型贴片的位置：取脸部网格中嘴部附近的顶点，求真实的表面中心。
            // 贴片本身是一小片深色张口，说话时上下张开，不再拉伸人中或鼻梁。
            if (i == mouthPart) {
                float minX = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE;
                float minY = Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                float minZ = Float.MAX_VALUE;
                float maxZ = -Float.MAX_VALUE;
                for (int vertex = 0; vertex < vertices; vertex++) {
                    float x = positions[vertex * 3];
                    float y = positions[vertex * 3 + 1];
                    float z = positions[vertex * 3 + 2];
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
                float centerX = (minX + maxX) * 0.5F;
                patchCenterY = minY + (maxY - minY) * 0.22F;
                float frontZ = minZ + (maxZ - minZ) * 0.06F;
                float sumWeight = 0.0F;
                float sumX = 0.0F;
                float sumZ = 0.0F;
                for (int vertex = 0; vertex < vertices; vertex++) {
                    float x = positions[vertex * 3];
                    float y = positions[vertex * 3 + 1];
                    float z = positions[vertex * 3 + 2];
                    float dx = (x - centerX) / 0.40F;
                    float dy = (y - patchCenterY) / 0.40F;
                    float dz = (z - frontZ) / 0.30F;
                    float weight = (float) Math.exp(-0.5F * (dx * dx + dy * dy + dz * dz));
                    if (weight > 0.5F) {
                        sumWeight += weight;
                        sumX += x * weight;
                        sumZ += z * weight;
                    }
                }
                if (sumWeight > 0.0F) {
                    patchCenterX = sumX / sumWeight;
                    patchCenterZ = sumZ / sumWeight - 0.06F;
                } else {
                    patchCenterX = centerX;
                    patchCenterZ = frontZ - 0.06F;
                }
                patchHalfWidth = (maxX - minX) * 0.085F;
                patchHalfHeight = (maxY - minY) * 0.06F;
            }

            parts[i] = new Part(partName, textureId, renderType, translucent,
                    positions, normals, uvs, joints, weights, triangles, tint, eyes, eyeCenterY,
                    sway, swayScale, swayPhase, 0.0F, false);
        }
        if (mouthPart >= 0) {
            int headBone = 0;
            for (int i = 0; i < names.length; i++) {
                if ("head".equals(names[i])) {
                    headBone = i;
                    break;
                }
            }
            parts[partCount] = mouthPatch(patchCenterX, patchCenterY, patchCenterZ,
                    patchHalfWidth, patchHalfHeight, headBone);
        }
        String name = meta.has("name") ? meta.get("name").getAsString() : null;
        return new RigModel(name, names, parents, pivots, parts);
    }

    /**
     * 一张贴在嘴前的独立小四边形，全部蒙到头骨上。
     * 说话时由网格层按口型开合度把它上下拉开并淡入，闭嘴时完全不提交。
     */
    private static Part mouthPatch(float centerX, float centerY, float centerZ,
                                   float halfWidth, float halfHeight, int headBone) {
        float[] positions = {
                centerX + halfWidth, centerY - halfHeight, centerZ,
                centerX - halfWidth, centerY - halfHeight, centerZ,
                centerX - halfWidth, centerY + halfHeight, centerZ,
                centerX + halfWidth, centerY + halfHeight, centerZ
        };
        float[] normals = {0.0F, 0.0F, -1.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, -1.0F};
        // 纹理 v=0 在上方：底边取 v=1、顶边取 v=0。
        float[] uvs = {1.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F};
        byte[] joints = new byte[16];
        float[] weights = new float[16];
        for (int vertex = 0; vertex < 4; vertex++) {
            joints[vertex * 4] = (byte) headBone;
            weights[vertex * 4] = 1.0F;
        }
        int[] indices = {0, 1, 2, 0, 2, 3};
        float[] tint = {1.0F, 1.0F, 1.0F, 1.0F};
        Identifier texture = XinkunGenshinMod.id("textures/entity/rig/mouth.png");
        return new Part("mouth", texture, RenderTypes.entityTranslucent(texture), true,
                positions, normals, uvs, joints, weights, indices, tint, false, 0.0F,
                null, 0.0F, 0.0F, centerY, true);
    }

    private static void readFloats(ByteBuffer buffer, float[] target) {
        for (int i = 0; i < target.length; i++) {
            target[i] = buffer.getFloat();
        }
    }

    /**
     * 26.3 的实体着色按 {@code gl_FrontFacing}（完全由三角形绕序决定）在两套面光照之间切换：
     * 正面用正常光照，背面用反向光照。哥伦比娅与桑多涅来自另一条导出管线，绕序与顶点法线相反，
     * 身体外侧被当成背面着色，头发、翅膀、眼睛看起来像翻了出去。这里按顶点法线统一绕序，
     * 外表面成为正面；本来就一致的角色不受影响。
     */
    private static void orientTriangles(float[] positions, float[] normals, int[] triangles) {
        for (int index = 0; index + 2 < triangles.length; index += 3) {
            int a = triangles[index];
            int b = triangles[index + 1];
            int c = triangles[index + 2];
            float ax = positions[a * 3];
            float ay = positions[a * 3 + 1];
            float az = positions[a * 3 + 2];
            float abx = positions[b * 3] - ax;
            float aby = positions[b * 3 + 1] - ay;
            float abz = positions[b * 3 + 2] - az;
            float acx = positions[c * 3] - ax;
            float acy = positions[c * 3 + 1] - ay;
            float acz = positions[c * 3 + 2] - az;
            float gx = aby * acz - abz * acy;
            float gy = abz * acx - abx * acz;
            float gz = abx * acy - aby * acx;
            float nx = normals[a * 3] + normals[b * 3] + normals[c * 3];
            float ny = normals[a * 3 + 1] + normals[b * 3 + 1] + normals[c * 3 + 1];
            float nz = normals[a * 3 + 2] + normals[b * 3 + 2] + normals[c * 3 + 2];
            if (gx * nx + gy * ny + gz * nz < 0.0F) {
                triangles[index + 1] = c;
                triangles[index + 2] = b;
            }
        }
    }

    /**
     * 一个材质组。顶点数据保持导出时的 32 像素坐标系，
     * 渲染时按骨骼矩阵线性混合后再统一缩放到方块尺度。
     */
    public static final class Part {
        public final String name;
        public final Identifier texture;
        public final RenderType renderType;
        public final boolean translucent;
        public final float[] positions;
        public final float[] normals;
        public final float[] uvs;
        public final byte[] joints;
        public final float[] weights;
        public final int[] indices;
        public final float[] tint;
        /** 眼睛部件：眨眼前沿竖直方向向 {@link #eyeCenterY} 压扁。 */
        public final boolean eyes;
        public final float eyeCenterY;
        /** 布料/长发的风力摆动权重（0 固定、1 摆动最大），没有则为 null。 */
        public final float[] sway;
        public final float swayScale;
        public final float swayPhase;
        /** 说话口型贴片的竖直中心；{@link #mouthPatch} 为 true 时生效。 */
        public final float mouthCenterY;
        /** 嘴前那张独立小四边形，按口型开合度缩放并淡入。 */
        public final boolean mouthPatch;
        /** 握拳时该顶点的卷曲权重（左右手各一份，0 不动、1 卷到最大），没有则为 null。 */
        public float[] gripLeft;
        public float[] gripRight;

        Part(String name, Identifier texture, RenderType renderType, boolean translucent,
             float[] positions, float[] normals, float[] uvs, byte[] joints, float[] weights,
             int[] indices, float[] tint, boolean eyes, float eyeCenterY,
             float[] sway, float swayScale, float swayPhase,
             float mouthCenterY, boolean mouthPatch) {
            this.name = name;
            this.texture = texture;
            this.renderType = renderType;
            this.translucent = translucent;
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.joints = joints;
            this.weights = weights;
            this.indices = indices;
            this.tint = tint;
            this.eyes = eyes;
            this.eyeCenterY = eyeCenterY;
            this.sway = sway;
            this.swayScale = swayScale;
            this.swayPhase = swayPhase;
            this.mouthCenterY = mouthCenterY;
            this.mouthPatch = mouthPatch;
        }

        public int vertexCount() {
            return this.positions.length / 3;
        }
    }

    /**
     * 渲染时把 32 像素高的模型放进玩家脚底（y = 0）与身高换算。
     * 这一段与玩家皮肤模型使用的矩阵完全一致，因此动作和朝向可以直接沿用原版。
     */
    public static void applyModelTransform(PoseStack poseStack) {
        poseStack.translate(0.0F, 1.5F, 0.0F);
        poseStack.scale(1.0F / 16.0F, -1.0F / 16.0F, 1.0F / 16.0F);
    }
}
