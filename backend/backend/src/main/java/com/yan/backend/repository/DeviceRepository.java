package com.yan.backend.repository;

import com.yan.backend.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 设备数据访问层。
 *
 * <p>继承 JpaRepository 即可直接获得 findAll / findById / save / deleteById
 * / existsById / count 等常用方法，不需要写任何实现代码 —— Spring Data JPA
 * 会在启动时生成代理实现类。
 *
 * <p>泛型参数：第一个是实体类型，第二个是主键类型。
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
}
