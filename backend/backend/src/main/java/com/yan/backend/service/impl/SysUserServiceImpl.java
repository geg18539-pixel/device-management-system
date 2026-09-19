package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SysUserSaveRequest;
import com.yan.backend.dto.SysUserVO;
import com.yan.backend.entity.SysRole;
import com.yan.backend.entity.SysUser;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysRoleRepository;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.PermissionService;
import com.yan.backend.service.SysUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SysUserServiceImpl implements SysUserService {

    /** 内置超管用户名，不允许被删除 */
    private static final String BUILTIN_ADMIN = "admin";

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;

    public SysUserServiceImpl(SysUserRepository sysUserRepository,
                              SysRoleRepository sysRoleRepository,
                              PasswordEncoder passwordEncoder,
                              PermissionService permissionService) {
        this.sysUserRepository = sysUserRepository;
        this.sysRoleRepository = sysRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
    }

    @Override
    public PageResult<SysUserVO> page(int pageNum, int pageSize, String username) {
        Pageable pageable = PageRequest.of(
                Math.max(pageNum, 1) - 1, pageSize, Sort.by(Sort.Direction.ASC, "id"));

        Page<SysUser> page = (username == null || username.isBlank())
                ? sysUserRepository.findAll(pageable)
                : sysUserRepository.findByUsernameContaining(username.trim(), pageable);

        // 映射必须在事务内做：toVO 里会读 user.getRoles()，
        // 出了事务 Session 就关了，会抛 LazyInitializationException
        return PageResult.of(page, this::toVO);
    }

    @Override
    public SysUserVO findById(Long id) {
        return toVO(getUser(id));
    }

    @Override
    @Transactional
    public SysUserVO create(SysUserSaveRequest request) {
        if (sysUserRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("用户名已存在：" + request.getUsername());
        }

        // 新增时密码必填。这种"条件必填"注解表达不了，只能手工校验
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("新增用户时密码不能为空");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() == null || request.getStatus().isBlank()
                ? "正常" : request.getStatus());
        user.setRoles(resolveRoles(request.getRoleIds()));

        return toVO(sysUserRepository.save(user));
    }

    @Override
    @Transactional
    public SysUserVO update(Long id, SysUserSaveRequest request) {
        SysUser existing = getUser(id);

        // 换用户名才查重，否则会和自己撞上
        if (!existing.getUsername().equals(request.getUsername())
                && sysUserRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("用户名已存在：" + request.getUsername());
        }

        existing.setUsername(request.getUsername());
        existing.setNickname(request.getNickname());
        existing.setEmail(request.getEmail());
        existing.setPhone(request.getPhone());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            existing.setStatus(request.getStatus());
        }

        // 密码留空表示不修改。这里不能写成 existing.setPassword(request.getPassword())，
        // 那会把 null 或空串写进去，用户就再也登录不了了
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // roleIds 为 null 表示这次请求不涉及角色，保持原样；
        // 传空数组才是"清空角色"。
        if (request.getRoleIds() != null) {
            existing.setRoles(resolveRoles(request.getRoleIds()));
            // 角色变了权限就变了，清缓存让它立刻生效
            permissionService.evictAfterCommit();
        }

        return toVO(sysUserRepository.save(existing));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = getUser(id);

        if (BUILTIN_ADMIN.equals(user.getUsername())) {
            throw new IllegalStateException("内置管理员账号不允许删除");
        }

        // 防止把自己删了导致会话失效、又没人能再进系统
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new IllegalStateException("不能删除当前登录的账号");
        }

        sysUserRepository.deleteById(id);
        permissionService.evictAfterCommit();
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = getUser(userId);
        user.setRoles(resolveRoles(roleIds));
        sysUserRepository.save(user);
        permissionService.evictAfterCommit();
    }

    @Override
    public List<Long> findRoleIds(Long userId) {
        return getUser(userId).getRoles().stream()
                .map(SysRole::getId)
                .toList();
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 4 || newPassword.length() > 64) {
            throw new IllegalArgumentException("密码长度需在 4 到 64 个字符之间");
        }
        SysUser user = getUser(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserRepository.save(user);
    }

    private SysUser getUser(Long id) {
        return sysUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在，id = " + id));
    }

    /** 把角色 id 列表解析成角色实体集合，非法 id 直接报错而不是静默忽略 */
    private Set<SysRole> resolveRoles(List<Long> roleIds) {
        Set<SysRole> roles = new HashSet<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return roles;
        }

        // 一次查完，不要循环 findById（N 次查询）
        List<SysRole> found = sysRoleRepository.findAllById(roleIds);
        if (found.size() != roleIds.stream().distinct().count()) {
            throw new IllegalArgumentException("部分角色不存在，请刷新后重试");
        }
        roles.addAll(found);
        return roles;
    }

    private SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());

        // 注意这里不能漏掉密码字段的排除 —— VO 里干脆就没有 password 这个属性，
        // 比在实体上靠 @JsonIgnore 更保险
        vo.setRoleIds(user.getRoles().stream().map(SysRole::getId).toList());
        vo.setRoleNames(user.getRoles().stream().map(SysRole::getRoleName).toList());
        return vo;
    }
}
