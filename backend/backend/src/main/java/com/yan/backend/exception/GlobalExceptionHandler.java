package com.yan.backend.exception;

import com.yan.backend.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.LocalDate;
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
     * 上传的文件超过了 Spring 的 multipart 上限。
     *
     * <p>正常情况下轮不到这里 —— application.yml 里把 multipart 上限设得比
     * 业务层（app.file.max-size-mb）大，所以超出的是业务层先发现、
     * 给出的是"最大 10 MB"这种明确提示。
     *
     * <p>但配置有可能被改错，而且这个异常不捕获的话会落到兜底分支，
     * 用户看到的是"服务器内部错误"，完全看不出是文件太大。
     * 所以还是显式接一下，保证任何情况下提示都是可读的。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超过 multipart 限制: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.failure(413, "上传的文件过大，请压缩后再试"));
    }

    /**
     * 上传请求的格式不对（不是 multipart/form-data）。
     *
     * <p>不接这个异常的话它会落到兜底分支变成 **500「服务器内部错误」**，
     * 而实际上这是客户端发错了请求格式 —— 用户看到"服务器内部错误"
     * 只会以为后端挂了，完全想不到是自己少传了个 Content-Type。
     *
     * <p>注意这个 handler 比 {@link #handleMaxUploadSize} 宽泛
     * （{@code MaxUploadSizeExceededException} 是 {@code MultipartException} 的子类），
     * Spring 会优先选更具体的那个，所以"文件太大"仍然走 413 的提示。
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipart(MultipartException ex) {
        log.warn("上传请求格式不正确: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(Result.failure(400, "请通过表单上传文件（multipart/form-data）"));
    }

    /**
     * 数据库约束被违反。
     *
     * <p><b>什么时候会走到这里</b>：业务层防重都是"先查再插"，
     * 而数据库上还有唯一索引兜底。两者之间有个窗口 ——
     * 两个请求同时通过预检查、都去插入，第二个必然撞唯一约束。
     * 另外字段过长、必填项为空、外键不存在这类也都是这个异常。
     *
     * <p><b>为什么不能让它落到兜底分支</b>：那样返回的是 500
     * 「服务器内部错误」，用户以为服务挂了、去重启；
     * 而且兜底分支会把异常消息原样回显 ——
     * MySQL 的那句话长这样：
     * <pre>
     *   Duplicate entry 'UK-001' for key 'device.UK9ia7b4hpjsdn7b4myie8ejgxu'
     * </pre>
     * 约束名、表名、甚至**重复的那个值**全都告诉调用方了，
     * 等于把库结构送出去。
     *
     * <p>所以这里做两件事：**返回 400**（是数据问题不是服务问题），
     * 并且**只回一句人话，数据库原文只写日志**。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        // 日志里留全，包括数据库原文 —— 排查时要靠它定位是哪个约束
        log.warn("数据违反数据库约束: {}",
                ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(400,
                        "保存失败：数据不符合数据库约束。常见原因是编号/名称重复、"
                                + "字段内容过长、或必填项为空。请检查后重试；"
                                + "具体是哪一个约束，请看后端日志"));
    }

    /**
     * 路径参数 / 查询参数的类型对不上。
     *
     * <p>比如 {@code GET /api/devices/abc} —— 接口上声明的是 {@code @PathVariable Long id}，
     * 而 {@code abc} 解析不成数字。
     *
     * <p><b>这一类必须单独处理</b>：它不实现 {@code ErrorResponse}，
     * 所以会一路落到兜底分支变成 500「服务器内部错误」——
     * 可实际上请求方写的地址有问题，服务器一点毛病都没有。
     * 前端收到 500 会去查后端日志，方向从一开始就是错的。
     *
     * <p>（实测过：同样的类型不匹配，**绑到对象上的查询参数**（如 {@code ?pageNum=abc}
     * 配 {@code DeviceQuery}）会走 Bean Validation 那条路，本来就是 400；
     * 只有散装的路径参数会漏到这里。）
     *
     * <p>消息里带上参数名和原始取值：**那是请求方自己发过来的东西**，
     * 回显出来正好是他排查所需的，不涉及任何服务端信息。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "参数「" + ex.getName() + "」的取值「" + ex.getValue()
                + "」不合法，应为" + describeType(ex.getRequiredType());

        log.warn("请求参数类型不匹配: {}", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(400, message));
    }

    /**
     * 请求体读不出来：不是合法 JSON，或者干脆是空的。
     *
     * <p>⚠️ <b>刻意不回显 {@code ex.getMessage()}</b>：Jackson 的解析异常消息里
     * 会带上出错的**原始片段**，严重时是整段请求体。请求体里可能有备注、
     * 密码改写这类字段，原样回显等于把它写进了响应和日志。
     * 这里只给一句固定的、可操作的提示，真正的原因记在服务端日志里。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("请求体无法解析: {}", ex.getMostSpecificCause().getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(400,
                        "请求体格式不正确：请确认发的是合法 JSON，且请求头带了 "
                                + "Content-Type: application/json"));
    }

    /** 把 Java 类型翻成人话 —— 别让调用方看到 {@code java.lang.Long} 这种东西 */
    private static String describeType(Class<?> type) {
        if (type == null) {
            return "合法值";
        }
        if (Long.class.equals(type) || Integer.class.equals(type)
                || long.class.equals(type) || int.class.equals(type)) {
            return "数字";
        }
        if (LocalDate.class.equals(type)) {
            return "日期（格式 yyyy-MM-dd）";
        }
        if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            return "布尔值";
        }
        return type.getSimpleName();
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

        // ⚠️ 这里**不回显 ex.getMessage()**。
        // 走到兜底的都是"没预料到的异常"，它的消息不是写给人看的 ——
        // 里面有 SQL 片段、表名列名、文件路径、甚至连接串。
        // 只给一个异常类名（类名不泄露数据），剩下的去看日志。
        // 真正写给人看的那句话（业务规则、AI 提供方的报错等）都是在
        // IllegalArgumentException / IllegalStateException 里，
        // 走上面那个 400 分支，那里的消息是可以回显的。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(500, "服务器内部错误（" + ex.getClass().getSimpleName()
                        + "），详细原因见后端日志"));
    }
}
