package com.yan.backend.dto;

import java.time.LocalDateTime;

/**
 * 知识库文档（列表 / 上传结果用）。
 *
 * <p>不直接返回实体，原因和项目里其它 VO 一样：实体的 {@code storedName}
 * 是磁盘上的真实文件名，属于服务端实现细节，没必要暴露给前端。
 * 万一以后有了路径拼接的疏漏，前端拿着它反而多一条攻击面。
 */
public class KnowledgeVO {

    private Long id;
    private String title;
    /** 用户上传时的原始文件名，仅供显示 */
    private String fileName;
    private String fileType;
    private Long fileSize;

    /** 待处理 / 处理中 / 已完成 / 失败 */
    private String status;
    /** 失败原因。成功时为 null */
    private String errorMsg;

    /** 解析出来的文本块数 */
    private Integer chunkCount;

    /** 用哪个嵌入模型算的向量。前端在模型和当前配置不一致时给出提示 */
    private String embedModel;
    private Integer dimension;

    private String uploader;
    private LocalDateTime uploadTime;

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
}
