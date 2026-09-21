package com.yan.backend.service;

import com.yan.backend.dto.DashboardStatsVO;

public interface DashboardService {

    /** 首页看板的全部统计数据，一次返回 */
    DashboardStatsVO stats();
}
