package com.yan.backend.service.impl;

import com.yan.backend.common.JwtUtil;
import com.yan.backend.dto.LoginRequest;
import com.yan.backend.dto.LoginResponse;
import com.yan.backend.entity.SysRole;
import com.yan.backend.entity.SysUser;
import com.yan.backend.exception.AuthException;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserRepository sysUserRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserRepository sysUserRepository,
                           JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // 用带 EntityGraph 的查询，角色在这一步就一并查出来了。
        // 如果换成普通的 findById 之类的查询，出了这个事务再访问 getRoles()
        // 会抛 LazyInitializationException（因为开了 open-in-view=false）。
        SysUser user = sysUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthException("用户名或密码错误"));

        // BCrypt 比对。注意这里不能直接比较字符串 —— BCrypt 每次加密结果都不同，
        // 必须用 matches() 做哈希校验。
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException("用户名或密码错误");
        }

        // 不区分"用户名不存在"和"密码错误"的提示文案，避免被用来枚举用户名
        if (!"正常".equals(user.getStatus())) {
            throw new AuthException("账号已停用，请联系管理员");
        }

        Set<String> roleKeys = user.getRoles().stream()
                .filter(role -> "正常".equals(role.getStatus()))
                .map(SysRole::getRoleKey)
                .collect(Collectors.toSet());

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roleKeys);

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
