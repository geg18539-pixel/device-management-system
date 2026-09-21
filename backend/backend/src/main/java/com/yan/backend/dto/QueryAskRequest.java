package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 一次自然语言问数的请求。
 *
 * @param question 用户的问句。长度上限和执行器里的提示词预算对齐 ——
 *                 让它在**参数校验**这一层就被挡下，比进了提示词再说要清楚
 */
public record QueryAskRequest(

        @NotBlank(message = "请先描述你想查什么")
        @Size(max = 200, message = "问题请控制在 200 个字以内")
        String question
) {
}
