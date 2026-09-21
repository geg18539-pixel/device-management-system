package com.yan.backend.service.impl;

import com.yan.backend.dto.AiFaultAnalysisResult;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.ai.provider.AiProviderRegistry;
import com.yan.backend.ai.provider.ChatMessage;
import com.yan.backend.ai.provider.LlmProvider;
import com.yan.backend.ai.provider.StructuredRequest;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.AiFaultAnalysisService;
import com.yan.backend.service.AiSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用本地 Ollama 分析故障描述，生成维修建议。
 *
 * <p>结果通过 Ollama 的 format 参数（JSON Schema）约束成固定结构，
 * 库里按 严重程度 / 可能原因 / 建议步骤 / 预计工时 分开存。
 */
@Service
public class AiFaultAnalysisServiceImpl implements AiFaultAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiFaultAnalysisServiceImpl.class);

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            你是一名设备维修专家。用户会给你一条设备故障报修记录，请给出维修建议。

            要求：
            - severity：故障严重程度，从「高」「中」「低」中选一个。影响生产或存在安全风险的算高。
            - possibleCauses：可能的原因，2 到 4 条，每条一句话。
            - suggestedSteps：建议的排查/维修步骤，3 到 5 步，要具体可操作，按执行顺序排列。
            - estimatedHours：预计维修工时，单位小时，用数字。

            只根据用户给出的故障描述判断。信息不足时给出通用的排查思路，
            不要编造具体的型号、参数或零件编号。

            只输出一个 JSON 对象，不要任何额外说明、解释或 Markdown 代码块标记。
            """;

    private final AiProviderRegistry providerRegistry;
    private final AiSettingsService settings;
    private final ObjectMapper objectMapper;
    private final DeviceRepairRepository deviceRepairRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;

    public AiFaultAnalysisServiceImpl(AiProviderRegistry providerRegistry,
                                      AiSettingsService settings,
                                      ObjectMapper objectMapper,
                                      DeviceRepairRepository deviceRepairRepository,
                                      DeviceRepository deviceRepository,
                                      DeviceCategoryRepository deviceCategoryRepository) {
        this.providerRegistry = providerRegistry;
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.deviceRepairRepository = deviceRepairRepository;
        this.deviceRepository = deviceRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
    }

    /**
     * 分析并写回。
     *
     * <p><b>这个方法刻意没有加 @Transactional</b>，是经过考虑的：
     * 如果整个方法在一个事务里，那么十几秒的模型调用期间会一直**占着数据库连接**。
     * Hikari 默认连接池只有 10 个，几个工单同时分析就能把连接池占满，
     * 导致其它接口全部排队等待 —— 一个"智能分析"功能把整个系统拖慢。
     *
     * <p>不加事务后，每次 repository 调用各自是独立的小事务，
     * AI 调用期间不持有任何数据库连接。
     *
     * <p><b>★ 写回一律用定向 update，绝不 save(实体)。</b>
     * 这里曾经有个很隐蔽的丢失更新 bug：开头查出整个工单实体，调完模型（十几秒后）
     * 再 save 回去，那个 save 会把**整行**用几十秒前的旧值覆盖一遍 ——
     * 用户在分析期间点的「受理」「指派」会在分析结束时被静默抹掉，
     * 表现为"点了受理也返回成功，过一会儿刷新又变回没受理"。
     * 定向 update 让这个异步任务只能写它自己负责的几个 AI 字段。
     */
    @Override
    @Async("aiExecutor")
    public void analyzeAsync(Long repairId) {
        long start = System.currentTimeMillis();

        DeviceRepair repair = deviceRepairRepository.findById(repairId).orElse(null);
        if (repair == null) {
            log.warn("工单 {} 已不存在，跳过 AI 分析", repairId);
            return;
        }

        // 先落一个"分析中"，前端能看到进度，而不是一直显示"待分析"让人以为没触发
        deviceRepairRepository.updateAiRunning(repairId, DeviceRepair.AI_RUNNING, settings.chatModel());

        try {
            String userPrompt = buildUserPrompt(repair);
            AiFaultAnalysisResult result = callModel(userPrompt);

            deviceRepairRepository.updateAiResult(
                    repairId,
                    DeviceRepair.AI_DONE,
                    result.severity(),
                    join(result.possibleCauses()),
                    join(result.suggestedSteps()),
                    result.estimatedHours(),
                    settings.chatModel(),
                    LocalDateTime.now());

            log.info("AI 分析完成：工单#{}，严重程度={}，耗时={}ms",
                    repairId, result.severity(), System.currentTimeMillis() - start);

        } catch (Exception e) {
            // 分析失败绝不能影响工单本身 —— 工单已经建好了，维修流程照常走。
            // 这里只把状态和原因记下来，前端显示"分析失败 + 原因 + 重新分析按钮"。
            log.warn("AI 分析失败：工单#{}，原因={}", repairId, e.getMessage());
            try {
                deviceRepairRepository.updateAiFailed(
                        repairId,
                        DeviceRepair.AI_FAILED,
                        truncate(e.getMessage() == null
                                ? e.getClass().getSimpleName() : e.getMessage(), 1000),
                        settings.chatModel());
            } catch (Exception saveError) {
                // 连失败状态都写不进去，只能记日志了
                log.error("写入 AI 分析失败状态时又出错：工单#{}", repairId, saveError);
            }
        }
    }

    // ============================================================
    // 调用模型
    // ============================================================

    private AiFaultAnalysisResult callModel(String userPrompt) {
        LlmProvider provider = providerRegistry.currentLlm();

        StructuredRequest request = new StructuredRequest(
                // 传 null 让提供方决定用哪个模型（管理员在系统设置里配的那个）
                null,
                List.of(
                        ChatMessage.system(ANALYSIS_SYSTEM_PROMPT),
                        ChatMessage.user(userPrompt)),
                buildJsonSchema(),
                settings.analysisTemperature(),
                settings.analysisMaxTokens());

        // ⚠️ 提供方**不保证**返回的一定是干净 JSON：各家的结构化输出支持程度差别很大
        // （原生 Ollama 能强约束、OpenAI 有 json_object、还有些服务直接忽略这个参数）。
        // 所以下面必须容忍"一段带 JSON 的自由文本"
        String content = provider.structured(request);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型返回内容为空");
        }

        JsonNode parsed = extractJson(content);

        return new AiFaultAnalysisResult(
                normalizeSeverity(parsed.path("severity").asString()),
                toStringList(parsed.path("possibleCauses")),
                toStringList(parsed.path("suggestedSteps")),
                toDecimal(parsed.path("estimatedHours")));
    }

    /**
     * 从模型返回的文本里抠出 JSON 对象。
     *
     * <p>三层兜底，因为"结构化输出"这件事**没有跨厂商的统一保证**：
     * <ol>
     *   <li>直接解析 —— 支持约束的提供方（原生 Ollama / OpenAI）会走到这一步；</li>
     *   <li>去掉 {@code ```json} 代码围栏再解析 —— 模型习惯性地包一层，很常见；</li>
     *   <li>取文本里第一个 {@code &#123;} 到最后一个 {@code &#125;} 之间的内容 ——
     *       模型絮絮叨叨说了一堆再给 JSON 时靠这个救回来。</li>
     * </ol>
     * 三层都失败才报错。相比"假定返回的一定是 JSON"，这里多写十几行，
     * 换来的是**换一个提供方不用改代码**。
     */
    private JsonNode extractJson(String content) {
        String text = content.strip();

        JsonNode direct = tryParse(text);
        if (direct != null) {
            return direct;
        }

        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                JsonNode unfenced = tryParse(text.substring(firstNewline + 1, lastFence).strip());
                if (unfenced != null) {
                    return unfenced;
                }
            }
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            JsonNode embedded = tryParse(text.substring(start, end + 1));
            if (embedded != null) {
                return embedded;
            }
        }

        throw new IllegalStateException("模型返回的不是合法 JSON：" + truncate(content, 200));
    }

    private JsonNode tryParse(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            // readTree 对 "" 之类的输入会返回 null 而不是抛异常，这里一并挡掉
            return node != null && node.isObject() ? node : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** JSON Schema：约束模型必须返回这四个字段，且类型正确 */
    private Map<String, Object> buildJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("severity", Map.of(
                "type", "string",
                "enum", List.of(DeviceRepair.SEVERITY_HIGH,
                        DeviceRepair.SEVERITY_MEDIUM,
                        DeviceRepair.SEVERITY_LOW)));
        properties.put("possibleCauses", Map.of(
                "type", "array",
                "items", Map.of("type", "string")));
        properties.put("suggestedSteps", Map.of(
                "type", "array",
                "items", Map.of("type", "string")));
        properties.put("estimatedHours", Map.of("type", "number"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(
                "severity", "possibleCauses", "suggestedSteps", "estimatedHours"));
        return schema;
    }

    /** 把设备和分类信息也带上，模型判断能准一些（比如"传感器"和"交换机"的常见故障不同） */
    private String buildUserPrompt(DeviceRepair repair) {
        StringBuilder sb = new StringBuilder();
        sb.append("设备名称：").append(repair.getDeviceName() == null ? "未知" : repair.getDeviceName()).append("\n");

        Device device = repair.getDeviceId() == null
                ? null : deviceRepository.findById(repair.getDeviceId()).orElse(null);
        if (device != null) {
            if (device.getCategoryId() != null) {
                String categoryName = deviceCategoryRepository.findById(device.getCategoryId())
                        .map(DeviceCategory::getCategoryName).orElse(null);
                if (categoryName != null) {
                    sb.append("设备分类：").append(categoryName).append("\n");
                }
            }
            if (device.getLocation() != null) {
                sb.append("所在位置：").append(device.getLocation()).append("\n");
            }
        }

        sb.append("故障描述：").append(repair.getFaultDesc()).append("\n");
        sb.append("\n请给出维修建议。");
        return sb.toString();
    }

    // ============================================================
    // 解析辅助
    // ============================================================

    private List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asString();
                if (text != null && !text.isBlank()) {
                    list.add(text.trim());
                }
            }
        }
        return list;
    }

    /** 严重程度兜底：模型偶尔会返回"中等""严重"这种同义词，统一归到三个标准值 */
    private String normalizeSeverity(String raw) {
        if (raw == null) {
            return DeviceRepair.SEVERITY_MEDIUM;
        }
        String value = raw.trim();
        if (value.contains("高") || value.contains("严重") || value.contains("紧急")) {
            return DeviceRepair.SEVERITY_HIGH;
        }
        if (value.contains("低") || value.contains("轻微")) {
            return DeviceRepair.SEVERITY_LOW;
        }
        return DeviceRepair.SEVERITY_MEDIUM;
    }

    private BigDecimal toDecimal(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        try {
            return BigDecimal.valueOf(node.asDouble());
        } catch (Exception e) {
            return null;
        }
    }

    /** 多条内容用换行拼成一个字符串入库，前端按 \n 拆开显示 */
    private String join(List<String> items) {
        return items == null || items.isEmpty() ? null : String.join("\n", items);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
