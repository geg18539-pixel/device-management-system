package com.yan.backend.service;

import com.yan.backend.dto.DeptTreeVO;
import com.yan.backend.entity.SysDept;

import java.util.List;

public interface SysDeptService {

    /** 树形结构，给部门树 / 树形表格 / 部门下拉用 */
    List<DeptTreeVO> tree();

    List<SysDept> listAll();

    SysDept findById(Long id);

    SysDept create(SysDept dept);

    SysDept update(Long id, SysDept dept);

    void delete(Long id);
}
