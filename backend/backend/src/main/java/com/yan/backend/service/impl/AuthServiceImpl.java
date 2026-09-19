package com.yan.backend.service.impl;

import com.yan.backend.common.JwtUtil;
import com.yan.backend.common.RequestUtils;
import com.yan.backend.dto.LoginRequest;
import com.yan.backend.dto.LoginResponse;
import com.yan.backend.entity.SysRole;
import com.yan.backend.entity.SysUser;
import com.yan.backend.exception.AuthException;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.AuthService;
import com.yan.backend.service.LoginLogRecorder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserRepository sysUserRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LoginLogRecorder loginLogRecorder;

    public AuthServiceImpl(SysUserRepository sysUserRepository,
                           JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder,
                           LoginLogRecorder loginLogRecorder) {
        this.sysUserRepository = sysUserRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.loginLogRecorder = loginLogRecorder;
    }

    /**
     * 校验用户名密码并签发 token。
     *
     * <p><b>注意这里的事务是**可写**的（不是 readOnly）</b>：
     * 登录成功要更新 lastLoginTime / lastLoginIp。之前的 readOnly=true
     * 会让 Hibernate 进入只读模式、脏检查不生效，那两个字段会**静默不更新** ——
     * 不报错、也不写库，最难查的那种。
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {

        // 请求信息只取一次，后面成功失败的日志都用它
        String ip = RequestUtils.getClientIp();
        String userAgent = RequestUtils.getUserAgent();

        SysUser user = sysUserRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null) {
            // 日志里记真实原因（"用户名不存在"），但**返回给用户的文案要和密码错误一致** ——
            // 否则等于提供了一个"这个账号存不存在"的探测接口
            loginLogRecorder.recordFailure(request.getUsername(), null, ip, userAgent, "用户名不存在");
            throw new AuthException("用户名或密码错误");
        }

        // BCrypt 比对。注意不能直接比较字符串 —— BCrypt 每次加密结果都不同，
        // 必须用 matches() 做哈希校验
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginLogRecorder.recordFailure(user.getUsername(), user.getId(), ip, userAgent, "密码错误");
            throw new AuthException("用户名或密码错误");
        }

        if (!"正常".equals(user.getStatus())) {
            loginLogRecorder.recordFailure(user.getUsername(), user.getId(), ip, userAgent,
                    "账号已停用");
            throw new AuthException("账号已停用，请联系管理员");
        }

        Set<String> roleKeys = user.getRoles().stream()
                .filter(role -> "正常".equals(role.getStatus()))
                .map(SysRole::getRoleKey)
                .collect(Collectors.toSet());

        // 记录最后登录信息。用户页面的详情弹窗和列表都展示这两个字段
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        sysUserRepository.save(user);

        loginLogRecorder.recordSuccess(user, ip, userAgent);

        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getNickname(), roleKeys);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(jwtUtil.getExpireSeconds());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRoles(roleKeys);
        return response;
    }
}
