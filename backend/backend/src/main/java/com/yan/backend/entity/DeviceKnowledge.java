package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 知识库文档（设备手册、厂商资料、维修案例）。
 *
 * <p>这是 RAG 的**源文件表**：一个上传的 PDF/TXT/MD 一行。
 * 它解析出来的文本块在 {@link KnowledgeChunk} 里，一对多。
 *
 * <p><b>为什么状态字段是"处理状态"而不是"有没有向量"</b>：
 * 一个文档要经过「落盘 → 解析 → 切分 → 逐块嵌入」四步，中间任何一步都可能
 * 失败（PDF 是扫描件抽不出文字、嵌入模型没拉下来、Ollama 没启动）。
 * 只记一个布尔值的话，用户看到"失败"却不知道卡在哪一步，
 * 而这几步的排查方向完全不同。所以状态 + 可读的失败原因都要存。
 *
 * <p>处理是**同步**做的（上传接口里直接跑完）：知识库文档是低频操作，
 * 一个几百页的 PDF 也就几秒到几十秒。异步的话要引入轮询和状态刷新，
 * 对"传一份手册等它解析好"这个场景是过度设计。
 * （对比工单的 AI 分析——那个是每次报修都触发，且用户在前端等着，
 * 所以必须异步 + 事件驱动。）
 */
@Entity
@Table(name = "device_knowledge", indexes = {
        @Index(name = "idx_knowledge_status", columnList = "status"),
        @Index(name = "idx_knowledge_time", columnList = "upload_time")
})
public class DeviceKnowledge {

    // ---------- 处理状态 ----------
    public static final String STATUS_PENDING = "待处理";
    public static final String STATUS_PROCESSING = "处理中";
    public static final String STATUS_DONE = "已完成";
    public static final String STATUS_FAILED = "失败";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 展示用标题，默认取去掉扩展名的文件名，用户可以改 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 用户上传时的原始文件名（快照，仅供显示） */
    @Column(name = "file_name", length = 200)
    private String fileName;

    /**
     * 磁盘上的实际文件名（服务端生成的 UUID.扩展名）。
     *
     * <p>和附件一样：用户的原始名**不参与拼路径**，避免重名覆盖和目录穿越。
     */
    @Column(name = "stored_name", nullable = false, length = 100)
    private String storedName;

    /** 扩展名（小写，不含点）：pdf / txt / md */
    @Column(name = "file_type", length = 10)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** 失败原因。只写给人看的一句话，不打整段堆栈 */
    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    /** 解析出来的文本块数 */
    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount = 0;

    /**
     * 这批向量是哪个模型算的。
     *
     * <p>⚠️ 必须记 —— 换了嵌入模型之后，旧向量和新向量**不在同一个语义空间**，
     * 拿来比相似度会得到毫无意义的分数（而且不报错）。
     * 检索时只比同一个模型、同一个维度的向量。
     */
    @Column(name = "embed_model", length = 100)
    private String embedModel;

    /** 向量维度。和 embedModel 一起构成"能不能放在一起比"的判据 */
    @Column(name = "dimension")
    private Integer dimension;

    @Column(name = "uploader", length = 50)
    private String uploader;

    @Column(name = "upload_time", nullable = false)
    private LocalDateTime uploadTime;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getEmbedModel() {
        return embedModel;
    }

    public void setEmbedModel(String embedModel) {
        this.embedModel = embedModel;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public LocalDateTime getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
