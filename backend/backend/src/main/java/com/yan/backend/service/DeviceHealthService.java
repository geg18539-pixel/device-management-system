package com.yan.backend.service;

import com.yan.backend.dto.DeviceHealthSummaryVO;
import com.yan.backend.dto.DeviceHealthVO;

/**
 * 设备健康评估。
 *
 * <p><b>刻意不落表。</b> 健康分是**纯派生数据** ——
 * 由机龄、维修记录、费用这些随时会变的源数据算出来。
 * 一旦落表就立刻要回答"什么时候更新它"：设备改一次、工单完一次、
 * 配件出一次库，分数就过期了，而漏更新**不会报错**，
 * 只会让看板上显示一个和事实不符的分数。
 * 按需计算 + 结果只活在当次请求里，从结构上就不可能不同步。
 *
 * <p>（真要做"健康分趋势曲线"时才需要历史表，那是另一个需求。）
 *
 * <p>这里没有任何 AI 参与：分数完全由业务数据算出来，
 * 可复现、可解释、不依赖模型的稳定性和速度。
 * 演示项目里，一个"说得出为什么"的分数比大模型估的分数有用得多。
 */
public interface DeviceHealthService {

    /**
     * 风险设备摘要：总数 + 最差的若干台（按分数升序，最差的在最前）。
     *
     * @param limit 列表最多返回几台。总数不受它影响
     */
    DeviceHealthSummaryVO summary(int limit);

    /** 算一台设备的健康分。设备详情页用 */
    DeviceHealthVO evaluate(Long deviceId);
}
