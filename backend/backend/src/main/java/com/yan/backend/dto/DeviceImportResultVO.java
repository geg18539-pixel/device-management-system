package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入的结果。
 *
 * <p><b>采用"部分成功"而不是"全成功或全失败"</b>，和用户批量操作的策略一致：
 * 1000 行里有 3 行格式不对，不该让其余 997 行一起失败 ——
 * 但也不能静默跳过，必须明确告诉用户"成功了几个、哪几行为什么没进"。
 */
public class DeviceImportResultVO {

    /** 文件里的数据行数（不含表头、不含完全空白的行） */
    private int total;

    /** 成功导入的条数 */
    private int successCount;

    /** 失败的条数 */
    private int failCount;

    /**
     * 失败明细。**最多只返回若干条** ——
     * 一个格式全错的文件可能有上千条错误，全塞进响应既没意义又会撑爆前端。
     * 被截断时 {@link #errorsTruncated} 为 true，页面提示用户去看前几条的模式。
     */
    private List<FailedRow> errors = new ArrayList<>();

    /** 失败明细是否被截断 */
    private boolean errorsTruncated;

    /** 提示信息，比如"部门列有 3 个名称在系统里找不到"这类需要用户注意的事 */
    private List<String> warnings = new ArrayList<>();

    /** 一行失败的原因 */
    public static class FailedRow {
        /** Excel 里的行号（**从 1 开始，含表头**，和用户在 Excel 里看到的一致） */
        private int rowNum;
        /** 设备名称，便于用户在表格里定位 */
        private String deviceName;
        /** 失败原因 */
        private String reason;

        public FailedRow() {
        }

        public FailedRow(int rowNum, String deviceName, String reason) {
            this.rowNum = rowNum;
            this.deviceName = deviceName;
            this.reason = reason;
        }

        public int getRowNum() {
            return rowNum;
        }

        public void setRowNum(int rowNum) {
            this.rowNum = rowNum;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    // ---------- getter / setter ----------

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<FailedRow> getErrors() {
        return errors;
    }

    public void setErrors(List<FailedRow> errors) {
        this.errors = errors;
    }

    public boolean isErrorsTruncated() {
        return errorsTruncated;
    }

    public void setErrorsTruncated(boolean errorsTruncated) {
        this.errorsTruncated = errorsTruncated;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
