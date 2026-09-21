package com.yan.backend.dto;

import java.time.LocalDateTime;

/**
 * 首页 AI 摘要卡片的内容。
 *
 * <h3>为什么它不是"一个字符串"</h3>
 *
 * <p>因为这段摘要**随时可能没有**：还没生成过、正在生成、生成失败了、
 * 功能被关掉了 —— 而且这几种情况在界面上该有完全不同的表现。
 * 只返回一个字符串的话，前端无从区分"模型还没跑完"和"模型挂了"，
 * 只能要么一直显示空白、要么一直显示上次的旧内容（看起来像坏了）。
 *
 * <p>所以这里把"有没有内容"和"为什么没有"分开表达：
 * {@link #available} 决定要不要渲染正文，{@link #message} 说明当前是什么状态。
 *
 * <h3>⚠️ available=false 不代表出错了</h3>
 *
 * <p>刚部署完、或者应用刚重启还没预热完，都会是 false。
 * 这是正常状态，不是异常 —— {@link #generating} 才是"正在做"的意思。
 */
public class DashboardDigestVO {

    /** 摘要正文。{@code available=false} 时是空串 */
    private String text = "";

    /**
     * 生成时间。也就是这段摘要描述的是"什么时候的"系统状态。
     *
     * <p>必须返回：摘要是定时生成的，用户看到的可能是几小时前的判断。
     * 不标出来，用户会以为是实时的，拿它去对看板上的数字（而那些是实时的），
     * 一对不上就会以为系统算错了。
     */
    private LocalDateTime generatedAt;

    /**
     * 生成用的模型名。
     *
     * <p>显示成一行小字。用户换了模型之后想判断"摘要质量变了没有"，
     * 得先知道这段是谁写的 —— 否则很容易归因到别的地方去。
     */
    private String model;

    /** 有没有一份可以展示的摘要 */
    private boolean available;

    /** 是否正在生成。true 时前端会轮询等结果 */
    private boolean generating;

    /**
     * 功能是否开启。
     *
     * <p>和 available 分开：关掉是管理员的决定，没内容是运行状态。
     * 前端据此决定"整块不渲染"（而不是渲染一个写着"已关闭"的空框）。
     */
    private boolean enabled;

    /** available=false 时给用户看的一句话 */
    private String message = "";

    // ---------- getter / setter ----------

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isGenerating() {
        return generating;
    }

    public void setGenerating(boolean generating) {
        this.generating = generating;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
