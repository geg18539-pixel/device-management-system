package com.yan.backend.common;

/**
 * 系统参数的键名。
 *
 * <p>收成常量而不是散在各处写字符串，原因很实际：**同一个键会在三个地方出现** ——
 * 代码里的读取点、种子数据里的默认行、页面上展示的名称。
 * 任何一处拼错都**不会报错**，只会表现为"这个参数改了没反应"
 * （因为代码读的键和库里存的键对不上，读不到就走默认值了）。
 *
 * <p>命名刻意和 application.yml 里的路径对齐，方便对照。
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    // ---------- 系统信息（展示类，登录页也要用） ----------

    /** 系统名称。显示在登录页、侧边栏标题、浏览器标签页 */
    public static final String SYSTEM_NAME = "system.name";
    public static final String SYSTEM_NAME_DEFAULT = "设备管理系统";

    /** 企业名称。显示在登录页底部 */
    public static final String COMPANY_NAME = "system.company";
    public static final String COMPANY_NAME_DEFAULT = "示例企业有限公司";

    /**
     * 系统**对外**的访问地址，例如 {@code http://192.168.1.20:8080} 或
     * {@code https://dms.example.com}。
     *
     * <p><b>它只被一个地方用</b>：设备资产标签上那个二维码。二维码是印出来给
     * **别的设备**（手机）扫的，所以里面必须是别的设备能访问到的地址。
     *
     * <p>⚠️ <b>空值表示"用当前页面地址"</b>。在开发机上（浏览器里是
     * {@code localhost:5173}）那样扫出来是打不开的 —— 所以这个值在
     * 真正要打印标签的场合**必须配**。界面上检测到是本机地址时会明确提示。
     *
     * <p>放进 {@link #PUBLIC_KEYS} 是因为它**按定义就是公开的**：
     * 它要印在二维码上给任何人扫。
     */
    public static final String SYSTEM_BASE_URL = "system.base-url";
    public static final String SYSTEM_BASE_URL_DEFAULT = "";

    // ---------- 安全策略 ----------

    /** 密码有效期（天）。<=0 表示关闭过期策略。对应 app.security.password-valid-days */
    public static final String PASSWORD_VALID_DAYS = "security.password.valid-days";
    public static final int PASSWORD_VALID_DAYS_DEFAULT = 90;

    // ---------- 提醒阈值 ----------

    /** 维保到期提前预警天数。对应 app.maintenance.warn-days */
    public static final String MAINTENANCE_WARN_DAYS = "maintenance.warn-days";
    public static final int MAINTENANCE_WARN_DAYS_DEFAULT = 30;

    /** 保修到期提前预警天数。原来硬编码在看板里，现在也放出来可配 */
    public static final String WARRANTY_WARN_DAYS = "warranty.warn-days";
    public static final int WARRANTY_WARN_DAYS_DEFAULT = 90;

    // ---------- 站内消息 ----------

    /**
     * 维保到期是否自动发站内消息。
     *
     * <p>做成开关是因为它的行为比较"吵"：每天会给每个到期计划的负责人各发一条。
     * 有些团队觉得这种提醒有用，有些觉得是打扰（他们更愿意看维保页面顶部的告警条）。
     */
    public static final String MAINTENANCE_NOTIFY_ENABLED = "maintenance.notify.enabled";
    public static final boolean MAINTENANCE_NOTIFY_ENABLED_DEFAULT = true;

    /**
     * 配件库存告急是否自动发站内消息。
     *
     * <p>和维保提醒那个开关是同一个理由：这类提醒"比较吵" ——
     * 一件配件只要还在阈值以下，每天都发一条，直到补货为止。
     * 有的团队靠它提醒补货，有的团队更愿意自己盯「配件耗材」页的告急清单。
     */
    public static final String STOCK_NOTIFY_ENABLED = "stock.notify.enabled";
    public static final boolean STOCK_NOTIFY_ENABLED_DEFAULT = true;

    /**
     * 设备健康预警是否自动发站内消息。
     *
     * <p>⚠️ 这条提醒**只发给"能处理的人"**（拥有 dev:device:edit 的用户 + 管理员），
     * 而不是广播给所有人 —— 设备健康是运维职责范围内的事，
     * 发给不相关的人只会让消息中心变成一个没人看的红点。
     *
     * <p>和库存告警不同，健康预警**每天聚合成一条**：一台设备的健康分
     * 会在低位停留好几个月，逐台发的话消息中心很快就被同一批设备刷屏了。
     */
    public static final String HEALTH_NOTIFY_ENABLED = "health.notify.enabled";
    public static final boolean HEALTH_NOTIFY_ENABLED_DEFAULT = true;

    // ---------- 首页 AI 摘要 ----------

    /**
     * 首页是否显示 AI 摘要卡片。
     *
     * <p>做成开关而不是写死，有两个实际理由：
     * <ul>
     *   <li>摘要是**每天定时调一次模型**生成的。本机小模型跑一次要几十秒，
     *       有人会觉得这份开销不值得（尤其是不想为了首页一句话一直开着 Ollama）；</li>
     *   <li>它和维保提醒一样属于"比较吵"的功能 —— 关掉之后首页其余部分完全不受影响。</li>
     * </ul>
     *
     * <p>关闭后的表现是摘要卡片整块不出现，而不是显示一个空框。
     */
    public static final String DASHBOARD_DIGEST_ENABLED = "dashboard.digest.enabled";
    public static final boolean DASHBOARD_DIGEST_ENABLED_DEFAULT = true;

    // ---------- 附件 ----------

    /**
     * 单个附件的大小上限（MB）。对应 app.file.max-size-mb
     *
     * <p>注意**扩展名白名单没有做成可配的**：那是安全边界
     * （放进 svg / html 就等于开了存储型 XSS 的口子），
     * 不该让界面上的一个输入框能改掉。要调整得改 yml 并重新部署。
     */
    public static final String UPLOAD_MAX_SIZE_MB = "file.upload.max-size-mb";
    public static final int UPLOAD_MAX_SIZE_MB_DEFAULT = 10;

    // ---------- AI 模型 ----------
    //
    // ⚠️ 这里**只放非敏感项**。提供方的 API Key 一律走环境变量 / yml，
    // 不进数据库、不出现在任何接口响应里 —— 理由见 AiSettingsService 的类注释。
    //
    // 这几项的值都是"覆盖值"：库里没有（或那一行被删掉）时，
    // 代码会退回 yml 里 app.ai.chat.* / app.ai.embedding.* 的值。

    /**
     * 对话提供方：ollama（本机）/ openai（任意 OpenAI 兼容服务）。
     *
     * <p>⚠️ 种子默认值是**空串**，表示"不覆盖，跟随配置文件/环境变量"。
     * 这几项都遵循同一条规则：**库里为空就用 yml 的值，填了才覆盖**。
     *
     * <p>为什么不种成具体值：种成 {@code ollama} 的话，Docker 里注入的
     * {@code AI_CHAT_PROVIDER=openai} 会被库里的值盖掉 ——
     * 而管理员在界面上完全看不出"为什么改了环境变量不生效"。
     * 界面上把实际生效的值放在占位提示里显示，不会让人以为是没配。
     */
    public static final String AI_CHAT_PROVIDER = "ai.chat.provider";
    public static final String AI_CHAT_PROVIDER_DEFAULT = "";

    /**
     * 对话服务地址。对应 app.ai.chat.base-url
     *
     * <p>默认值刻意留空：留空表示"用 yml / 环境变量里的那个"。
     * 这里如果写死成 {@code http://localhost:11434}，Docker 里注入的
     * {@code AI_CHAT_BASE_URL=host.docker.internal:11434} 就会被库里的值盖掉，
     * 而管理员在界面上完全看不出为什么改环境变量不生效。
     */
    public static final String AI_CHAT_BASE_URL = "ai.chat.base-url";
    public static final String AI_CHAT_BASE_URL_DEFAULT = "";

    /** 对话模型名。对应 app.ai.chat.model。留空表示跟随配置文件 */
    public static final String AI_CHAT_MODEL = "ai.chat.model";
    public static final String AI_CHAT_MODEL_DEFAULT = "";

    /** 对话随机度。对应 app.ai.chat.temperature */
    public static final String AI_CHAT_TEMPERATURE = "ai.chat.temperature";
    public static final String AI_CHAT_TEMPERATURE_DEFAULT = "0.7";

    /**
     * 要不要让模型**先思考再回答**（Ollama 的 {@code think} 参数）。
     *
     * <p>⚠️ <b>只对本机 Ollama 生效</b>：OpenAI 兼容提供方不读这个字段，
     * 切过去之后这个开关会失效，而界面上看不出来（原因见 OpenAI 提供方的类注释）。
     *
     * <p><b>默认关</b>，理由是实测出来的取舍：思考能把回答质量往上抬一点，
     * 但会把时间拉长好几倍。而这个系统里的 AI 调用大多是
     * "把已有资料组织成人话"（摘要、故障分析），不是需要反复推敲的开放问题 ——
     * 何况 4B 这种规模的模型，思考带来的增益本身就有限。
     * 觉得回答不够好的话，打开它试试就行。
     *
     * <p>⚠️ 种子值刻意是**空串**，和 provider / base-url / model 那几项一样：
     * 空 = "不覆盖，跟随配置文件 / 环境变量"。种成 {@code "false"} 的话，
     * Docker 里注入的 {@code AI_CHAT_THINKING=true} 会被库里的值悄悄盖掉，
     * 而管理员在界面上完全看不出为什么改环境变量不生效 —— 这个坑 N 批踩过一次。
     */
    public static final String AI_CHAT_THINKING = "ai.chat.thinking";
    public static final String AI_CHAT_THINKING_DEFAULT = "";

    /** 嵌入提供方。对应 app.ai.embedding.provider。留空表示跟随配置文件 */
    public static final String AI_EMBEDDING_PROVIDER = "ai.embedding.provider";
    public static final String AI_EMBEDDING_PROVIDER_DEFAULT = "";

    /** 嵌入服务地址。对应 app.ai.embedding.base-url。留空表示用 yml 的 */
    public static final String AI_EMBEDDING_BASE_URL = "ai.embedding.base-url";
    public static final String AI_EMBEDDING_BASE_URL_DEFAULT = "";

    /** 嵌入模型名。对应 app.ai.embedding.model。留空表示跟随配置文件 */
    public static final String AI_EMBEDDING_MODEL = "ai.embedding.model";
    public static final String AI_EMBEDDING_MODEL_DEFAULT = "";

    /**
     * 允许匿名读取的参数：**键 → 读不到时的兜底值**。
     *
     * <p>登录页要用系统名称和企业名称，而那时用户还没登录、拿不到 token。
     * 所以这几项单独开一个免登录接口。**只放展示类的键**，
     * 安全策略、阈值、AI 配置这类一律不放 —— 它们会暴露系统的内部配置。
     *
     * <p>{@link #SYSTEM_BASE_URL} 也算展示类，而且**按定义就是公开的**：
     * 它要印在资产标签的二维码上给任何人扫。
     *
     * <h3>⚠️ 为什么是 Map 而不是两个平行的常量</h3>
     *
     * <p>原来是「一个 {@code PUBLIC_KEYS} 列表 + 接口里逐个 {@code put}」。
     * 那种写法看着有白名单，实际上**接口根本没用它** ——
     * 往列表里加一项、忘了在接口里补一行，白名单就成了摆设，
     * 而且**什么都不报**。（真发生过：加 {@code system.base-url} 时就是这样，
     * 接口照旧只返回原来那两项；更麻烦的是 {@code WebMvcConfig} 里
     * "这个接口可以免登录"那段安全论证**引用的正是这个列表**。）
     *
     * <p>收成一个 Map 之后，"有哪些键"和"兜底值是什么"在同一处，
     * 接口直接遍历它 —— 结构上不可能再脱节。
     * 顺序用 {@link java.util.LinkedHashMap} 包一层是为了返回值稳定（便于比对）。
     */
    public static final java.util.Map<String, String> PUBLIC_DEFAULTS = buildPublicDefaults();

    /** 允许匿名读取的键名。由 {@link #PUBLIC_DEFAULTS} 派生，不再单独维护 */
    public static final java.util.List<String> PUBLIC_KEYS =
            java.util.List.copyOf(PUBLIC_DEFAULTS.keySet());

    private static java.util.Map<String, String> buildPublicDefaults() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put(SYSTEM_NAME, SYSTEM_NAME_DEFAULT);
        map.put(COMPANY_NAME, COMPANY_NAME_DEFAULT);
        map.put(SYSTEM_BASE_URL, SYSTEM_BASE_URL_DEFAULT);
        return java.util.Collections.unmodifiableMap(map);
    }
}
