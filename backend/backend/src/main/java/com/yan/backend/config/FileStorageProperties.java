package com.yan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 附件存储配置（对应 application.yml 里的 app.file.*）。
 *
 * <p>抽成配置类而不是在服务里散落 @Value，是因为这几个值彼此有关联、
 * 而且**必须在启动时就确定**（比如上传目录能不能创建）。配置类能让 Spring
 * 在启动阶段就把值绑好，配错了立刻报错，而不是等用户第一次上传才炸。
 */
@Component
@ConfigurationProperties(prefix = "app.file")
public class FileStorageProperties {

    /**
     * 附件存放目录。
     *
     * <p>默认是运行目录下的 uploads。**这个目录不在 Web 静态资源路径下**，
     * 所以文件不会被直接暴露成 URL —— 下载必须走带鉴权的接口。
     * Docker 部署时要把它挂成卷，否则容器重建就丢了。
     */
    private String uploadDir = "uploads";

    /** 单个文件大小上限（MB）。Spring 的 multipart 上限也要配套设成不小于这个值 */
    private long maxSizeMb = 10;

    /**
     * 允许上传的扩展名白名单。
     *
     * <p><b>白名单而不是黑名单</b>：黑名单永远列不全（.jsp/.jspx/.phtml/.svg...），
     * 漏一个就是漏洞。这里只放行图片、Office 文档和 PDF 这些"设备资料"真正会用到的类型。
     *
     * <p>特别注意**故意排除 svg 和 html**：它们能被浏览器当页面渲染，
     * 内嵌脚本就等于给上传者开了一个存储型 XSS 的口子。
     */
    private List<String> allowedExtensions = List.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "md");

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public long getMaxSizeMb() {
        return maxSizeMb;
    }

    public void setMaxSizeMb(long maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    /** 字节形式的大小上限，方便和 MultipartFile.getSize() 直接比较 */
    public long getMaxSizeBytes() {
        return maxSizeMb * 1024 * 1024;
    }
}
