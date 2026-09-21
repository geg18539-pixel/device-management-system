package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 设备附件（说明书、照片、验收单等）。
 *
 * <p>文件**存在磁盘上，数据库只存元数据**，不存二进制。
 * 把文件塞进数据库会让库迅速膨胀、备份变慢，而且读文件还要过一次 JDBC，
 * 对"传个说明书"这种需求得不偿失。
 *
 * <p>两个文件名字段要分清楚，这是安全上的关键：
 * <ul>
 *   <li>{@code fileName} —— 用户上传时的原始名，**只用于展示和下载时还原**
 *       （比如"说明书 v2.pdf"）。它可能含中文、空格、甚至 {@code ../} 这种路径片段。</li>
 *   <li>{@code storedName} —— 磁盘上的真实文件名，由服务端生成成
 *       {@code UUID + 扩展名}的纯 ASCII 形式。**任何来自用户的字符串都不参与拼路径**，
 *       否则就是把目录穿越漏洞直接开给了上传者。</li>
 * </ul>
 */
@Entity
@Table(name = "device_attachment", indexes = {
        // 详情页一定是"按设备查附件"，加个索引
        @Index(name = "idx_attachment_device", columnList = "device_id")
})
public class DeviceAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属设备 id */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 用户上传时的原始文件名，用于展示和下载时还原 */
    @Size(max = 200, message = "文件名不能超过 200 个字符")
    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    /** 磁盘上的真实文件名（UUID + 扩展名，纯 ASCII），服务端生成 */
    @Column(name = "stored_name", nullable = false, length = 100, unique = true)
    private String storedName;

    /** MIME 类型，如 application/pdf、image/png。前端据此决定预览方式 */
    @Size(max = 100, message = "文件类型不能超过 100 个字符")
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    @Size(max = 50, message = "上传人不能超过 50 个字符")
    @Column(name = "uploader", length = 50)
    private String uploader;

    @CreationTimestamp
    @Column(name = "upload_time", nullable = false, updatable = false)
    private LocalDateTime uploadTime;

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
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

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
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
