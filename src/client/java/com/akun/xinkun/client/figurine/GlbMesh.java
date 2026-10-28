package com.akun.xinkun.client.figurine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * 一个极简的 glTF 二进制（GLB）静态网格读取器。
 *
 * <p>Blender 与大多数 DCC 都能直接导出 GLB，但 Minecraft 本身不支持这种格式。
 * 这里只实现手办需要的那一小部分：单个网格、POSITION / NORMAL / TEXCOORD_0
 * 三个顶点属性、无符号整型索引，以及材质名与基础色。贴图不打包在 GLB 里，
 * 由渲染器按材质名换成游戏内普通贴图，这样能走原版的贴图过滤与资源包覆盖。</p>
 */
public final class GlbMesh {
    private static final int MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;
    private static final int COMPONENT_FLOAT = 5126;
    private static final int COMPONENT_UBYTE = 5121;
    private static final int COMPONENT_USHORT = 5123;
    private static final int COMPONENT_UINT = 5125;

    /**
     * 一个材质分区。顶点数据按三角形索引展开，渲染时直接顺序写入顶点缓冲。
     *
     * @param material 材质名，渲染器据此选择贴图
     * @param tint     顶点颜色（ARGB），无贴图的底座等材质用它上色
     * @param emissive 是否无视环境光自发光（眼睛）
     */
    public record Part(String material, float[] positions, float[] normals, float[] uvs,
                       int[] indices, int tint, boolean emissive) {
    }

    private final List<Part> parts;
    private final float maxY;
    private final float minY;

    private GlbMesh(List<Part> parts, float minY, float maxY) {
        this.parts = List.copyOf(parts);
        this.minY = minY;
        this.maxY = maxY;
    }

    public List<Part> parts() {
        return this.parts;
    }

    public float minY() {
        return this.minY;
    }

    public float maxY() {
        return this.maxY;
    }

    /** 读取并解析一个 GLB。失败时抛出 {@link IllegalStateException}，由渲染器记录一次日志后停用。 */
    public static GlbMesh load(Identifier location) {
        byte[] data;
        try (InputStream in = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(location).open()) {
            data = in.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("读取手办模型失败: " + location, exception);
        }
        if (data.length < 20) {
            throw new IllegalStateException("手办模型文件过短: " + location);
        }
        ByteBuffer file = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (file.getInt(0) != MAGIC) {
            throw new IllegalStateException("不是 GLB 文件: " + location);
        }
        int version = file.getInt(4);
        if (version != 2) {
            throw new IllegalStateException("只支持 glTF 2.0 的 GLB，实际版本 " + version);
        }
        String json = null;
        ByteBuffer binary = null;
        int offset = 12;
        while (offset + 8 <= data.length) {
            int chunkLength = file.getInt(offset);
            int chunkType = file.getInt(offset + 4);
            offset += 8;
            if (chunkLength < 0 || offset + chunkLength > data.length) {
                throw new IllegalStateException("GLB 数据块长度异常");
            }
            if (chunkType == CHUNK_JSON) {
                json = new String(data, offset, chunkLength, StandardCharsets.UTF_8);
            } else if (chunkType == CHUNK_BIN) {
                binary = ByteBuffer.wrap(data, offset, chunkLength)
                        .slice().order(ByteOrder.LITTLE_ENDIAN);
            }
            offset += chunkLength;
        }
        if (json == null || binary == null) {
            throw new IllegalStateException("GLB 缺少 JSON 或二进制数据块");
        }
        return parse(json, binary);
    }

    private static GlbMesh parse(String json, ByteBuffer binary) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray meshes = root.getAsJsonArray("meshes");
        if (meshes == null || meshes.isEmpty()) {
            throw new IllegalStateException("GLB 中没有网格");
        }
        JsonArray primitives = meshes.get(0).getAsJsonObject().getAsJsonArray("primitives");
        JsonArray materials = root.has("materials") ? root.getAsJsonArray("materials") : new JsonArray();

        List<Part> parts = new ArrayList<>(primitives.size());
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < primitives.size(); i++) {
            JsonObject primitive = primitives.get(i).getAsJsonObject();
            JsonObject attributes = primitive.getAsJsonObject("attributes");
            if (attributes == null || !attributes.has("POSITION")) {
                continue;
            }
            float[] positions = readFloats(root, binary, attributes.get("POSITION").getAsInt(), 3);
            float[] normals = attributes.has("NORMAL")
                    ? readFloats(root, binary, attributes.get("NORMAL").getAsInt(), 3)
                    : new float[positions.length];
            float[] uvs = attributes.has("TEXCOORD_0")
                    ? readFloats(root, binary, attributes.get("TEXCOORD_0").getAsInt(), 2)
                    : new float[positions.length / 3 * 2];
            int[] indices = primitive.has("indices")
                    ? readIndices(root, binary, primitive.get("indices").getAsInt())
                    : sequential(positions.length / 3);

            String material = "part_" + i;
            int tint = 0xFFFFFFFF;
            boolean emissive = false;
            if (primitive.has("material")) {
                int materialIndex = primitive.get("material").getAsInt();
                if (materialIndex >= 0 && materialIndex < materials.size()) {
                    JsonObject materialJson = materials.get(materialIndex).getAsJsonObject();
                    if (materialJson.has("name")) {
                        material = materialJson.get("name").getAsString();
                    }
                    if (materialJson.has("pbrMetallicRoughness")) {
                        JsonObject pbr = materialJson.getAsJsonObject("pbrMetallicRoughness");
                        if (pbr.has("baseColorFactor")) {
                            JsonArray factor = pbr.getAsJsonArray("baseColorFactor");
                            tint = toArgb(factor);
                        }
                    }
                    if (materialJson.has("emissiveFactor")) {
                        JsonArray factor = materialJson.getAsJsonArray("emissiveFactor");
                        emissive = factor.get(0).getAsFloat() > 0.01F
                                || factor.get(1).getAsFloat() > 0.01F
                                || factor.get(2).getAsFloat() > 0.01F;
                    }
                }
            }
            for (int v = 1; v < positions.length; v += 3) {
                minY = Math.min(minY, positions[v]);
                maxY = Math.max(maxY, positions[v]);
            }
            parts.add(new Part(material, positions, normals, uvs, indices, tint, emissive));
        }
        if (parts.isEmpty()) {
            throw new IllegalStateException("GLB 网格没有可用图元");
        }
        return new GlbMesh(parts, minY, maxY);
    }

    private static int[] sequential(int count) {
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = i;
        }
        return indices;
    }

    /** 把 glTF 的线性 baseColorFactor 转成顶点颜色使用的 sRGB。 */
    private static int toArgb(JsonArray factor) {
        int r = toSrgb(factor.get(0).getAsFloat());
        int g = toSrgb(factor.get(1).getAsFloat());
        int b = toSrgb(factor.get(2).getAsFloat());
        int a = factor.size() > 3 ? toSrgb(factor.get(3).getAsFloat()) : 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int toSrgb(float linear) {
        double value = Math.pow(Math.max(0.0F, Math.min(1.0F, linear)), 1.0 / 2.2);
        return (int) Math.round(value * 255.0);
    }

    private static JsonObject accessor(JsonObject root, int index) {
        return root.getAsJsonArray("accessors").get(index).getAsJsonObject();
    }

    private static float[] readFloats(JsonObject root, ByteBuffer binary, int accessorIndex, int components) {
        JsonObject accessor = accessor(root, accessorIndex);
        if (accessor.get("componentType").getAsInt() != COMPONENT_FLOAT) {
            throw new IllegalStateException("顶点属性不是 float: accessor " + accessorIndex);
        }
        int count = accessor.get("count").getAsInt();
        JsonObject view = root.getAsJsonArray("bufferViews")
                .get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int start = intOr(view, "byteOffset", 0) + intOr(accessor, "byteOffset", 0);
        int stride = intOr(view, "byteStride", components * Float.BYTES);
        float[] values = new float[count * components];
        for (int i = 0; i < count; i++) {
            int base = start + i * stride;
            for (int c = 0; c < components; c++) {
                values[i * components + c] = binary.getFloat(base + c * Float.BYTES);
            }
        }
        return values;
    }

    private static int[] readIndices(JsonObject root, ByteBuffer binary, int accessorIndex) {
        JsonObject accessor = accessor(root, accessorIndex);
        int componentType = accessor.get("componentType").getAsInt();
        int size = switch (componentType) {
            case COMPONENT_UBYTE -> 1;
            case COMPONENT_USHORT -> 2;
            case COMPONENT_UINT -> 4;
            default -> throw new IllegalStateException("不支持的索引类型 " + componentType);
        };
        int count = accessor.get("count").getAsInt();
        JsonObject view = root.getAsJsonArray("bufferViews")
                .get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int start = intOr(view, "byteOffset", 0) + intOr(accessor, "byteOffset", 0);
        int stride = intOr(view, "byteStride", size);
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            int base = start + i * stride;
            indices[i] = switch (size) {
                case 1 -> binary.get(base) & 0xFF;
                case 2 -> binary.getShort(base) & 0xFFFF;
                default -> binary.getInt(base);
            };
        }
        return indices;
    }

    private static int intOr(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }
}
