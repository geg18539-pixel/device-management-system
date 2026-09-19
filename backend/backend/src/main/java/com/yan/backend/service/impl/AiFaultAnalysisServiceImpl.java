package com.yan.backend.service.impl;

import com.yan.backend.dto.AiFaultAnalysisResult;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.AiFaultAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
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
            """;

    private final RestClient ollamaRestClient;
    private final ObjectMapper objectMapper;
    private final DeviceRepairRepository deviceRepairRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final String model;
    private final int maxTokens;

    public AiFaultAnalysisServiceImpl(RestClient ollamaRestClient,
                                      ObjectMapper objectMapper,
                                      DeviceRepairRepository deviceRepairRepository,
                                      DeviceRepository deviceRepository,
                                      DeviceCategoryRepository deviceCategoryRepository,
                                      @Value("${app.ai.model}") String model,
                                      @Value("${app.ai.analysis-max-tokens:800}") int maxTokens) {
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
        this.deviceRepairRepository = deviceRepairRepository;
        this.deviceRepository = deviceRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.model = model;
        this.maxTokens = maxTokens;
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
        repair.setAiStatus(DeviceRepair.AI_RUNNING);
        repair.setAiError(null);
        deviceRepairRepository.save(repair);

        try {
            String userPrompt = buildUserPrompt(repair);
            AiFaultAnalysisResult result = callModel(userPrompt);

            repair.setAiSeverity(result.severity());
            repair.setAiPossibleCauses(join(result.possibleCauses()));
            repair.setAiSuggestion(join(result.suggestedSteps()));
            repair.setAiEstimatedHours(result.estimatedHours());
            repair.setAiModel(model);
            repair.setAiAnalyzedAt(LocalDateTime.now());
            repair.setAiStatus(DeviceRepair.AI_DONE);
            repair.setAiError(null);
            deviceRepairRepository.save(repair);

            log.info("AI 分析完成：工单#{}，严重程度={}，耗时={}ms",
                    repairId, result.severity(), System.currentTimeMillis() - start);

        } catch (Exception e) {
            // 分析失败绝不能影响工单本身 —— 工单已经建好了，维修流程照常走。
            // 这里只把状态和原因记下来，前端显示"分析失败 + 原因 + 重新分析按钮"。
            log.warn("AI 分析失败：工单#{}，原因={}", repairId, e.getMessage());
            try {
                repair.setAiStatus(DeviceRepair.AI_FAILED);
                repair.setAiError(truncate(e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage(), 1000));
                repair.setAiModel(model);
                deviceRepairRepository.save(repair);
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", ANALYSIS_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)));
        // 这里要的是完整 JSON，不是流式分片
        payload.put("stream", false);
        // format 传 JSON Schema，让 Ollama 约束输出结构。
        // 这样即使小模型判断水平有限，至少返回的 JSON 是合法且字段齐全的。
        payload.put("format", buildJsonSchema());
        payload.put("options", Map.of(
                // 分析类任务要的是稳定结论，不是创意，温度调低
                "temperature", 0.3,
                "num_predict", maxTokens));

        JsonNode response = ollamaRestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        String content = response == null
                ? null : response.path("message").path("content").asString();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型返回内容为空");
        }

        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(content);
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是合法 JSON：" + truncate(content, 200));
        }

        return new AiFaultAnalysisResult(
                normalizeSeverity(parsed.path("severity").asString()),
                toStringList(parsed.path("possibleCauses")),
                toStringList(parsed.path("suggestedSteps")),
                toDecimal(parsed.path("estimatedHours")));
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
