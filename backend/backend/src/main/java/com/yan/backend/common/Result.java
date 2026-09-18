package com.yan.backend.common;

/**
 * 统一响应包装类。
 *
 * <p>所有接口的返回值都包一层这个类，前端就能用同一套逻辑处理响应：
 * 先看 code 判断成败，再从 data 里取数据。
 *
 * @param <T> 业务数据的类型
 */
public class Result<T> {

    /** 业务状态码，200 表示成功 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 业务数据，失败时通常为 null */
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ---------- 静态工厂方法 ----------

    /** 成功，携带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功，自定义提示信息 */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 成功，无数据返回（如删除操作） */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 失败，只有状态码和提示信息 */
    public static <T> Result<T> failure(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败，额外携带数据（如字段校验错误明细） */
    public static <T> Result<T> failure(Integer code, String message, T data) {
        return new Result<>(code, message, data);
    }

    // ---------- getter / setter ----------

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
