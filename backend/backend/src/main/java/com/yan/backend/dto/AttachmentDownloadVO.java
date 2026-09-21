package com.yan.backend.dto;

import org.springframework.core.io.Resource;

/**
 * 一次附件下载所需的全部信息。
 *
 * <p>把 Resource 和"该用什么文件名、什么 Content-Type 返回"打包在一起：
 * 这些只有查到数据库记录才知道（磁盘上的名字是 UUID，用户看到的名字在库里），
 * 而 Controller 层不该再去查一次库。
 */
public class AttachmentDownloadVO {

    private Resource resource;

    /** 返回给浏览器的文件名（原始名，可能含中文） */
    private String fileName;

    private String contentType;

    private long size;

    public AttachmentDownloadVO() {
    }

    public AttachmentDownloadVO(Resource resource, String fileName, String contentType, long size) {
        this.resource = resource;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
