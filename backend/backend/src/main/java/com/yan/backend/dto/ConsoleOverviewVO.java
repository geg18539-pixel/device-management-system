package com.yan.backend.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台首页的数据。
 *
 * <h3>和首页看板的区别</h3>
 *
 * <p>看板回答的是「**设备**现在怎么样」（业务数据），
 * 这个回答的是「**系统**现在怎么样」（账号、权限、日志、配置）。
 * 两者的受众、口径、落地页都不一样，所以是两个接口而不是一个。
 *
 * <h3>为什么把「需要关注」放在最前面</h3>
 *
 * <p>纯数字罗列（"用户 12 个、角色 2 个、部门 5 个"）看一次就没价值了 ——
 * 它不会变，也不需要处理。真正值得管理员每天看一眼的是
 * **异常的那几个数**：今天有多少次登录失败（撞库信号）、
 * 多少次越权被拒（有人在试不该试的接口）、有没有知识库文档处理失败。
 * 那些是 0 的时候看着安心，不是 0 的时候就是要处理的。
 */
public class ConsoleOverviewVO {

    // ---------- 需要关注（异常时才值得看） ----------

    /** 今天登录失败次数。**这是最该盯的一个数**：突然变多通常是撞库 */
    private long loginFailToday;

    /** 今天登录成功次数。给失败次数一个参照 —— 失败 3 次在总共 5 次和总共 500 次里含义完全不同 */
    private long loginSuccessToday;

    /** 今天被拦截的越权访问次数（拦截器拦下时写进操作日志的那些） */
    private long deniedToday;

    /** 停用的账号数 */
    private long disabledUsers;

    /** 知识库里处理失败的文档数。**这类不会自愈**，要人工点「重新处理」 */
    private long knowledgeFailed;

    // ---------- 规模（看一眼就知道系统有多大） ----------

    private long userTotal;
    private long roleTotal;
    private long deptTotal;
    private long knowledgeTotal;

    // ---------- 最近登录 ----------

    /** 最近若干条登录记录，最近的在最前 */
    private List<LoginRecord> recentLogins = new ArrayList<>();

    /**
     * 一条登录记录。
     *
     * <p>**刻意不返回 userAgent**：那是给服务端做设备识别的原料，
     * 又长又原始，界面上要的是已经归好类的 {@code device}。
     */
    public record LoginRecord(String username,
                              String ip,
                              String device,
                              boolean success,
                              String failReason,
                              LocalDateTime loginTime) {}

    // ---------- getter / setter ----------

    public long getLoginFailToday() {
        return loginFailToday;
    }

    public void setLoginFailToday(long loginFailToday) {
        this.loginFailToday = loginFailToday;
    }

    public long getLoginSuccessToday() {
        return loginSuccessToday;
    }

    public void setLoginSuccessToday(long loginSuccessToday) {
        this.loginSuccessToday = loginSuccessToday;
    }

    public long getDeniedToday() {
        return deniedToday;
    }

    public void setDeniedToday(long deniedToday) {
        this.deniedToday = deniedToday;
    }

    public long getDisabledUsers() {
        return disabledUsers;
    }

    public void setDisabledUsers(long disabledUsers) {
        this.disabledUsers = disabledUsers;
    }

    public long getKnowledgeFailed() {
        return knowledgeFailed;
    }

    public void setKnowledgeFailed(long knowledgeFailed) {
        this.knowledgeFailed = knowledgeFailed;
    }

    public long getUserTotal() {
        return userTotal;
    }

    public void setUserTotal(long userTotal) {
        this.userTotal = userTotal;
    }

    public long getRoleTotal() {
        return roleTotal;
    }

    public void setRoleTotal(long roleTotal) {
        this.roleTotal = roleTotal;
    }

    public long getDeptTotal() {
        return deptTotal;
    }

    public void setDeptTotal(long deptTotal) {
        this.deptTotal = deptTotal;
    }

    public long getKnowledgeTotal() {
        return knowledgeTotal;
    }

    public void setKnowledgeTotal(long knowledgeTotal) {
        this.knowledgeTotal = knowledgeTotal;
    }

    public List<LoginRecord> getRecentLogins() {
        return recentLogins;
    }

    public void setRecentLogins(List<LoginRecord> recentLogins) {
        this.recentLogins = recentLogins;
    }
}
