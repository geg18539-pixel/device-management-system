package com.yan.backend.service.impl;

import com.yan.backend.entity.Device;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.DeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备业务实现。
 *
 * <p>类上加 @Transactional(readOnly = true) 作为默认值，让所有查询走只读事务
 * （Hibernate 会因此跳过脏检查，性能更好）；写操作再各自覆盖成可写事务。
 */
@Service
@Transactional(readOnly = true)
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;

    /** 构造器注入：依赖不可变，且不依赖 Spring 也能直接 new 出来做单元测试 */
    public DeviceServiceImpl(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public List<Device> findAll() {
        return deviceRepository.findAll();
    }

    @Override
    public Device findById(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在，id = " + id));
    }

    @Override
    @Transactional
    public Device save(Device device) {
        // id 是数据库自增的。这里强制置空，避免调用方（或恶意请求）传了 id，
        // 导致 JPA 把 insert 变成 update、悄悄覆盖掉已有数据。
        device.setId(null);

        return deviceRepository.save(device);
    }

    @Override
    @Transactional
    public Device update(Long id, Device device) {
        Device existing = findById(id);

        // 逐个字段覆盖，而不是直接 save(device)：
        // createTime 由 @CreationTimestamp 维护，不能让请求体里的值覆盖掉。
        existing.setDeviceName(device.getDeviceName());
        existing.setDeviceType(device.getDeviceType());
        existing.setSerialNumber(device.getSerialNumber());
        existing.setStatus(device.getStatus());
        existing.setLocation(device.getLocation());
        existing.setDescription(device.getDescription());

        return deviceRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 先判断再删，让"删除一个不存在的 id"返回 404 而不是静默成功
        if (!deviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("设备不存在，id = " + id);
        }

        deviceRepository.deleteById(id);
    }
}
