package com.yan.backend.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要记录操作日志的方法。
 *
 * <p>用法：加在 Controller 的写操作（新增/修改/删除）上，
 * 由 LogAspect 拦截并异步写入 sys_oper_log 表。
 *
 * <pre>
 *   &#64;Log(title = "用户管理", businessType = "INSERT")
 *   &#64;PostMapping
 *   public ResponseEntity&lt;...&gt; create(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** 操作模块名，如"用户管理"、"角色管理" */
    String title();

    /** 业务类型：INSERT / UPDATE / DELETE / OTHER */
    String businessType() default "OTHER";
}
