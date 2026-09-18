package com.yan.backend.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明访问该接口所需的角色。
 *
 * <p>语义是「**拥有其中任意一个角色即可**」，不是"必须全部拥有"。
 * 比如 &#64;RequireRole({"admin", "auditor"}) 表示 admin 或 auditor 都能访问。
 *
 * <p>可以标在方法上（只作用于该方法）或类上（作用于该 Controller 的所有接口）。
 * 方法上的注解优先于类上的 —— 这样可以在一个受限的 Controller 里单独放开某个接口。
 *
 * <p>由 JwtInterceptor 在 preHandle 里校验，不通过返回 403（不是 401）：
 * 401 表示"你没登录"，403 表示"你登录了但没权限"，两者语义不同，
 * 前端处理方式也不一样（401 要跳登录页，403 只提示即可）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RequireRole {

    /** 允许访问的角色标识（roleKey），满足任意一个即可 */
    String[] value();
}
