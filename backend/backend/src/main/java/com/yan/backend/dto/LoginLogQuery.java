package com.yan.backend.dto;

import java.time.LocalDate;

/** 登录日志的查询条件 */
public class LoginLogQuery {

    /** 按尝试登录的用户名模糊搜 */
    private String username;

    private String ip;

    /** 只看成功或只看失败。null 表示不限 */
    private Boolean success;

    /** 登录时间范围，格式 YYYY-MM-DD */
    private LocalDate loginTimeBegin;
    private LocalDate loginTimeEnd;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public LocalDate getLoginTimeBegin() {
        return loginTimeBegin;
    }

    public void setLoginTimeBegin(LocalDate loginTimeBegin) {
        this.loginTimeBegin = loginTimeBegin;
    }

    public LocalDate getLoginTimeEnd() {
        return loginTimeEnd;
    }

    public void setLoginTimeEnd(LocalDate loginTimeEnd) {
        this.loginTimeEnd = loginTimeEnd;
    }
}
