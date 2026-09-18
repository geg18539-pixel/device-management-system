package com.yan.backend.service.impl;

import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysMenu;
import com.yan.backend.entity.SysRole;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysMenuRepository;
import com.yan.backend.repository.SysRoleRepository;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.SysRoleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleRepository sysRoleRepository;
    private final SysMenuRepository sysMenuRepository;
    private final SysUserRepository sysUserRepository;

    public SysRoleServiceImpl(SysRoleRepository sysRoleRepository,
                              SysMenuRepository sysMenuRepository,
                              SysUserRepository sysUserRepository) {
        this.sysRoleRepository = sysRoleRepository;
        this.sysMenuRepository = sysMenuRepository;
        this.sysUserRepository = sysUserRepository;
    }

    @Override
    public PageResult<SysRole> page(int pageNum, int pageSize, String roleName) {
        Pageable pageable = PageRequest.of(
                Math.max(pageNum, 1) - 1, pageSize, Sort.by(Sort.Direction.ASC, "sortOrder"));

        Page<SysRole> page = (roleName == null || roleName.isBlank())
                ? sysRoleRepository.findAll(pageable)
                : sysRoleRepository.findByRoleNameContaining(roleName.trim(), pageable);

        return PageResult.of(page);
    }

    @Override
    public List<SysRole> listAll() {
        return sysRoleRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder"));
    }

    @Override
    public SysRole findById(Long id) {
        return sysRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在，id = " + id));
    }

    @Override
    @Transactional
    public SysRole create(SysRole role) {
        if (sysRoleRepository.existsByRoleKey(role.getRoleKey())) {
            throw new IllegalStateException("角色标识已存在：" + role.getRoleKey());
        }
        role.setId(null);
        return sysRoleRepository.save(role);
    }

    @Override
    @Transactional
    public SysRole update(Long id, SysRole role) {
        SysRole existing = findById(id);

        // 换 roleKey 时要查重；没换就不用查（否则会和自己撞上）
        if (!existing.getRoleKey().equals(role.getRoleKey())
                && sysRoleRepository.existsByRoleKey(role.getRoleKey())) {
            throw new IllegalStateException("角色标识已存在：" + role.getRoleKey());
        }

        existing.setRoleName(role.getRoleName());
        existing.setRoleKey(role.getRoleKey());
        existing.setRemark(role.getRemark());
        if (role.getSortOrder() != null) {
            existing.setSortOrder(role.getSortOrder());
        }
        if (role.getStatus() != null) {
            existing.setStatus(role.getStatus());
        }
        return sysRoleRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = findById(id);

        // 内置的 admin 角色不允许删，否则整个系统会失去超管入口
        if ("admin".equals(role.getRoleKey())) {
            throw new IllegalStateException("内置管理员角色不允许删除");
        }

        // 还有用户在用这个角色时不允许删。
        // 虽然 sys_user_role 有外键约束、删了数据库也会拦，但那样报的是
        // 500 + 一堆 SQL 异常，不如提前检查给出可读的提示。
        if (sysUserRepository.existsByRoles_Id(id)) {
            throw new IllegalStateException("该角色下还有用户，请先解除关联");
        }

        sysRoleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        SysRole role = findById(roleId);

        Set<SysMenu> menus = new HashSet<>();
        if (menuIds != null && !menuIds.isEmpty()) {
            // findAllById 一次查完，不要循环 findById（那是 N 次查询）
            List<SysMenu> found = sysMenuRepository.findAllById(menuIds);
            if (found.size() != menuIds.stream().distinct().count()) {
                throw new IllegalArgumentException("部分菜单不存在，请刷新后重试");
            }
            menus.addAll(found);
        }

        // 直接用 setMenus 整体替换。@ManyToMany 的拥有者一侧做集合替换，
        // Hibernate 会自动算出中间表要删哪些、插哪些。
        role.setMenus(menus);
        sysRoleRepository.save(role);
    }

    @Override
    public List<Long> findMenuIds(Long roleId) {
        return findById(roleId).getMenus().stream()
                .map(SysMenu::getId)
                .toList();
    }
}
