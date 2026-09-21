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
 * 维修工单的附件（现场照片、更换件的照片、检测报告等）。
 *
 * <p><b>为什么和 DeviceAttachment 分成两张表？</b>
 * 字段几乎一样，但两者的生命周期和归属完全不同：设备附件跟着设备走
 * （设备删了附件就没了意义，是级联删除），工单附件跟着工单走
 * （工单是历史记录，附件是当时的证据，不该因为设备被删就消失）。
 *
 * <p>另一种做法是合成一张带 bizType/bizId 的通用附件表。没这么做是因为
 * 现有表已经有数据，`device_id` 是 NOT NULL 且查询都按它走；
 * 加 biz_type 后老数据是 NULL，每个查询都得写
 * `(biz_type='DEVICE' or biz_type is null)` —— 这种 NULL 语义的坑
 * 这个项目已经踩过两次（device_type 的 NOT NULL、lifecycle_status 的 NULL），
 * 不值得为省一张表再冒一次险。
 *
 * <p>真正难的部分（落盘、路径校验、扩展名白名单）都在 FileStorageService 里，
 * 两张表共用，所以这里的重复只是几个字段和图省事的 CRUD。
 */
@Entity
@Table(name = "repair_attachment", indexes = {
        @Index(name = "idx_repair_attachment_repair", columnList = "repair_id")
})
public class RepairAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属工单 id */
    @Column(name = "repair_id", nullable = false)
    private Long repairId;

    /** 用户上传时的原始文件名，用于展示和下载时还原 */
    @Size(max = 200, message = "文件名不能超过 200 个字符")
    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    /** 磁盘上的真实文件名（UUID + 扩展名，纯 ASCII），服务端生成 */
    @Column(name = "stored_name", nullable = false, length = 100, unique = true)
    private String storedName;

    @Size(max = 100, message = "文件类型不能超过 100 个字符")
    @Column(name = "content_type", length = 100)
    private String contentType;

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

    public Long getRepairId() {
        return repairId;
    }

    public void setRepairId(Long repairId) {
        this.repairId = repairId;
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
