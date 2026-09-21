package com.yan.backend.repository;

import com.yan.backend.entity.SysMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SysMessageRepository
        extends JpaRepository<SysMessage, Long>, JpaSpecificationExecutor<SysMessage> {

    /** 未读数。只看"未读"，所以用 readFlag <> TRUE 兼容老数据里的 NULL */
    @Query("select count(m) from SysMessage m "
            + "where m.receiverId = :receiverId and (m.readFlag is null or m.readFlag = false)")
    long countUnread(@Param("receiverId") Long receiverId);

    /** 最近的若干条，给顶栏下拉用 */
    Page<SysMessage> findByReceiverIdOrderByCreateTimeDesc(Long receiverId, Pageable pageable);

    /** 幂等判断：这个键发过没有 */
    Optional<SysMessage> findByBizKey(String bizKey);

    boolean existsByBizKey(String bizKey);

    /**
     * 全部标记已读。
     *
     * <p>批量 update 而不是把实体查出来逐个改：未读消息可能有几百条，
     * 逐条改会产生几百条 UPDATE。
     */
    @Transactional
    @Modifying
    @Query("update SysMessage m set m.readFlag = true, m.readTime = :readTime "
            + "where m.receiverId = :receiverId and (m.readFlag is null or m.readFlag = false)")
    int markAllRead(@Param("receiverId") Long receiverId,
                    @Param("readTime") LocalDateTime readTime);

    /** 删除某人的全部已读消息 */
    @Transactional
    @Modifying
    @Query("delete from SysMessage m where m.receiverId = :receiverId and m.readFlag = true")
    int deleteAllRead(@Param("receiverId") Long receiverId);

    /** 用户被删除时清掉他的消息，避免留下指向不存在用户的孤儿记录 */
    void deleteByReceiverId(Long receiverId);

    List<SysMessage> findByReceiverIdAndReadFlag(Long receiverId, Boolean readFlag);

    long countByReceiverId(Long receiverId);
}
