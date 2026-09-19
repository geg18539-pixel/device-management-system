package com.yan.backend.aspect;

import com.yan.backend.annotation.Log;
import com.yan.backend.common.RequestUtils;
import com.yan.backend.common.UserContext;
import com.yan.backend.entity.SysOperLog;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面。
 *
 * <p>切点绑定到 @Log 注解上：只有标注了 @Log 的方法才会被记录，
 * 查询类接口不需要留下审计记录，避免日志表被 GET 请求刷爆。
 *
 * <p>切点表达式写成 &#64;annotation(logAnnotation) 并把注解作为方法参数传入，
 * 这样能直接读到注解上的 title / businessType，不用反射去取。
 */
@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    private final OperLogRecorder operLogRecorder;

    public LogAspect(OperLogRecorder operLogRecorder) {
        this.operLogRecorder = operLogRecorder;
    }

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long start = System.currentTimeMillis();

        Throwable error = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            // 放在 finally 里，成功和失败都要记录
            long cost = System.currentTimeMillis() - start;
            try {
                SysOperLog operLog = buildOperLog(joinPoint, logAnnotation, cost, error);
                operLogRecorder.saveAsync(operLog);
            } catch (Exception e) {
                // 记录日志本身出错不能影响业务，只打控制台
                log.warn("组装操作日志失败: {}", e.getMessage());
            }
        }
    }

    private SysOperLog buildOperLog(ProceedingJoinPoint joinPoint, Log logAnnotation,
                                    long cost, Throwable error) {
        SysOperLog operLog = new SysOperLog();

        operLog.setTitle(logAnnotation.title());
        operLog.setBusinessType(logAnnotation.businessType());
        operLog.setMethod(joinPoint.getSignature().toShortString());
        operLog.setCostTime(cost);

        HttpServletRequest request = RequestUtils.currentRequest();
        if (request != null) {
            operLog.setRequestMethod(request.getMethod());
            String url = request.getRequestURI();
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                url = url + "?" + request.getQueryString();
            }
            operLog.setRequestUrl(truncate(url, 500));
            // 取 IP 的逻辑抽到了 RequestUtils，登录日志那边也用同一份 ——
            // 之前它俩各写一份，迟早会不一致
            operLog.setIp(RequestUtils.getClientIp());
        }

        // 当前操作人来自 JwtInterceptor 塞进 ThreadLocal 的登录信息。
        // 注意：如果某天把 @Log 用在非 HTTP 场景（定时任务），这里是 null，属正常。
        operLog.setOperatorId(UserContext.getUserId());
        operLog.setOperatorName(UserContext.getUsername());

        if (error != null) {
            operLog.setStatus("失败");
            operLog.setErrorMsg(truncate(error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getClass().getSimpleName() + ": " + error.getMessage(), 2000));
        } else {
            operLog.setStatus("成功");
        }

        return operLog;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
