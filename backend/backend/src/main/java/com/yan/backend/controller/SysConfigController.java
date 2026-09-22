package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.annotation.RequireRole;
import com.yan.backend.common.ConfigKeys;
import com.yan.backend.common.Result;
import com.yan.backend.entity.SysConfig;
import com.yan.backend.service.SysConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统参数配置。
 *
 * <p>管理接口在 {@code /api/system/configs} 下，只给管理员。
 * 另外有一个**免登录**的 {@code /api/config/public} —— 登录页要显示
 * 系统名称和企业名称，那时用户还没有 token。
 */
@RestController
public class SysConfigController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    // ============================================================
    // 免登录接口（登录页要用）
    // ============================================================

    /**
     * GET /api/config/public —— 只返回展示类的参数。
     *
     * <p>这个路径在 WebMvcConfig 里被排除在拦截器之外。
     * 返回内容严格限制在 {@link ConfigKeys#PUBLIC_DEFAULTS} 白名单内，
     * **不要**顺手把整个参数表返回出去 —— 那样等于把密码策略、
     * 预警阈值这些内部配置暴露给了未登录的人。
     */
    @GetMapping("/api/config/public")
    public ResponseEntity<Result<Map<String, String>>> publicConfig() {
        Map<String, String> result = new LinkedHashMap<>();

        // ⚠️ **按白名单遍历，不要逐个 put。**
        // 原来这里写死两项，而 ConfigKeys.PUBLIC_KEYS 那个列表根本没人读 ——
        // 往白名单里加一项却忘了在这里补一行，白名单就成了摆设，且不报任何错。
        // （真发生过：加 system.base-url 时接口照旧只返回原来那两项。）
        // 遍历之后，"能返回哪些键"只有白名单一个地方说了算。
        ConfigKeys.PUBLIC_DEFAULTS.forEach((key, fallback) ->
                result.put(key, sysConfigService.getString(key, fallback)));

        return ResponseEntity.ok(Result.success(result));
    }

    // ============================================================
    // 管理接口
    // ============================================================

    /** GET /api/system/configs —— 全部参数（前端按 configGroup 分块展示） */
    @RequireRole("admin")
    @RequirePerm("sys:config:list")
    @GetMapping("/api/system/configs")
    public ResponseEntity<Result<List<SysConfig>>> list() {
        return ResponseEntity.ok(Result.success(sysConfigService.listAll()));
    }

    /**
     * PUT /api/system/configs/{key} —— 改某一项的值。
     *
     * <p>按 key 而不是 id 定位：key 是稳定且可读的，
     * 前端拿着配置项直接拼 URL 就行，不用先记 id。
     */
    @RequireRole("admin")
    @RequirePerm("sys:config:edit")
    @Log(title = "系统参数", businessType = "UPDATE")
    @PutMapping("/api/system/configs/{key}")
    public ResponseEntity<Result<SysConfig>> update(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Result.success("保存成功（即时生效）",
                sysConfigService.updateValue(key, body.get("configValue"))));
    }

    /** POST /api/system/configs —— 新增自定义参数 */
    @RequireRole("admin")
    @RequirePerm("sys:config:edit")
    @Log(title = "系统参数", businessType = "INSERT")
    @PostMapping("/api/system/configs")
    public ResponseEntity<Result<SysConfig>> create(@Valid @RequestBody SysConfig config) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysConfigService.create(config)));
    }

    /** DELETE /api/system/configs/{id} —— 删除自定义参数（内置项会被拒绝） */
    @RequireRole("admin")
    @RequirePerm("sys:config:edit")
    @Log(title = "系统参数", businessType = "DELETE")
    @DeleteMapping("/api/system/configs/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysConfigService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
