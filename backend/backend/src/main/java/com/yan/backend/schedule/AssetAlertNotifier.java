package com.yan.backend.schedule;

import com.yan.backend.common.ConfigKeys;
import com.yan.backend.dto.DeviceHealthSummaryVO;
import com.yan.backend.dto.DeviceHealthVO;
import com.yan.backend.entity.SparePart;
import com.yan.backend.entity.SysMessage;
import com.yan.backend.entity.SysUser;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.DeviceHealthService;
import com.yan.backend.service.SparePartService;
import com.yan.backend.service.SysConfigService;
import com.yan.backend.service.SysMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 资产类告警：配件库存告急 + 设备健康预警。
 *
 * <p>和 {@link MaintenanceDueNotifier} 是同一套结构（每天定时 + 启动补扫 + bizKey 幂等），
 * 但**收件人的确定方式完全不同**，这是这批最需要注意的地方：
 *
 * <table border="1">
 *   <caption>两类通知的对比</caption>
 *   <tr><th></th><th>维保到期</th><th>库存告急 / 健康预警</th></tr>
 *   <tr><td>收件人</td><td>计划上的 maintainer 字段，**精确到人**</td>
 *       <td>**没有归属人**，只能按"谁能处理就通知谁"广播</td></tr>
 *   <tr><td>怎么定</td><td>直接读业务字段</td>
 *       <td>查拥有对应权限点的账号（+ 管理员）</td></tr>
 *   <tr><td>条数</td><td>一个计划一条</td><td>库存每件一条；健康**每天聚合成一条**</td></tr>
 * </table>
 *
 * <h3>⚠️ 多收件人时 bizKey 必须带上收件人 id</h3>
 *
 * <p>{@code sys_message.biz_key} 上有**全局唯一约束**。维保提醒是一个计划发给一个人，
 * 所以 bizKey 里不含收件人也没事；但这里是"一条内容发给好几个人"，
 * 如果 bizKey 只按业务 id + 日期拼，那么**第二个收件人会因为唯一约束冲突被静默跳过** ——
 * 表现是"告警只发给了第一个管理员，其余人什么都没有"，而且不报错。
 * 所以幂等键统一是 {@code 前缀 + 业务id + 收件人id + 日期}。
 *
 * <h3>为什么健康预警要聚合</h3>
 *
 * <p>一台设备的健康分偏低，会在低位**停留好几个月**。逐台逐天发的话，
 * 消息中心三天就被同一批设备刷屏，用户很快就开始无视它。
 * 聚合成"今天有 N 台需要关注"一条，数量变化才是有信息量的。
 */
@Component
public class AssetAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(AssetAlertNotifier.class);

    /** 幂等键前缀，和别的消息类型区分开 */
    private static final String BIZ_KEY_STOCK = "stock_low:";
    private static final String BIZ_KEY_HEALTH = "health_risk:";

    /** 健康预警正文里最多列几台。太多了这条消息本身就没人读完 */
    private static final int MAX_HEALTH_LIST = 5;

    /**
     * 「能处理库存告急」的人：能改配件（调阈值、停用）的和能出入库的。
     *
     * <p>这两个权限点合起来就是"负责库存的人"，和 SystemDataSeeder 里定义的一致。
     */
    private static final List<String> STOCK_PERMS = List.of("dev:part:edit", "dev:part:stock");

    /**
     * 「能处理设备健康问题」的人：能改设备档案的（也就是能安排检修、改资产状态的）。
     */
    private static final List<String> HEALTH_PERMS = List.of("dev:device:edit");

    /**
     * 启用状态的账号才收告警。
     *
     * <p>⚠️ 字面量和 {@code SysUser.status} / {@code SysMessageServiceImpl} 保持一致 ——
     * SysUser 上还没有这个常量，属于既有状况，不是这里新引入的。
     */
    private static final String USER_STATUS_ENABLED = "正常";

    /** 超管角色。⚠️ 和 {@code JwtInterceptor.SUPER_ADMIN_ROLE} 必须一致 —— 见 findReceivers */
    private static final String SUPER_ADMIN_ROLE = "admin";

    private final SparePartService sparePartService;
    private final DeviceHealthService deviceHealthService;
    private final SysMessageService messageService;
    private final SysConfigService configService;
    private final SysUserRepository userRepository;

    public AssetAlertNotifier(SparePartService sparePartService,
                              DeviceHealthService deviceHealthService,
                              SysMessageService messageService,
                              SysConfigService configService,
                              SysUserRepository userRepository) {
        this.sparePartService = sparePartService;
        this.deviceHealthService = deviceHealthService;
        this.messageService = messageService;
        this.configService = configService;
        this.userRepository = userRepository;
    }

    // ============================================================
    // 触发点
    // ============================================================

    /**
     * 每天早上 8:10 跑一次。
     *
     * <p>排在维保提醒（8:00）之后：维保到期是**推给具体责任人**的，
     * 时效性更强，先发。而且健康预警要遍历全库设备算分，和维保扫描错开
     * 能避免两个重任务同时压数据库。
     */
    @Scheduled(cron = "0 10 8 * * *")
    public void scheduledNotify() {
        try {
            AlertResult r = notifyAlerts();
            if (r.total() > 0) {
                log.info("资产告警定时任务：库存 {} 条、健康 {} 条", r.stockSent(), r.healthSent());
            }
        } catch (Exception e) {
            // 定时任务的异常绝不能往上抛：调度线程会把它吞掉并继续下次调度，
            // 但堆栈里看不出是哪次任务出的问题
            log.error("资产告警定时任务执行失败", e);
        }
    }

    /**
     * 应用启动完成时补扫一次。
     *
     * <p>和维保提醒同样的理由：应用关几天不开，那几天的告警就永远丢了。
     * 用 {@code ApplicationReadyEvent}（在种子数据之后）而不是 {@code CommandLineRunner}。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            AlertResult r = notifyAlerts();
            if (r.total() > 0) {
                log.info("启动补扫：资产告警已发出 库存 {} 条、健康 {} 条", r.stockSent(), r.healthSent());
            }
        } catch (Exception e) {
            // 启动补扫失败绝不能影响应用启动 —— 它只是个通知
            log.warn("启动时补扫资产告警失败，已跳过：{}", e.getMessage());
        }
    }

    // ============================================================
    // 两类告警
    // ============================================================

    /**
     * 跑一遍两类告警，各自独立。
     *
     * <p>**刻意不共用一个 try/catch**：库存那块因为数据异常挂了，
     * 不该把健康预警一起拖下水（和种子数据分块 try/catch 是同一个思路）。
     */
    public AlertResult notifyAlerts() {
        int stock = safeRun("库存告急提醒", this::notifyLowStock);
        int health = safeRun("设备健康预警", this::notifyHealthRisk);
        return new AlertResult(stock, health);
    }

    private int safeRun(String name, java.util.function.IntSupplier action) {
        try {
            return action.getAsInt();
        } catch (Exception e) {
            log.error("资产告警[{}]执行失败，已跳过这一块", name, e);
            return 0;
        }
    }

    /**
     * 配件库存告急：**每件配件一条消息**。
     *
     * <p>为什么不聚合：每件配件对应一个独立的补货动作，
     * 而且配件总量不大（几十件级别），聚合反而让"要补哪几样"变得含糊。
     * 这和健康预警的处理刚好相反，理由见类注释。
     *
     * @return 实际发出的条数（收件人数 × 告急件数，减去因幂等跳过的）
     */
    public int notifyLowStock() {
        if (!configService.getBoolean(ConfigKeys.STOCK_NOTIFY_ENABLED,
                ConfigKeys.STOCK_NOTIFY_ENABLED_DEFAULT)) {
            log.info("库存告急提醒已关闭（系统参数 {}），跳过", ConfigKeys.STOCK_NOTIFY_ENABLED);
            return 0;
        }

        List<SparePart> low = sparePartService.listLowStock();
        if (low.isEmpty()) {
            return 0;
        }

        List<SysUser> receivers = findReceivers(STOCK_PERMS, "库存告急");
        if (receivers.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        int sent = 0;

        for (SparePart part : low) {
            String title = "库存告急：" + part.getPartName();
            String content = buildStockContent(part);

            for (SysUser receiver : receivers) {
                // ★ 幂等键必须带上收件人 id —— 见类注释。少了它，
                //   除第一个收件人外都会被唯一约束静默挡掉
                String bizKey = BIZ_KEY_STOCK + part.getId() + ":" + receiver.getId() + ":" + today;
                if (messageService.send(receiver.getId(), SysMessage.TYPE_STOCK_LOW,
                        title, content, SysMessage.LEVEL_IMPORTANT,
                        SysMessage.BIZ_SPARE_PART, part.getId(), bizKey)) {
                    sent++;
                }
            }
        }

        if (sent > 0) {
            log.info("库存告急提醒：{} 件配件 × {} 个收件人，实际发出 {} 条",
                    low.size(), receivers.size(), sent);
        }
        return sent;
    }

    /**
     * 设备健康预警：**每天聚合成一条**。
     *
     * <p>严重程度按"有没有高风险设备"决定：只要有一台掉到高风险档，
     * 整条消息标成重要（前端标红）；都只是"关注"档的话用普通级别 ——
     * 全都标红等于全都不红。
     */
    public int notifyHealthRisk() {
        if (!configService.getBoolean(ConfigKeys.HEALTH_NOTIFY_ENABLED,
                ConfigKeys.HEALTH_NOTIFY_ENABLED_DEFAULT)) {
            log.info("健康预警已关闭（系统参数 {}），跳过", ConfigKeys.HEALTH_NOTIFY_ENABLED);
            return 0;
        }

        DeviceHealthSummaryVO summary = deviceHealthService.summary(MAX_HEALTH_LIST);
        if (summary.getRiskCount() <= 0) {
            return 0;
        }

        List<SysUser> receivers = findReceivers(HEALTH_PERMS, "设备健康预警");
        if (receivers.isEmpty()) {
            return 0;
        }

        boolean hasHighRisk = summary.getRiskDevices().stream()
                .anyMatch(d -> DeviceHealthVO.GRADE_RISK.equals(d.getGrade()));

        String title = summary.getRiskCount() + " 台设备健康分偏低";
        String content = buildHealthContent(summary);
        String level = hasHighRisk ? SysMessage.LEVEL_IMPORTANT : SysMessage.LEVEL_NORMAL;

        LocalDate today = LocalDate.now();
        int sent = 0;

        for (SysUser receiver : receivers) {
            String bizKey = BIZ_KEY_HEALTH + receiver.getId() + ":" + today;
            if (messageService.send(receiver.getId(), SysMessage.TYPE_HEALTH_RISK,
                    title, content, level,
                    SysMessage.BIZ_DASHBOARD, null, bizKey)) {
                sent++;
            }
        }

        if (sent > 0) {
            log.info("设备健康预警：{} 台偏低，发给 {} 个收件人，实际发出 {} 条",
                    summary.getRiskCount(), receivers.size(), sent);
        }
        return sent;
    }

    // ============================================================
    // 收件人
    // ============================================================

    /**
     * 找该收到这类告警的人。
     *
     * <p>⚠️ <b>admin 角色是必须显式带上的</b>：超管绕过权限点检查
     * （见 {@code JwtInterceptor}），所以管理员在 {@code sys_role_menu} 里
     * 可能压根没有这些权限点的记录 —— 只按权限点筛的话，
     * **最该收到告警的管理员反而收不到**。
     */
    private List<SysUser> findReceivers(List<String> perms, String alertName) {
        List<SysUser> receivers =
                userRepository.findAlertRecipients(USER_STATUS_ENABLED, SUPER_ADMIN_ROLE, perms);

        if (receivers.isEmpty()) {
            // 不静默：这是"告警没送达"的唯一线索。用 WARN 不用 ERROR ——
            // 一个还没给任何人配权限的系统本来就是这个状态，不算异常
            log.warn("没有找到能接收{}的启用账号（需要 {} 权限或 {} 角色），本次跳过",
                    alertName, String.join(" / ", perms), SUPER_ADMIN_ROLE);
        }
        return receivers;
    }

    // ============================================================
    // 正文
    // ============================================================

    private String buildStockContent(SparePart part) {
        StringBuilder sb = new StringBuilder();
        sb.append("配件：").append(part.getPartName());
        if (StringUtils.hasText(part.getPartCode())) {
            sb.append("（").append(part.getPartCode()).append("）");
        }
        sb.append("\n");

        String unit = StringUtils.hasText(part.getUnit()) ? part.getUnit() : "件";
        sb.append("当前库存：").append(part.getStockQuantity()).append(" ").append(unit);
        sb.append("｜预警阈值：").append(part.getWarnThreshold()).append(" ").append(unit).append("\n");

        if (StringUtils.hasText(part.getLocation())) {
            sb.append("存放位置：").append(part.getLocation()).append("\n");
        }

        sb.append("\n请及时补货。补货入库后库存回到阈值以上，本提醒会自动停止。");
        return sb.toString();
    }

    private String buildHealthContent(DeviceHealthSummaryVO summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下设备健康分低于 90，建议安排检查：\n");

        for (DeviceHealthVO d : summary.getRiskDevices()) {
            sb.append("- ").append(d.getDeviceName());
            if (StringUtils.hasText(d.getAssetCode())) {
                sb.append("（").append(d.getAssetCode()).append("）");
            }
            sb.append("：").append(d.getScore()).append(" 分");
            if (StringUtils.hasText(d.getGrade())) {
                sb.append("，").append(d.getGrade());
            }
            sb.append("\n");
        }

        long total = summary.getRiskCount();
        long shown = summary.getRiskDevices().size();
        if (total > shown) {
            // 说清"只列了前几台"，否则用户会以为总共就这么多
            sb.append("另有 ").append(total - shown).append(" 台，请到首页看板查看完整清单。\n");
        }

        sb.append("\n分数由机龄、维修次数、维修费用综合算出。");
        sb.append("每台的扣分原因在「设备档案」的基础信息里可以看到。");
        return sb.toString();
    }

    /**
     * 一次告警的执行结果，给手动触发接口回显用。
     */
    public record AlertResult(int stockSent, int healthSent) {

        public int total() {
            return stockSent + healthSent;
        }
    }
}
