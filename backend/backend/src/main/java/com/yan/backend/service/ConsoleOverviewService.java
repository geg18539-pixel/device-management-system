package com.yan.backend.service;

import com.yan.backend.dto.ConsoleOverviewVO;

/**
 * 管理后台首页的系统概览。
 *
 * <p>和 {@link DashboardService} 是两件事：那个看的是**业务**（设备、工单、维保），
 * 这个看的是**系统本身**（账号、登录、越权、知识库）。
 */
public interface ConsoleOverviewService {

    /** 一次返回首页要的全部数字 */
    ConsoleOverviewVO overview();
}
