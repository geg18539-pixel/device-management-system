package com.yan.backend.service;

import com.yan.backend.dto.StoredFileVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件的磁盘存取。
 *
 * <p>只管"文件怎么落到磁盘、怎么读回来"，不涉及任何业务表 ——
 * 设备附件、以后的工单照片都复用它。
 */
public interface FileStorageService {

    /**
     * 保存一个上传的文件。
     *
     * <p>会做三件事：校验大小和扩展名、生成不冲突的服务端文件名、落盘。
     * 校验不通过直接抛 IllegalArgumentException（会被映射成 400）。
     */
    StoredFileVO store(MultipartFile file);

    /**
     * 按服务端文件名读回文件。
     *
     * <p>传入的名字会先做**严格格式校验**才允许拼路径 ——
     * 这个参数最终来自 URL，不校验就等于把目录穿越漏洞开在公网上。
     */
    Resource load(String storedName);

    /** 删除磁盘文件。文件不存在时静默返回，不抛异常 */
    void delete(String storedName);
}
