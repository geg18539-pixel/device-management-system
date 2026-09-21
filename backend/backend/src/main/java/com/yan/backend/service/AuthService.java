package com.yan.backend.service;

import com.yan.backend.dto.ChangePasswordRequest;
import com.yan.backend.dto.CurrentUserVO;
import com.yan.backend.dto.LoginRequest;
import com.yan.backend.dto.LoginResponse;

public interface AuthService {

    /**
     * 校验用户名密码并签发 token。
     *
     * @throws com.yan.backend.exception.AuthException 用户名或密码错误、账号停用
     */
    LoginResponse login(LoginRequest request);

    /**
     * 当前登录用户的完整信息（含权限点、是否需改密、密码剩余天数）。
     *
     * <p>和 token 里那份 {@code LoginUser} 的区别：这里会查一次库，
     * 所以能拿到"密码什么时候改的"这类 token 装不下的信息。
     */
    CurrentUserVO currentUser(Long userId);

    /**
     * 用户自己修改密码。
     *
     * <p>返回的是**新的 token**：改完密码后原 token 里还挂着
     * "需强制改密"的标记，不换 token 的话用户会立刻又被拦一次。
     *
     * @throws com.yan.backend.exception.AuthException 原密码错误
     * @throws IllegalArgumentException 新密码强度不够、或与原密码相同
     */
    LoginResponse changePassword(Long userId, ChangePasswordRequest request);
}
