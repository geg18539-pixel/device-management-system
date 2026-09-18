package com.yan.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试接口，用于验证后端是否正常启动。
 */
@RestController
public class TestController {

    /**
     * GET /api/hello
     * 返回 JSON: {"code": 200, "message": "Hello, Device Management System!", "data": "后端启动成功"}
     */
    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        // 用 LinkedHashMap 而不是 Map.of()，因为 Map.of() 不保证遍历顺序，
        // 会让返回的 JSON 里 key 的先后顺序变得不确定。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "Hello, Device Management System!");
        result.put("data", "后端启动成功");
        return result;
    }
}
