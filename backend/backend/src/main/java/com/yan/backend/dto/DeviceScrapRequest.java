package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 设备报废请求。
 *
 * <p>{@code scrapDate} 允许为空，为空时按今天处理 ——
 * 大部分情况就是今天报的，不该逼用户每次都去点一次日期。
 */
public class DeviceScrapRequest {

    /** 报废日期。为空按今天算 */
    private LocalDate scrapDate;

    @NotBlank(message = "报废原因不能为空")
    @Size(max = 200, message = "报废原因不能超过 200 个字符")
    private String reason;

    public LocalDate getScrapDate() {
        return scrapDate;
    }

    public void setScrapDate(LocalDate scrapDate) {
        this.scrapDate = scrapDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
