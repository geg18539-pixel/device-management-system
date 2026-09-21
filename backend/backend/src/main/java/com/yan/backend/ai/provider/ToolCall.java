package com.yan.backend.ai.provider;

/**
 * 模型要求调用一次工具。
 *
 * <p>⚠️ <b>{@code argumentsJson} 统一约定为 JSON <u>字符串</u></b>，哪怕来源给的是对象。
 *
 * <p>为什么必须统一：两家给的形态**天然不同** ——
 * <ul>
 *   <li>Ollama 的 {@code function.arguments} 是一个**对象**；</li>
 *   <li>OpenAI 系的 {@code function.arguments} 是一个**字符串**，而且是分片到达的，
 *       要按 index 把片段拼起来才能解析。</li>
 * </ul>
 * 不统一的话，调用方（工具执行器）就得知道自己连的是哪家，
 * 抽象就白做了。由提供方在解析时归一：Ollama 那边序列化成字符串，
 * OpenAI 那边拼接后原样保留。
 *
 * @param id            调用 id。Ollama 不返回这个字段，会给一个本地生成的占位值
 * @param name          工具名
 * @param argumentsJson 参数的 JSON 字符串
 */
public record ToolCall(String id, String name, String argumentsJson) {
}
