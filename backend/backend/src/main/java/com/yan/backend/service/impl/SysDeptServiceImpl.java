package com.yan.backend.service.impl;

import com.yan.backend.dto.DeptTreeVO;
import com.yan.backend.entity.SysDept;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.SysDeptRepository;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.SysDeptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SysDeptServiceImpl implements SysDeptService {

    private final SysDeptRepository sysDeptRepository;
    private final DeviceRepository deviceRepository;
    private final SysUserRepository sysUserRepository;

    public SysDeptServiceImpl(SysDeptRepository sysDeptRepository,
                              DeviceRepository deviceRepository,
                              SysUserRepository sysUserRepository) {
        this.sysDeptRepository = sysDeptRepository;
        this.deviceRepository = deviceRepository;
        this.sysUserRepository = sysUserRepository;
    }

    @Override
    public List<DeptTreeVO> tree() {
        List<SysDept> all = sysDeptRepository.findAllByOrderBySortOrderAscIdAsc();

        // 先建 id -> VO 索引，再挂父子关系。
        // 只遍历两次、不用递归，数据里即使出现环也不会死循环
        // （找不到父节点的就当顶级处理）。
        Map<Long, DeptTreeVO> index = new LinkedHashMap<>();
        for (SysDept dept : all) {
            index.put(dept.getId(), toVO(dept));
        }

        List<DeptTreeVO> roots = new ArrayList<>();
        for (SysDept dept : all) {
            DeptTreeVO node = index.get(dept.getId());
            DeptTreeVO parent = index.get(dept.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    public List<SysDept> listAll() {
        return sysDeptRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Override
    public SysDept findById(Long id) {
        return sysDeptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("部门不存在，id = " + id));
    }

    @Override
    @Transactional
    public SysDept create(SysDept dept) {
        dept.setId(null);
        if (dept.getParentId() == null) {
            dept.setParentId(SysDept.ROOT_PARENT_ID);
        }
        if (sysDeptRepository.existsByDeptNameAndParentId(dept.getDeptName(), dept.getParentId())) {
            throw new IllegalStateException("同级下已存在同名部门：" + dept.getDeptName());
        }
        if (!StringUtils.hasText(dept.getStatus())) {
            dept.setStatus(SysDept.STATUS_NORMAL);
        }
        return sysDeptRepository.save(dept);
    }

    @Override
    @Transactional
    public SysDept update(Long id, SysDept dept) {
        SysDept existing = findById(id);

        Long newParentId = dept.getParentId() == null
                ? SysDept.ROOT_PARENT_ID : dept.getParentId();

        // 不能把部门挂到自己下面，否则树结构出现自引用死循环。
        // 更隐蔽的是"挂到自己的后代下面" —— 那会把整个子树从树里摘出去，
        // 变成一个孤立的环，页面上再也看不到。所以这里要往下查一遍。
        if (id.equals(newParentId)) {
            throw new IllegalArgumentException("不能把部门的上级设置为自己");
        }
        if (isDescendant(id, newParentId)) {
            throw new IllegalArgumentException("不能把部门移动到它自己的下级部门下");
        }

        if (!existing.getDeptName().equals(dept.getDeptName())
                || !existing.getParentId().equals(newParentId)) {
            if (sysDeptRepository.existsByDeptNameAndParentId(dept.getDeptName(), newParentId)) {
                throw new IllegalStateException("同级下已存在同名部门：" + dept.getDeptName());
            }
        }

        existing.setDeptName(dept.getDeptName());
        existing.setParentId(newParentId);
        existing.setLeader(dept.getLeader());
        existing.setPhone(dept.getPhone());
        existing.setRemark(dept.getRemark());
        if (dept.getSortOrder() != null) {
            existing.setSortOrder(dept.getSortOrder());
        }
        if (StringUtils.hasText(dept.getStatus())) {
            existing.setStatus(dept.getStatus());
        }
        return sysDeptRepository.save(existing);
    }

    /**
     * 判断 candidate 是不是 deptId 的后代（含自己）。
     *
     * <p>用来拦住"把部门移动到自己的下级"这种操作 —— 一旦允许，
     * 那棵子树就从主树上脱开了，页面上直接消失。
     */
    private boolean isDescendant(Long deptId, Long candidateId) {
        if (candidateId == null || deptId.equals(candidateId)) {
            return true;
        }
        // 部门层级不会很深，逐级往上找足够；而且比一次性把所有节点读进内存更省
        Long current = candidateId;
        int guard = 0;
        while (current != null && !SysDept.ROOT_PARENT_ID.equals(current) && guard++ < 100) {
            if (deptId.equals(current)) {
                return true;
            }
            current = sysDeptRepository.findById(current)
                    .map(SysDept::getParentId)
                    .orElse(null);
        }
        return false;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findById(id);

        if (sysDeptRepository.existsByParentId(id)) {
            throw new IllegalStateException("该部门下还有子部门，请先删除子部门");
        }
        // 有设备或用户还挂在这个部门上时不允许删，
        // 否则那些记录的 deptId 会变成指向不存在部门的悬空引用
        if (deviceRepository.existsByDeptId(id)) {
            throw new IllegalStateException("该部门下还有设备，请先调整这些设备的归属部门");
        }
        if (sysUserRepository.existsByDeptId(id)) {
            throw new IllegalStateException("该部门下还有用户，请先调整这些用户的归属部门");
        }

        sysDeptRepository.deleteById(id);
    }

    private DeptTreeVO toVO(SysDept dept) {
        DeptTreeVO vo = new DeptTreeVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setDeptName(dept.getDeptName());
        vo.setSortOrder(dept.getSortOrder());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setStatus(dept.getStatus());
        vo.setRemark(dept.getRemark());
        return vo;
    }
}
