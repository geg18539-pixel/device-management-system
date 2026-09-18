package com.yan.backend.service;

import com.yan.backend.dto.DeviceCategoryTreeVO;
import com.yan.backend.entity.DeviceCategory;

import java.util.List;

public interface DeviceCategoryService {

    /** 树形结构，给分类下拉和树形表格用 */
    List<DeviceCategoryTreeVO> tree();

    List<DeviceCategory> listAll();

    DeviceCategory findById(Long id);

    DeviceCategory create(DeviceCategory category);

    DeviceCategory update(Long id, DeviceCategory category);

    void delete(Long id);
}
