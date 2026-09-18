package com.yan.backend.exception;

import com.yan.backend.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器。
 *
 * <p>用 @RestControllerAdvice 把散落在各个 Controller 里的 try-catch 收拢到一处，
 * 保证任何异常最终都以 Result 的格式返回，前端不需要为异常情况写第二套解析逻辑。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 参数校验失败。
     *
     * <p>当 @Valid @RequestBody 校验不通过时，Spring 抛出这个异常。
     * 这里把每个字段的错误信息收集起来放进 data，前端能直接标红对应输入框。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // putIfAbsent: 同一个字段有多个校验注解失败时，只保留第一条信息
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("参数校验失败: {}", errors);

        return ResponseEntity.badRequest()
                .body(Result.failure(400, "参数校验失败", errors));
    }

    /**
     * 业务规则不满足。
     *
     * <p>比如：用户名已存在、不允许删除内置管理员、菜单下还有子菜单不能删、
     * 角色下还有用户不能删等等。这些是"这次请求本身不合法"，属于客户端问题，
     * 应该返回 400。
     *
     * <p>如果不单独处理，它们会落到下面的兜底分支变成 500 —— 前端会以为
     * 服务器崩了，也没法把这些提示当成正常的表单校验错误来展示。
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Result<Void>> handleBusinessException(RuntimeException ex) {
        log.warn("业务校验未通过: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(Result.failure(400, ex.getMessage()));
    }

    /** 认证失败（用户名密码错误、账号停用）。 */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Result<Void>> handleAuthException(AuthException ex) {
        log.warn("认证失败: {}", ex.getMessage());

        // 用 401 而不是 400，前端拦截器会据此跳转登录页
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.failure(401, ex.getMessage()));
    }

    /** 查询的资源不存在。 */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Result<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("资源不存在: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.failure(404, ex.getMessage()));
    }

    /**
     * 兜底处理。
     *
     * <p>注意这里要先把 Spring 自己抛的 HTTP 异常放行，否则会把 404、405
     * 这类本来语义正确的情况一律压成 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {

        // ErrorResponse 是 Spring 6 引入的接口，HttpRequestMethodNotSupportedException、
        // ResponseStatusException 等都实现了它，自带语义化的状态码
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("请求处理失败 [{}]: {}", status, ex.getMessage());

            return ResponseEntity.status(status)
                    .body(Result.failure(status.value(), ex.getMessage()));
        }

        log.error("未处理的异常", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(500, "服务器内部错误: " + ex.getMessage()));
    }
}
