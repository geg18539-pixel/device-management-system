package com.yan.backend.service;

import com.yan.backend.dto.DeviceImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

public interface DeviceImportService {

    /**
     * 从 Excel 批量导入设备。
     *
     * <p><b>逐行处理，部分成功</b>：格式正确的行照常入库，错误的行跳过并在结果里列出来。
     */
    DeviceImportResultVO importDevices(MultipartFile file);

    /** 输出导入模板（含示例行和填写说明） */
    void writeTemplate(OutputStream outputStream);
}
