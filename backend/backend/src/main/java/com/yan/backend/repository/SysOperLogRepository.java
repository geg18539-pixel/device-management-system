package com.yan.backend.repository;

import com.yan.backend.entity.SysOperLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysOperLogRepository extends JpaRepository<SysOperLog, Long> {
}
