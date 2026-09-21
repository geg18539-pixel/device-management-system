package com.yan.backend.knowledge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 向量和字节数组之间的编解码。
 *
 * <p>数据库里存的是二进制（BLOB）而不是逗号分隔的文本：768 维的向量，
 * 二进制占 3072 字节，文本形式要 7000 多字符，差一倍多。
 * 知识库会被反复整表读取（每次检索都要全量算余弦），这个差别会累积。
 *
 * <p>⚠️ <b>字节序必须写死成 BIG_ENDIAN。</b> JVM 默认就是大端，看起来"不写也对"，
 * 但一旦有人用 {@code -Duser.nio.byteorder=little} 或者换成别的架构，
 * 存进去的和读出来的就会错位 —— 而错位的后果是**相似度算出来完全正常、
 * 只是答案全错**，这种 bug 光看代码是发现不了的。所以显式指定，
 * 并用往返测试兜底。
 */
public final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    public static byte[] encode(float[] vector) {
        if (vector == null) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.asFloatBuffer().put(vector);
        return buffer.array();
    }

    public static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length < Float.BYTES) {
            return new float[0];
        }
        FloatBuffer floatBuffer = ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .asFloatBuffer();
        float[] vector = new float[floatBuffer.remaining()];
        floatBuffer.get(vector);
        return vector;
    }

    /**
     * 余弦相似度。
     *
     * <p>两个零向量的余弦是 0/0，会得到 NaN；NaN 参与排序会污染整个结果
     * （比较运算全返回 false，顺序变得不可预测）。所以显式挡掉。
     *
     * <p>维度不一致时返回 -1 而不是抛异常：调用方本来就要按
     * (模型, 维度) 分组比较，走到这里说明分组没做干净。给一个"绝不可能是
     * 合法相似度"的值（余弦范围是 [-1, 1]），比抛异常更容易定位 ——
     * 检索不该因为一条脏数据整个失败。
     */
    public static double cosine(float[] a, float[] b) {
        if (a.length == 0 || a.length != b.length) {
            return -1;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return -1;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
