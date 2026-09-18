package com.yan.backend.exception;

/**
 * 资源不存在时抛出，由 GlobalExceptionHandler 统一转成 404 响应。
 *
 * <p>继承 RuntimeException 而不是 Exception，是为了避免在每个 Service 方法上
 * 都声明 throws —— 这类"查不到"的异常属于业务流程的正常分支，不是调用方需要
 * 强制处理的受检异常。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
