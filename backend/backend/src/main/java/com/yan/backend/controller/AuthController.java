package com.yan.backend.controller;

import com.yan.backend.common.LoginUser;
import com.yan.backend.common.Result;
import com.yan.backend.common.UserContext;
import com.yan.backend.dto.LoginRequest;
import com.yan.backend.dto.LoginResponse;
import com.yan.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
     * GET /api/auth/me —— 返回当前登录用户。
     *
     * <p>这个接口没有被排除，所以必须带合法 token 才能访问。
     * 前端用它做两件事：刷新页面后判断 token 是否还有效、拿角色去过滤菜单。
     */
    @GetMapping("/me")
    public ResponseEntity<Result<LoginUser>> currentUser() {
        return ResponseEntity.ok(Result.success(UserContext.get()));
    }
}
