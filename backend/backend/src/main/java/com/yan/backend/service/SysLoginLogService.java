package com.yan.backend.service;

import com.yan.backend.dto.LoginLogQuery;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysLoginLog;

public interface SysLoginLogService {

    PageResult<SysLoginLog> page(LoginLogQuery query, int pageNum, int pageSize);
}
