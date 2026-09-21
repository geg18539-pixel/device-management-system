package com.yan.backend.service.impl;

import com.yan.backend.ai.provider.ChatMessage;
import com.yan.backend.ai.provider.ChatRequest;
import com.yan.backend.ai.provider.LlmProvider;
import com.yan.backend.ai.provider.LlmRoundResult;
import com.yan.backend.common.ConfigKeys;
import com.yan.backend.dto.DashboardDigestVO;
import com.yan.backend.dto.DashboardStatsVO;
import com.yan.backend.dto.DeviceHealthVO;
import com.yan.backend.ai.provider.AiProviderRegistry;
import com.yan.backend.service.AiSettingsService;
import com.yan.backend.service.DashboardDigestService;
import com.yan.backend.service.DashboardService;
import com.yan.backend.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 首页 AI 摘要。
 *
 * <h3>输入是"已经算好的数字"，不是让模型自己去查</h3>
 *
 * <p>摘要的输入直接取 {@link DashboardService#stats()} —— 也就是看板上
 * 那一份数据。这样做有两个好处：一是**口径必然一致**，摘要里的数字和
 * 旁边卡片上的数字出自同一份统计，不可能出现"摘要说 6 台、卡片说 7 台"；
 * 二是**小模型也答不歪**，它拿到的是一段事实，只需要组织语言，
 * 不需要判断"该查什么"。让 3B 模型自己决定查库，它连表名都未必选对。
 *
 * <h3>⚠️ 异步是必须的，而且不能靠 @Async 自调用</h3>
 *
 * <p>一次生成要几十秒。这里用的是**显式提交到线程池**
 * （{@link TaskExecutor#execute}），不是 {@code @Async} 注解 ——
 * 因为 {@code requestGenerate()} 和真正干活的方法在同一个类里，
 * 自调用不走代理，{@code @Async} 会**静默失效**、变成同步执行，
 * 把 HTTP 请求线程卡住几十秒。
 * （这个坑项目里已经踩过三次：{@code OperLogRecorder}、AI 工具执行、数据种子。）
 * 显式提交还顺带让"异步的边界在哪一行"一眼可见。
 *
 * <h3>⚠️ 绝不能加 @Transactional</h3>
 *
 * <p>方法里有一次几十秒的外部 HTTP 调用。包在事务里的话，这段时间会一直
 * 占着一条数据库连接 —— Hikari 默认池只有 10 条，几次生成就能把池占满，
 * 整个系统开始排队。（AI 故障分析那边踩过同一个坑。）
 */
@Service
public class DashboardDigestServiceImpl implements DashboardDigestService {

    private static final Logger log = LoggerFactory.getLogger(DashboardDigestServiceImpl.class);

    /**
     * 两次生成之间的最小间隔。
     *
     * <p>拦的是"手动刷新按钮被连点"。定时任务也走同一个入口，
     * 但它是每天一次，永远不会撞上这个限制。
     */
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(2);

    /** 提示词里最多列几台健康分偏低的设备。多了这段就喧宾夺主了 */
    private static final int MAX_HEALTH_DEVICES = 5;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy年M月d日");

    private final DashboardService dashboardService;
    private final AiProviderRegistry providerRegistry;
    private final AiSettingsService aiSettings;
    private final SysConfigService configService;
    private final TaskExecutor digestExecutor;

    /**
     * 最近一次生成的结果。
     *
     * <p>用 {@link AtomicReference} 而不是 volatile 字段：摘要正文和它的生成时间、
     * 模型名必须**一起**被换掉。分开写的话，读线程可能拿到"新正文 + 旧时间"，
     * 于是页面上标着错的时间。
     */
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.never());

    /** 生成中的标志。挡住并发触发，也用来给前端显示"正在生成" */
    private final AtomicBoolean generating = new AtomicBoolean(false);

    /** 最近一次启动生成的时刻，用于节流 */
    private final AtomicReference<Instant> lastRequestedAt = new AtomicReference<>();

    public DashboardDigestServiceImpl(DashboardService dashboardService,
                                      AiProviderRegistry providerRegistry,
                                      AiSettingsService aiSettings,
                                      SysConfigService configService,
                                      @Qualifier("digestExecutor") TaskExecutor digestExecutor) {
        this.dashboardService = dashboardService;
        this.providerRegistry = providerRegistry;
        this.aiSettings = aiSettings;
        this.configService = configService;
        this.digestExecutor = digestExecutor;
    }

    // ============================================================
    // 读
    // ============================================================

    @Override
    public DashboardDigestVO current() {
        DashboardDigestVO vo = new DashboardDigestVO();

        boolean enabled = enabled();
        vo.setEnabled(enabled);
        vo.setGenerating(generating.get());

        if (!enabled) {
            vo.setMessage("首页 AI 摘要已关闭");
            return vo;
        }

        Snapshot snap = snapshot.get();
        if (StringUtils.hasText(snap.text())) {
            // 有正文就展示，哪怕最近一次生成失败了 ——
            // 正文配着 generatedAt 一起显示，用户看得出它是"截至什么时候"的
            vo.setAvailable(true);
            vo.setText(snap.text());
            vo.setGeneratedAt(snap.generatedAt());
            vo.setModel(snap.model());
            if (snap.error() != null) {
                vo.setMessage("最近一次生成失败（" + snap.error() + "），以上是上一次的内容");
            }
            return vo;
        }

        // 没有正文：说清楚是哪一种"没有"
        vo.setAvailable(false);
        if (generating.get()) {
            vo.setMessage("正在生成今日摘要…");
        } else if (snap.error() != null) {
            vo.setMessage("摘要生成失败：" + snap.error());
        } else {
            vo.setMessage("今日摘要尚未生成");
        }
        return vo;
    }

    // ============================================================
    // 写（触发）
    // ============================================================

    @Override
    public boolean requestGenerate() {
        if (!enabled()) {
            log.info("首页 AI 摘要已关闭（系统参数 {}），跳过生成",
                    ConfigKeys.DASHBOARD_DIGEST_ENABLED);
            return false;
        }

        Instant last = lastRequestedAt.get();
        if (last != null && Duration.between(last, Instant.now()).compareTo(MIN_INTERVAL) < 0) {
            log.info("首页 AI 摘要距上次生成不足 {} 分钟，本次跳过", MIN_INTERVAL.toMinutes());
            return false;
        }

        if (!generating.compareAndSet(false, true)) {
            log.info("首页 AI 摘要正在生成中，忽略本次触发");
            return false;
        }

        try {
            digestExecutor.execute(this::generateNow);
        } catch (RuntimeException e) {
            // 提交失败（线程池已关闭、队列满）。必须把标志放回去，
            // 否则这个功能会永久卡在"正在生成"，之后再也不会生成
            generating.set(false);
            log.warn("首页 AI 摘要任务提交失败，本次跳过：{}", e.getMessage());
            return false;
        }

        lastRequestedAt.set(Instant.now());
        return true;
    }

    // ============================================================
    // 真正的生成（跑在 digestExecutor 线程上）
    // ============================================================

    private void generateNow() {
        String model = aiSettings.chatModel();
        try {
            DashboardStatsVO stats = dashboardService.stats();
            String text = callModel(stats, model);

            snapshot.set(new Snapshot(text, LocalDateTime.now(), model, null));
            log.info("首页 AI 摘要已生成：{} 字，模型 {}，输入为看板统计（设备 {} 台 / 待处理工单 {} 条）",
                    text.length(), model, stats.getDeviceTotal(), stats.getRepairPending());

        } catch (Exception e) {
            // 摘要失败绝不能影响任何别的功能 —— 它只是首页上的一张卡片。
            // 保留上一次的正文（如果有），只把失败原因挂上去
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Snapshot old = snapshot.get();
            snapshot.set(new Snapshot(old.text(), old.generatedAt(), old.model(), reason));
            log.warn("首页 AI 摘要生成失败（首页其余部分不受影响）：{}", reason);

        } finally {
            generating.set(false);
        }
    }

    private String callModel(DashboardStatsVO stats, String model) {
        LlmProvider provider = providerRegistry.currentLlm();

        List<ChatMessage> messages = List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(buildDataBlock(stats)));

        // ⚠️ 和故障分析同理，这里也**固定关掉思考**：摘要的 maxTokens 只有 300，
        // 而它的任务只是"把看板上已经算好的数字组织成人话"，没有需要推理的东西。
        // 让会思考的模型跑这个，它会把预算全花在中间过程上，最后交不出摘要
        ChatRequest request = ChatRequest.plain(model, messages,
                aiSettings.digestTemperature(), aiSettings.digestMaxTokens(), Boolean.FALSE);

        // 这里传一个空回调：摘要是缓存起来一次性给前端的，不需要流式。
        // 但 LlmProvider 只有流式这一个"拿文本"的入口，所以把片段丢掉、用返回值 ——
        // 为此多加一个非流式方法不值得，各家提供方都要多实现一遍。
        LlmRoundResult result = provider.streamRound(request, chunk -> { });

        String text = clean(result.text());
        if (!StringUtils.hasText(text)) {
            // 空回复必须当失败。否则会把一个空字符串存进缓存，
            // 前端拿到 available=true 却渲染出一片空白，看起来像界面坏了
            throw new IllegalStateException("模型没有返回任何内容");
        }
        return text;
    }

    // ============================================================
    // 提示词
    // ============================================================

    private static final String SYSTEM_PROMPT = """
            你是设备管理系统的值班助手，为设备科管理员写每日设备状况简报。

            只依据下面给出的数据写，数据里没有的一个字都不要编。
            判断不了的事情不要写，哪怕它听起来很合理。

            输出要求：
            1. 用 2 到 4 句中文，写成一段自然的话。
            2. 先说真正需要关注的事（逾期、待处理、库存告急、健康分偏低），
               正常的部分一笔带过。全部正常就简短说一句今日无异常。
            3. 数字照抄，不要换算、不要估算、不要四舍五入。
            4. 不要把所有数字都念一遍 —— 看板就在它旁边，用户自己看得到。
               你要做的是指出哪里值得去看。
            5. 直接输出正文。不要标题、不要列表符号、不要 Markdown 标记、不要开场白。
            """;

    /**
     * 把看板统计拼成一段事实。
     *
     * <p>刻意写成"带标签的分行事实"而不是一大段话：小模型对结构化输入的
     * 数字搬运更准，也不太会把不同维度的数字串到一起。
     */
    private String buildDataBlock(DashboardStatsVO s) {
        StringBuilder sb = new StringBuilder();
        sb.append("下面是设备管理系统的今日数据。\n");
        sb.append("今天是 ").append(LocalDate.now().format(DATE_FMT)).append("。\n\n");

        sb.append("设备资产：总共 ").append(s.getDeviceTotal()).append(" 台。")
                .append("正常 ").append(s.getLifecycleNormal()).append(" 台，")
                .append("维修 ").append(s.getLifecycleRepair()).append(" 台，")
                .append("停用 ").append(s.getLifecycleDisabled()).append(" 台，")
                .append("报废 ").append(s.getLifecycleScrapped()).append(" 台。\n");

        sb.append("借出在外：").append(s.getBorrowedCount()).append(" 台。\n");

        sb.append("维修工单：共 ").append(s.getRepairTotal()).append(" 条，")
                .append("待处理 ").append(s.getRepairPending()).append(" 条，")
                .append("已完成 ").append(s.getRepairFinished()).append(" 条，")
                .append("已关闭 ").append(s.getRepairClosed()).append(" 条，")
                .append("完成率 ").append(s.getRepairCompletionRate()).append("%。\n");

        sb.append("维保计划：").append(s.getMaintenanceDueCount())
                .append(" 个即将到期或已经逾期（提前 ").append(s.getMaintenanceWarnDays()).append(" 天预警）。\n");

        sb.append("厂商保修：").append(s.getWarrantyExpiringCount())
                .append(" 台设备即将过保或已经过保（提前 ").append(s.getWarrantyWarnDays()).append(" 天预警）。\n");

        sb.append("配件库存：").append(s.getLowStockCount()).append(" 种配件库存告急。\n");

        sb.append("设备健康：").append(s.getHealthRiskCount()).append(" 台设备健康分偏低。");
        List<DeviceHealthVO> risky = s.getHealthRiskDevices();
        if (risky != null && !risky.isEmpty()) {
            List<String> parts = risky.stream()
                    .limit(MAX_HEALTH_DEVICES)
                    .map(d -> d.getDeviceName() + "（" + d.getScore() + " 分，"
                            + (StringUtils.hasText(d.getGrade()) ? d.getGrade() : "待评估") + "）")
                    .toList();
            sb.append("分数最低的几台：").append(String.join("、", parts)).append("。");
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * 清理模型返回的正文。
     *
     * <p>模型（尤其是小模型）很容易不受要求地加上 ``` 代码围栏或
     * "好的，以下是今日摘要："这类开场白。前端直接渲染的话会很难看，
     * 而在前端做清理等于把这套规则复制一份到另一个语言里，迟早对不上。
     */
    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();

        // 去掉整段被 ``` 包起来的情况
        if (text.startsWith("```")) {
            int firstBreak = text.indexOf('\n');
            if (firstBreak > 0) {
                text = text.substring(firstBreak + 1);
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
            text = text.trim();
        }

        // 去掉成对的引号（有的模型会把答案用引号括起来）
        if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).trim();
        }

        return text;
    }

    private boolean enabled() {
        return configService.getBoolean(ConfigKeys.DASHBOARD_DIGEST_ENABLED,
                ConfigKeys.DASHBOARD_DIGEST_ENABLED_DEFAULT);
    }

    /**
     * 一次生成的结果。
     *
     * <p>{@code error} 和 {@code text} **可以同时非空**：那表示这次生成失败了，
     * 但上一次的正文还在，页面上于是显示"旧正文 + 一行失败提示"。
     */
    private record Snapshot(String text, LocalDateTime generatedAt, String model, String error) {

        /** 从来没生成过 */
        static Snapshot never() {
            return new Snapshot(null, null, null, null);
        }
    }
}
