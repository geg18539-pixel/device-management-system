package com.yan.backend.service;

import com.yan.backend.dto.LoginRequest;
import com.yan.backend.dto.LoginResponse;

public interface AuthService {

    /**
     * 校验用户名密码并签发 token。
     *
     * @throws com.yan.backend.exception.AuthException 用户名或密码错误、账号停用
     */
    LoginResponse login(LoginRequest request);
}
