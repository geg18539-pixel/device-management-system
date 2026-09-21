package com.yan.backend.repository;

import com.yan.backend.entity.DeviceTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceTransferRepository extends JpaRepository<DeviceTransfer, Long> {

    /** 某台设备的调拨历史，最近的在前 */
    List<DeviceTransfer> findByDeviceIdOrderByTransferTimeDesc(Long deviceId);

    /** 删除设备前的保护检查：有调拨历史就不给删（审计资料要留着） */
    boolean existsByDeviceId(Long deviceId);
}
