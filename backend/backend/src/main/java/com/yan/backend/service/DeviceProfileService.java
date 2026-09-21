package com.yan.backend.service;

import com.yan.backend.dto.DeviceProfileVO;

public interface DeviceProfileService {

    /** 设备档案详情：基础信息 + 维保 + 工单 + 配件 + 附件 + 调拨，一次取全 */
    DeviceProfileVO profile(Long deviceId);
}
