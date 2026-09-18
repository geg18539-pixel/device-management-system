package com.yan.backend.service;

import com.yan.backend.dto.SysMenuTreeVO;
import com.yan.backend.entity.SysMenu;

import java.util.List;

public interface SysMenuService {

    /** 返回树形结构，直接给前端 el-tree / 树形表格用 */
    List<SysMenuTreeVO> tree();

    /** 平铺列表，按 sortOrder 排序 */
    List<SysMenu> listAll();

    SysMenu findById(Long id);

    SysMenu create(SysMenu menu);

    SysMenu update(Long id, SysMenu menu);

    void delete(Long id);
}
