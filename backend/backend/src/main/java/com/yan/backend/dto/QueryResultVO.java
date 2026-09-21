package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次自然语言问数的结果。
 *
 * <h3>为什么必须回显「我理解成什么」</h3>
 *
 * <p>{@link #understanding} 是模型自己复述的"我理解你在问什么"，
 * <b>一定要显示给用户</b>。小模型一定会把问题理解错 —— 关键是让它错的时候
 * 用户一眼就能看出来，而不是对着一个莫名其妙的结果反复猜"是不是系统坏了"。
 * 看出理解错了，他能马上换个说法再问一次，成本很低。
 *
 * <h3>为什么行是字符串而不是数字</h3>
 *
 * <p>聚合结果里既有"5"这种整数，也有"1234.50"这种金额，还有部门名这种纯文本。
 * 统一转成字符串之后，前端不需要为每种指标各写一套格式化逻辑，
 * 也避免了"金额显示成 1234.5 少了个零"这类只在某个指标上出现的小毛病。
 * 排序已经在后端做完了（按数值倒序），前端只管画。
 */
public class QueryResultVO {

    /** 用户原话，回显用 */
    private String question;

    /** 模型复述的理解。**必须显示** —— 见类注释 */
    private String understanding;

    /** 用到的指标名，如「设备数量」 */
    private String metricLabel;

    /** 分组维度名。没分组时为 null */
    private String groupByLabel;

    /** 是不是清单形态（决定前端怎么摆放：清单是表格，聚合是"标签 + 数字"） */
    private boolean listMode;

    /** 表头 */
    private List<String> columns = new ArrayList<>();

    /** 数据行。**行和列都是字符串** */
    private List<List<String>> rows = new ArrayList<>();

    /** 实际返回的行数 */
    private int rowCount;

    /** 是否被上限截断 */
    private boolean truncated;

    /**
     * 后端拼的一句人话结论（不调模型，纯模板）。
     *
     * <p>刻意**不再调一次模型**去组织语言：这里的数据量很小、结构固定，
     * 模板拼出来的既准确又快，而且同样的话每次问都一模一样 ——
     * 而再调一次模型既慢又可能把数字说错。
     */
    private String summary;

    // ---------- getter / setter ----------

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getUnderstanding() {
        return understanding;
    }

    public void setUnderstanding(String understanding) {
        this.understanding = understanding;
    }

    public String getMetricLabel() {
        return metricLabel;
    }

    public void setMetricLabel(String metricLabel) {
        this.metricLabel = metricLabel;
    }

    public String getGroupByLabel() {
        return groupByLabel;
    }

    public void setGroupByLabel(String groupByLabel) {
        this.groupByLabel = groupByLabel;
    }

    public boolean isListMode() {
        return listMode;
    }

    public void setListMode(boolean listMode) {
        this.listMode = listMode;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
