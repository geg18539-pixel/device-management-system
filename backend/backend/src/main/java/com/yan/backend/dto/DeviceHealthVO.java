package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 一台设备的健康评估结果。
 *
 * <p>除了分数本身，还带上一串 {@code reasons}（扣分原因）。
 * 这是**故意的**：一个没有解释的分数没人敢信，也没法据它做决策。
 * 运维看到「62 分」的第一反应是"凭什么"，看到
 * 「62 分 · 使用 5 年 · 累计维修 6 次 · 近 90 天故障 2 次」才会去处理。
 *
 * <p>{@code grade} 返回的是中文等级（良好 / 关注 / 高风险）而不是颜色。
 * 颜色属于展示层的事，由前端把它映射成状态铭牌的色调 ——
 * 和 DEVICE_LIFECYCLE_TONE、MSG_TYPE_META 是同一套分工。
 */
public class DeviceHealthVO {

    public static final String GRADE_GOOD = "良好";
    public static final String GRADE_WATCH = "关注";
    public static final String GRADE_RISK = "高风险";

    private Long deviceId;
    private String deviceName;
    private String assetCode;
    /** 所属部门名。未分配时是「未分配」 */
    private String deptName;
    /** 资产生命周期状态（正常 / 维修 / 报废 / 停用） */
    private String lifecycleStatus;

    /** 健康分，0~100，越高越健康 */
    private int score;
    /** 等级：良好 / 关注 / 高风险 */
    private String grade;
    /** 扣分原因，人话，按扣分从多到少排 */
    private List<String> reasons = new ArrayList<>();

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
