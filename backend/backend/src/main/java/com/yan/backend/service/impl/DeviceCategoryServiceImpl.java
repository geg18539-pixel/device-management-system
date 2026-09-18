package com.yan.backend.service.impl;

import com.yan.backend.dto.DeviceCategoryTreeVO;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.DeviceCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DeviceCategoryServiceImpl implements DeviceCategoryService {

    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceRepository deviceRepository;

    public DeviceCategoryServiceImpl(DeviceCategoryRepository deviceCategoryRepository,
                                     DeviceRepository deviceRepository) {
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.deviceRepository = deviceRepository;
    }

    @Override
    public List<DeviceCategoryTreeVO> tree() {
        List<DeviceCategory> all = deviceCategoryRepository.findAllByOrderBySortOrderAsc();

        // 先建 id -> VO 索引，再挂父子关系。
        // 只遍历两次、不用递归，数据里即使出现环也不会死循环
        // （找不到父节点的就当根节点处理）。
        Map<Long, DeviceCategoryTreeVO> index = new LinkedHashMap<>();
        for (DeviceCategory category : all) {
            index.put(category.getId(), toVO(category));
        }

        List<DeviceCategoryTreeVO> roots = new ArrayList<>();
        for (DeviceCategory category : all) {
            DeviceCategoryTreeVO node = index.get(category.getId());
            DeviceCategoryTreeVO parent = index.get(category.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    public List<DeviceCategory> listAll() {
        return deviceCategoryRepository.findAllByOrderBySortOrderAsc();
    }

    @Override
    public DeviceCategory findById(Long id) {
        return deviceCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("分类不存在，id = " + id));
    }

    @Override
    @Transactional
    public DeviceCategory create(DeviceCategory category) {
        category.setId(null);
        if (category.getParentId() == null) {
            category.setParentId(DeviceCategory.ROOT_PARENT_ID);
        }
        if (deviceCategoryRepository.existsByCategoryNameAndParentId(
                category.getCategoryName(), category.getParentId())) {
            throw new IllegalStateException("同级下已存在同名分类：" + category.getCategoryName());
        }
        return deviceCategoryRepository.save(category);
    }

    @Override
    @Transactional
    public DeviceCategory update(Long id, DeviceCategory category) {
        DeviceCategory existing = findById(id);

        // 不能把分类挂到自己下面，否则树结构出现自引用死循环
        if (id.equals(category.getParentId())) {
            throw new IllegalArgumentException("不能把分类的上级设置为自己");
        }

        Long newParentId = category.getParentId() == null
                ? DeviceCategory.ROOT_PARENT_ID : category.getParentId();

        if (!existing.getCategoryName().equals(category.getCategoryName())
                || !existing.getParentId().equals(newParentId)) {
            if (deviceCategoryRepository.existsByCategoryNameAndParentId(
                    category.getCategoryName(), newParentId)) {
                throw new IllegalStateException("同级下已存在同名分类：" + category.getCategoryName());
            }
        }

        existing.setCategoryName(category.getCategoryName());
        existing.setParentId(newParentId);
        existing.setRemark(category.getRemark());
        if (category.getSortOrder() != null) {
            existing.setSortOrder(category.getSortOrder());
        }
        return deviceCategoryRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findById(id);

        if (deviceCategoryRepository.existsByParentId(id)) {
            throw new IllegalStateException("该分类下还有子分类，请先删除子分类");
        }
        // 有设备在用这个分类时不允许删，否则那些设备的 category_id 会变成悬空引用
        if (deviceRepository.existsByCategoryId(id)) {
            throw new IllegalStateException("该分类下还有设备，请先调整这些设备的分类");
        }

        deviceCategoryRepository.deleteById(id);
    }

    private DeviceCategoryTreeVO toVO(DeviceCategory category) {
        DeviceCategoryTreeVO vo = new DeviceCategoryTreeVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setCategoryName(category.getCategoryName());
        vo.setSortOrder(category.getSortOrder());
        vo.setRemark(category.getRemark());
        return vo;
    }
}
