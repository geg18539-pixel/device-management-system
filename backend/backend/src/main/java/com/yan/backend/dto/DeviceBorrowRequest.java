package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 借用请求 */
public class DeviceBorrowRequest {

    @NotBlank(message = "借用人不能为空")
    @Size(max = 50, message = "借用人不能超过 50 个字符")
    private String borrower;

    @Size(max = 500, message = "借用说明不能超过 500 个字符")
    private String remark;

    public String getBorrower() {
        return borrower;
    }

    public void setBorrower(String borrower) {
        this.borrower = borrower;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
