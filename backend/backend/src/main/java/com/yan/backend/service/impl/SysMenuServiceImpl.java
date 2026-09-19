package com.yan.backend.service.impl;

import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SysMenuTreeVO;
import com.yan.backend.entity.SysMenu;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysMenuRepository;
import com.yan.backend.service.PermissionService;
import com.yan.backend.service.SysMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuRepository sysMenuRepository;
    private final PermissionService permissionService;

    public SysMenuServiceImpl(SysMenuRepository sysMenuRepository,
                              PermissionService permissionService) {
        this.sysMenuRepository = sysMenuRepository;
        this.permissionService = permissionService;
    }

    @Override
    public List<SysMenuTreeVO> tree() {
        List<SysMenu> all = sysMenuRepository.findAllByOrderBySortOrderAsc();

        // 先建一遍 id -> VO 的索引，再挂父子关系。
        // 这样只需要遍历两次、不需要递归，也不会因为数据里出现环而死循环
        // （父节点找不到时就直接当成根节点）。
        Map<Long, SysMenuTreeVO> index = new LinkedHashMap<>();
        for (SysMenu menu : all) {
            index.put(menu.getId(), toVO(menu));
        }

        List<SysMenuTreeVO> roots = new ArrayList<>();
        for (SysMenu menu : all) {
            SysMenuTreeVO node = index.get(menu.getId());
            SysMenuTreeVO parent = index.get(menu.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    public List<SysMenu> listAll() {
        return sysMenuRepository.findAllByOrderBySortOrderAsc();
    }

    @Override
    public SysMenu findById(Long id) {
        return sysMenuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("菜单不存在，id = " + id));
    }

    @Override
    @Transactional
    public SysMenu create(SysMenu menu) {
        menu.setId(null);
        if (menu.getParentId() == null) {
            menu.setParentId(SysMenu.ROOT_PARENT_ID);
        }
        return sysMenuRepository.save(menu);
    }

    @Override
    @Transactional
    public SysMenu update(Long id, SysMenu menu) {
        SysMenu existing = findById(id);

        // 不允许把菜单挂到自己下面，否则树结构会出现自引用死循环
        if (id.equals(menu.getParentId())) {
            throw new IllegalArgumentException("不能把菜单的上级设置为自己");
        }

        existing.setMenuName(menu.getMenuName());
        existing.setParentId(menu.getParentId() == null ? SysMenu.ROOT_PARENT_ID : menu.getParentId());
        existing.setPath(menu.getPath());
        existing.setComponent(menu.getComponent());
        existing.setMenuType(menu.getMenuType());
        existing.setPerms(menu.getPerms());
        existing.setIcon(menu.getIcon());
        if (menu.getSortOrder() != null) {
            existing.setSortOrder(menu.getSortOrder());
        }
        if (menu.getVisible() != null) {
            existing.setVisible(menu.getVisible());
        }
        return sysMenuRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findById(id);

        // 有子菜单时不允许直接删，否则子节点会变成"孤儿"挂在树外面看不见
        if (sysMenuRepository.existsByParentId(id)) {
            throw new IllegalStateException("该菜单下还有子菜单，请先删除子菜单");
        }

        sysMenuRepository.deleteById(id);
        // 删掉的可能是个按钮权限点，权限集合变了，清缓存
        permissionService.evictAfterCommit();
    }

    private SysMenuTreeVO toVO(SysMenu menu) {
        SysMenuTreeVO vo = new SysMenuTreeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setMenuType(menu.getMenuType());
        vo.setPerms(menu.getPerms());
        vo.setIcon(menu.getIcon());
        vo.setSortOrder(menu.getSortOrder());
        vo.setVisible(menu.getVisible());
        return vo;
    }
}
