package com.yan.backend.service.impl;

import com.yan.backend.common.ConfigKeys;
import com.yan.backend.common.JwtUtil;
import com.yan.backend.common.PasswordPolicy;
import com.yan.backend.common.RequestUtils;
import com.yan.backend.dto.ChangePasswordRequest;
import com.yan.backend.dto.CurrentUserVO;
import com.yan.backend.dto.LoginRequest;
import com.yan.backend.dto.LoginResponse;
import com.yan.backend.entity.SysRole;
import com.yan.backend.entity.SysUser;
import com.yan.backend.exception.AuthException;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.AuthService;
import com.yan.backend.service.LoginLogRecorder;
import com.yan.backend.service.PermissionService;
import com.yan.backend.service.SysConfigService;
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
    private final PermissionService permissionService;
    private final SysConfigService configService;

    /**
     * 密码有效期（天）。<= 0 表示关闭过期策略。
     *
     * <p>从**系统参数**里读，不再是 application.yml 的固定值 ——
     * 管理员在「系统设置」页改完即时生效，不用重启。
     * 库里没配就回退到 yml 的默认值（见 ConfigKeys）。
     */
    private int passwordValidDays() {
        return configService.getInt(
                ConfigKeys.PASSWORD_VALID_DAYS, ConfigKeys.PASSWORD_VALID_DAYS_DEFAULT);
    }

    public AuthServiceImpl(SysUserRepository sysUserRepository,
                           JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder,
                           LoginLogRecorder loginLogRecorder,
                           PermissionService permissionService,
                           SysConfigService configService) {
        this.sysUserRepository = sysUserRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.loginLogRecorder = loginLogRecorder;
        this.permissionService = permissionService;
        this.configService = configService;
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

        // ★ 还在用公开的初始密码？直接标记为必须改密。
        // 这一道不是多余的：启动时的数据修补要等种子数据跑完，
        // 而 Tomcat 在那之前就已经监听端口了，极短时间内的登录会穿过那个窗口。
        // 在登录路径上再判一次，窗口就没了（这里比的是明文，不需要再做 BCrypt）
        if (PasswordPolicy.isKnownDefaultPassword(user.getUsername(), request.getPassword())) {
            user.setMustChangePassword(Boolean.TRUE);
        }

        // 记录最后登录信息。用户页面的详情弹窗和列表都展示这两个字段
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        sysUserRepository.save(user);

        loginLogRecorder.recordSuccess(user, ip, userAgent);

        return buildResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在，id = " + userId));

        // 必须校验原密码。即便是"被强制改密"的状态也要输 ——
        // 临时密码是管理员告知的，用户本来就知道；而这一道能挡住
        // "别人趁他没锁屏，直接给他改掉密码"的情况
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AuthException("原密码不正确");
        }

        String newPassword = request.getNewPassword();
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }
        String weak = PasswordPolicy.validate(newPassword);
        if (weak != null) {
            throw new IllegalArgumentException(weak);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPwdUpdateTime(LocalDateTime.now());
        // 改完就清掉强制标记，否则用户会一直被困在改密页
        user.setMustChangePassword(Boolean.FALSE);
        sysUserRepository.save(user);

        // 换发新 token：旧 token 里还挂着 pwdChange=true，不换的话
        // 用户改完密码立刻又被拦截器拦一次
        return buildResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserVO currentUser(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在，id = " + userId));

        CurrentUserVO vo = new CurrentUserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRoles(user.getRoles().stream()
                .filter(role -> "正常".equals(role.getStatus()))
                .map(SysRole::getRoleKey)
                .collect(Collectors.toSet()));
        vo.setPerms(permissionService.getPerms(userId));

        boolean expired = PasswordPolicy.isExpired(user.getPwdUpdateTime(), passwordValidDays());
        vo.setMustChangePassword(user.isMustChangePassword() || expired);
        vo.setPasswordExpireDays(
                PasswordPolicy.daysUntilExpire(user.getPwdUpdateTime(), passwordValidDays()));
        return vo;
    }

    /**
     * 组装登录/改密响应。
     *
     * <p>两处共用一份逻辑，避免"登录时返回了某字段、改密时漏了"这种不一致 ——
     * 改密后前端要拿这份数据重建整个用户状态。
     */
    private LoginResponse buildResponse(SysUser user) {
        Set<String> roleKeys = user.getRoles().stream()
                .filter(role -> "正常".equals(role.getStatus()))
                .map(SysRole::getRoleKey)
                .collect(Collectors.toSet());

        boolean expired = PasswordPolicy.isExpired(user.getPwdUpdateTime(), passwordValidDays());
        // 「管理员要求的」和「密码过期了」都会强制改密，但前端提示文案不同，
        // 所以两个标志都返回
        boolean mustChange = user.isMustChangePassword() || expired;

        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getNickname(), roleKeys, mustChange);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(jwtUtil.getExpireSeconds());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRoles(roleKeys);
        // 权限点登录时一并返回，前端不用再补一次 /auth/me 请求
        response.setPerms(permissionService.getPerms(user.getId()));
        response.setMustChangePassword(mustChange);
        response.setPasswordExpired(expired);
        response.setPasswordExpireDays(
                PasswordPolicy.daysUntilExpire(user.getPwdUpdateTime(), passwordValidDays()));
        return response;
    }
}
