package com.yan.backend.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明访问该接口所需的**权限标识**（细粒度）。
 *
 * <p>和 {@link RequireRole} 的区别：
 * <ul>
 *   <li>{@code @RequireRole("admin")} 是**角色级**：你是不是管理员。粒度粗，
 *       而且角色一旦绑定就写死在代码里，加一个新角色就得改代码。</li>
 *   <li>{@code @RequirePerm("sys:user:add")} 是**权限级**：你有没有"新增用户"这个权限点。
 *       权限点存在菜单表里（menuType = 'B' 的按钮记录），由管理员在"角色管理 →
 *       分配权限"里勾选分配，**不用改代码就能调整谁能做什么**。</li>
 * </ul>
 *
 * <p>语义是「拥有其中任意一个权限即可」。可以标在方法上（只作用于该方法）
 * 或类上（作用于该 Controller 的所有接口），方法上的优先。
 *
 * <p>命名约定：{@code 模块:资源:动作}，例如 sys:user:add、sys:role:assign。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RequirePerm {

    /** 所需权限标识，满足任意一个即可 */
    String[] value();
}
