package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.Result;
import com.yan.backend.dto.AssignRolesRequest;
import com.yan.backend.dto.BatchResultVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SysUserQuery;
import com.yan.backend.dto.SysUserSaveRequest;
import com.yan.backend.dto.SysUserVO;
import com.yan.backend.excel.SysUserExcelExporter;
import com.yan.backend.service.SysUserService;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户管理。
 *
 * <p><b>权限控制方式</b>：类上标 {@code @RequirePerm("sys:user:list")} 作为默认，
 * 覆盖所有读接口；写接口各自标更具体的权限标识。方法级注解优先于类级，
 * 所以不用给每个读方法重复标注。
 *
 * <p>权限标识来自菜单表里 menuType='B' 的按钮记录，由管理员在
 * 「角色管理 → 分配权限」里勾选分配，**不用改代码就能调整谁能做什么**。
 *
 * <p>注意：拥有 admin 角色的用户会绕过所有权限检查（见 JwtInterceptor），
 * 所以验证细粒度权限要用 operator 这类非超管账号。
 */
@RequirePerm("sys:user:list")
@RestController
@RequestMapping("/api/system/users")
public class SysUserController {

    private final SysUserService sysUserService;
    private final SysUserExcelExporter excelExporter;

    public SysUserController(SysUserService sysUserService,
                             SysUserExcelExporter excelExporter) {
        this.sysUserService = sysUserService;
        this.excelExporter = excelExporter;
    }

    /**
     * GET /api/system/users —— 分页 + 多条件筛选。
     *
     * <p>筛选条件放在 {@link SysUserQuery} 里由 Spring 自动从 query string 绑定，
     * 不用写成 8 个 @RequestParam。日期参数（createTimeBegin/createTimeEnd）
     * 传 {@code YYYY-MM-DD} 格式，Spring Boot 默认就支持 ISO 日期绑定。
     */
    @GetMapping
    public ResponseEntity<Result<PageResult<SysUserVO>>> page(
            SysUserQuery query,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        return ResponseEntity.ok(Result.success(sysUserService.page(query, pageNum, pageSize)));
    }

    /**
     * GET /api/system/users/export —— 导出 Excel。
     *
     * <p>筛选条件与列表完全一致（复用同一个 SysUserQuery），
     * 也就是"所见即所得"：页面上筛出什么，导出就是什么。
     *
     * <p>直接写 HttpServletResponse 而不是返回 byte[]：数据量大时
     * 返回 byte[] 会先在内存里拼一整个数组再交给 Spring，白多一次拷贝。
     */
    @RequirePerm("sys:user:export")
    @Log(title = "用户管理", businessType = "EXPORT")
    @GetMapping("/export")
    public void export(SysUserQuery query, HttpServletResponse response) throws IOException {
        List<SysUserVO> users = sysUserService.listForExport(query);

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // ★ 中文文件名必须 URL 编码，而且要用 filename* 这种写法。
        // 直接塞进 filename="用户列表.xlsx" 的话，HTTP 头不允许非 ASCII 字符，
        // Tomcat 会把它变成乱码，下载下来是"???.xlsx"。
        // URLEncoder 会把空格编成 "+"，而 HTTP 头里空格应当是 %20，所以要替换回来。
        String fileName = URLEncoder.encode(
                "用户列表_" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        excelExporter.write(users, response.getOutputStream());
    }

    /** GET /api/system/users/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<SysUserVO>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysUserService.findById(id)));
    }

    /** GET /api/system/users/{id}/roles —— 供"分配角色"弹窗回显勾选状态 */
    @GetMapping("/{id}/roles")
    public ResponseEntity<Result<List<Long>>> roleIds(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysUserService.findRoleIds(id)));
    }

    // ---------------- 批量操作 ----------------

    /**
     * POST /api/system/users/batch/delete —— 批量删除。
     *
     * <p>返回结果里会写明"成功几个 + 哪几个被跳过、为什么"。
     * 受保护的用户（内置 admin、当前登录账号）会被跳过而不是让整批失败。
     */
    @RequirePerm("sys:user:remove")
    @Log(title = "用户管理", businessType = "DELETE")
    @PostMapping("/batch/delete")
    public ResponseEntity<Result<BatchResultVO>> batchDelete(@RequestBody Map<String, Object> body) {
        BatchResultVO result = sysUserService.batchDelete(toLongList(body.get("ids")));
        return ResponseEntity.ok(Result.success(
                describeBatch("删除", result), result));
    }

    /** POST /api/system/users/batch/status —— 批量启用 / 停用 */
    @RequirePerm("sys:user:edit")
    @Log(title = "用户管理", businessType = "UPDATE")
    @PostMapping("/batch/status")
    public ResponseEntity<Result<BatchResultVO>> batchUpdateStatus(
            @RequestBody Map<String, Object> body) {

        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        BatchResultVO result = sysUserService.batchUpdateStatus(toLongList(body.get("ids")), status);

        return ResponseEntity.ok(Result.success(
                describeBatch("状态修改", result), result));
    }

    /** POST /api/system/users/batch/password —— 批量重置密码 */
    @RequirePerm("sys:user:reset")
    @Log(title = "用户管理", businessType = "UPDATE")
    @PostMapping("/batch/password")
    public ResponseEntity<Result<BatchResultVO>> batchResetPassword(
            @RequestBody Map<String, Object> body) {

        String password = body.get("password") == null ? null : String.valueOf(body.get("password"));
        BatchResultVO result = sysUserService.batchResetPassword(toLongList(body.get("ids")), password);

        return ResponseEntity.ok(Result.success(
                describeBatch("密码重置", result), result));
    }

    // ---------------- 单条操作 ----------------

    /** POST /api/system/users */
    @RequirePerm("sys:user:add")
    @Log(title = "用户管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<SysUserVO>> create(@Valid @RequestBody SysUserSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysUserService.create(request)));
    }

    /** PUT /api/system/users/{id} */
    @RequirePerm("sys:user:edit")
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SysUserVO>> update(@PathVariable Long id,
                                                    @Valid @RequestBody SysUserSaveRequest request) {
        return ResponseEntity.ok(Result.success("修改成功", sysUserService.update(id, request)));
    }

    /** PUT /api/system/users/{id}/roles —— 分配角色 */
    @RequirePerm("sys:user:assign")
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}/roles")
    public ResponseEntity<Result<Void>> assignRoles(@PathVariable Long id,
                                                    @RequestBody AssignRolesRequest request) {
        sysUserService.assignRoles(id, request.getRoleIds());
        return ResponseEntity.ok(Result.success("角色分配成功", null));
    }

    /** PUT /api/system/users/{id}/password —— 重置密码 */
    @RequirePerm("sys:user:reset")
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}/password")
    public ResponseEntity<Result<Void>> resetPassword(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        sysUserService.resetPassword(id, body.get("password"));
        return ResponseEntity.ok(Result.success("密码重置成功", null));
    }

    /** DELETE /api/system/users/{id} */
    @RequirePerm("sys:user:remove")
    @Log(title = "用户管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }

    // ---------------- 私有辅助 ----------------

    /** 批量结果的提示语：全部成功 / 部分成功都要说清楚，不能让用户以为全成了 */
    private String describeBatch(String action, BatchResultVO result) {
        if (result.getSkipped().isEmpty()) {
            return action + "成功 " + result.getSuccessCount() + " 条";
        }
        return action + "完成：成功 " + result.getSuccessCount()
                + " 条，跳过 " + result.getSkipped().size() + " 条";
    }

    /**
     * 把请求体里的 ids 转成 Long 列表。
     *
     * <p><b>不能写成 {@code (List<Long>) body.get("ids")}</b>：
     * Jackson 把 JSON 里的小整数反序列化成的是 **Integer** 而不是 Long，
     * 那个强制转换在编译期能过（泛型被擦除了），但遍历取元素时会抛
     * ClassCastException —— 典型的"编译通过、运行时炸"。
     * 所以按 Number 统一取值，字符串形式的数字也一并兜住。
     */
    private List<Long> toLongList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(item -> item instanceof Number number
                        ? number.longValue()
                        : Long.valueOf(String.valueOf(item).trim()))
                .toList();
    }
}
