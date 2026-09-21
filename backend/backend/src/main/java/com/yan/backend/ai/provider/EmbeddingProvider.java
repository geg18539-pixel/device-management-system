package com.yan.backend.ai.provider;

import java.util.List;

/**
 * 一个嵌入（向量化）提供方。
 *
 * <p>知识库的检索质量完全取决于它，而且**换一个提供方/模型会让已入库的向量全部失效**
 * （不同模型产出的向量不在同一个语义空间）。所以 {@link #modelName()} 必须如实返回，
 * 它会和每条向量一起存进库，检索时按 (模型, 维度) 分组比较。
 */
public interface EmbeddingProvider {

    String id();

    String label();

    /**
     * 当前配置的嵌入模型名。
     *
     * <p>⚠️ 这个值会被**写进每一条向量记录**，用来判断"这两条向量能不能比"。
     * 命名必须能唯一区分模型（含版本号更好），否则换了模型却还是同一个名字时，
     * 新旧向量会被混在一起比较 —— 结果毫无意义，而且不报错。
     */
    String modelName();

    /**
     * 批量向量化。
     *
     * <p>返回的列表**必须和入参一一对应、顺序一致** —— 调用方靠这个把向量贴回文本块。
     * 各家返回顺序的约定不同（OpenAI 系明确说顺序不保证、要按 index 排），
     * 排序是提供方的责任，不能甩给调用方。
     */
    List<float[]> embed(List<String> texts);

    /** 向量化单条。给"把用户的问题转成向量"用 */
    default float[] embedOne(String text) {
        List<float[]> result = embed(List.of(text));
        if (result.isEmpty()) {
            throw new IllegalStateException("嵌入模型没有返回向量");
        }
        return result.get(0);
    }

    /** 可用模型列表。同时充当连通性检查 */
    List<String> listModels();
}
