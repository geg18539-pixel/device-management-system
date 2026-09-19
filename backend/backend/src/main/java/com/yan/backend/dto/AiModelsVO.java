package com.yan.backend.dto;

import java.util.List;

/**
 * 模型列表接口的返回。
 *
 * <p>把可用模型和当前默认模型一起返回，前端进页面时就能：
 * 1）验证 Ollama 是否可达（拿不到就说明没启动）；
 * 2）把默认模型回显到下拉框上。
 */
public record AiModelsVO(List<String> models, String defaultModel) {
}
