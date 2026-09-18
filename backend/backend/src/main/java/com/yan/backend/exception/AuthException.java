package com.yan.backend.exception;

/**
 * 认证失败：用户名密码不对、账号被停用等。
 *
 * <p>由 GlobalExceptionHandler 统一转成 401。
 * 单独定义一个异常而不是复用 ResourceNotFoundException，
 * 是因为两者语义不同 —— 404 是"资源不存在"，401 是"你没通过认证"，
 * 前端对这两者的处理方式也不一样（401 要跳登录页）。
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
