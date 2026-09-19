package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量操作的执行结果。
 *
 * <p>批量操作**不能是"要么全成功要么全失败"**：删 10 个用户里有 1 个是内置 admin，
 * 全部回滚不合理（用户想删的另外 9 个明明可以删），静默跳过也不好（用户以为删干净了）。
 * 所以返回"成功几个 + 哪几个被跳过、为什么"，前端如实展示。
 */
public class BatchResultVO {

    /** 成功处理的条数 */
    private int successCount;

    /** 被跳过的条目及原因 */
    private List<SkippedItem> skipped = new ArrayList<>();

    public BatchResultVO() {
    }

    public BatchResultVO(int successCount, List<SkippedItem> skipped) {
        this.successCount = successCount;
        this.skipped = skipped;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public List<SkippedItem> getSkipped() {
        return skipped;
    }

    public void setSkipped(List<SkippedItem> skipped) {
        this.skipped = skipped;
    }

    /** 被跳过的一条：用用户名而不是 id，让用户能直接看懂是哪一个 */
    public record SkippedItem(Long id, String username, String reason) {
    }
}
