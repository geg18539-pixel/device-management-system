package com.yan.backend.dto;

import java.time.LocalDate;

/**
 * 操作日志的查询条件。
 *
 * <p>时间用 LocalDate（按天）而不是精确到秒：看日志的人问的是
 * "昨天下午谁删了东西"，不是"13:42:07.331 到 13:47:02.918 之间"。
 * 按天还能顺带避免一个经典坑 —— 结束日期必须取当天 23:59:59，
 * 否则"查到 9 月 20 日"会把 9 月 20 日当天的记录全漏掉。
 */
public class OperLogQuery {

    private int pageNum = 1;
    private int pageSize = 20;

    /** 操作人用户名，模糊匹配 */
    private String operatorName;

    /** 操作模块，取自 @Log 的 title */
    private String title;

    /** 业务类型：INSERT / UPDATE / DELETE / EXPORT / OTHER */
    private String businessType;

    /** 执行结果：成功 / 失败 */
    private String status;

    private LocalDate beginDate;
    private LocalDate endDate;

    /** 关键词：请求 URL 或方法名 */
    private String keyword;

    // ---------- getter / setter ----------

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getBeginDate() {
        return beginDate;
    }

    public void setBeginDate(LocalDate beginDate) {
        this.beginDate = beginDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
