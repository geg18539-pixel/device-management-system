package com.yan.backend.service;

import com.yan.backend.entity.Device;

import java.util.List;

/**
 * 设备业务接口。
 *
 * <p>把接口和实现分开，Controller 只依赖这个接口而不依赖具体实现，
 * 以后要换实现、加缓存或加事务代理都不影响上层。
 */
public interface DeviceService {

    /** 查询全部设备 */
    List<Device> findAll();

    /** 按 id 查询，不存在时抛 ResourceNotFoundException */
    Device findById(Long id);

    /** 新增设备 */
    Device save(Device device);

    /** 按 id 更新设备 */
    Device update(Long id, Device device);

    /** 按 id 删除设备 */
    void delete(Long id);
}
