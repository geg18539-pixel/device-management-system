package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.common.Result;
import com.yan.backend.common.UserContext;
import com.yan.backend.dto.ChangePasswordRequest;
import com.yan.backend.dto.CurrentUserVO;
import com.yan.backend.dto.LoginRequest;
import com.yan.backend.dto.LoginResponse;
import com.yan.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login —— 登录，换取 JWT。
     *
     * <p>这个路径在 WebMvcConfig 里被排除在拦截器之外，否则会陷入
     * "登录需要带 token、拿 token 又需要先登录"的死循环。
     */
    @PostMapping("/login")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(Result.success("登录成功", response));
    }

    /**
     * GET /api/auth/me —— 返回当前登录用户（含权限点）。
     *
     * <p>这个接口没有被排除，所以必须带合法 token 才能访问。
     * 前端用它做三件事：刷新页面后判断 token 是否还有效、
     * 拿角色去过滤菜单、拿 perms 去控制按钮的显示隐藏。
     */
    @GetMapping("/me")
    public ResponseEntity<Result<CurrentUserVO>> currentUser() {
        return ResponseEntity.ok(Result.success(
                authService.currentUser(UserContext.getUserId())));
    }

    /**
     * PUT /api/auth/change-password —— 用户自己修改密码。
     *
     * <p>这个接口**仍然走拦截器**（要带 token，因为要从登录态取 userId）。
     * 但它必须在"强制改密"的放行名单里 —— 否则会出现死循环：
     * 拦截器拦住所有接口要求先改密，而改密接口本身也被拦住，用户永远出不去。
     * 放行名单在 {@code JwtInterceptor.PASSWORD_CHANGE_ALLOWED} 里。
     *
     * <p>安全性由接口内部校验原密码来保证，不依赖"拦截器放行"这件事。
     *
     * <p>返回新的 token：旧 token 里还挂着"需强制改密"的标记，
     * 不换的话用户改完密码立刻又被拦一次。
     */
    @Log(title = "修改密码", businessType = "UPDATE")
    @PutMapping("/change-password")
    public ResponseEntity<Result<LoginResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(Result.success("密码修改成功",
                authService.changePassword(UserContext.getUserId(), request)));
    }
}
