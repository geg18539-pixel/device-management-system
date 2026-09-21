package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备健康摘要：风险设备总数 + 最差的若干台。
 *
 * <p>合在一个类里返回，是因为两者要用**同一份**聚合数据算
 * （要先把全库设备的维修汇总查出来才能评分）。
 * 拆成两个接口的话，看板加载一次就要把那份聚合查两遍 ——
 * 而且更糟的是，两次调用之间数据可能变，出现
 * 「卡片说 7 台有风险、列表里却列了 8 条」这种对不上。
 */
public class DeviceHealthSummaryVO {

    /** 健康分低于阈值的设备总数（不受列表条数限制） */
    private long riskCount;

    /** 风险最高的若干台，按分数升序。看板只展示这几条 */
    private List<DeviceHealthVO> riskDevices = new ArrayList<>();

    public long getRiskCount() {
        return riskCount;
    }

    public void setRiskCount(long riskCount) {
        this.riskCount = riskCount;
    }

    public List<DeviceHealthVO> getRiskDevices() {
        return riskDevices;
    }

    public void setRiskDevices(List<DeviceHealthVO> riskDevices) {
        this.riskDevices = riskDevices;
    }
}
