package com.yan.backend.dto;

/**
 * 一次上传落盘后的结果。
 *
 * <p>和实体 {@code DeviceAttachment} 分开：这里描述的是"文件层面发生了什么"，
 * 不含 deviceId、uploader 这些业务字段 —— 存文件的服务不该关心文件是挂在
 * 设备上还是别的地方。
 */
public class StoredFileVO {

    /** 磁盘上的真实文件名（纯 ASCII，服务端生成） */
    private String storedName;

    /** 用户上传时的原始文件名 */
    private String originalName;

    /** 字节大小 */
    private long size;

    /** MIME 类型 */
    private String contentType;

    public StoredFileVO() {
    }

    public StoredFileVO(String storedName, String originalName, long size, String contentType) {
        this.storedName = storedName;
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
