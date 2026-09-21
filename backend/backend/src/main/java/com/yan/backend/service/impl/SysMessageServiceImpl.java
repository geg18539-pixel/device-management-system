package com.yan.backend.service.impl;

import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysMessage;
import com.yan.backend.entity.SysUser;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysMessageRepository;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.SysMessageService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class SysMessageServiceImpl implements SysMessageService {

    private static final Logger log = LoggerFactory.getLogger(SysMessageServiceImpl.class);

    private final SysMessageRepository messageRepository;
    private final SysUserRepository sysUserRepository;

    public SysMessageServiceImpl(SysMessageRepository messageRepository,
                                 SysUserRepository sysUserRepository) {
        this.messageRepository = messageRepository;
        this.sysUserRepository = sysUserRepository;
    }

    // ============================================================
    // 发送
    // ============================================================

    @Override
    @Transactional
    public boolean send(Long receiverId, String msgType, String title, String content,
                        String level, String bizType, Long bizId, String bizKey) {
        if (receiverId == null) {
            log.warn("消息接收人为空，已跳过。类型={}，标题={}", msgType, title);
            return false;
        }
        // 幂等键已存在就直接跳过（定时任务重复跑时走这条路）。
        // 先查一次能避免绝大多数重复，剩下的并发情况由唯一约束兜底
        if (StringUtils.hasText(bizKey) && messageRepository.existsByBizKey(bizKey)) {
            return false;
        }

        SysMessage message = new SysMessage();
        message.setReceiverId(receiverId);
        message.setReceiverName(resolveUsername(receiverId));
        message.setMsgType(msgType);
        message.setTitle(title);
        message.setContent(content);
        message.setLevel(StringUtils.hasText(level) ? level : SysMessage.LEVEL_NORMAL);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setBizKey(bizKey);
        message.setReadFlag(Boolean.FALSE);

        try {
            messageRepository.save(message);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 唯一约束挡住了并发下的重复插入。这不是错误，是去重生效了
            log.debug("消息已存在（幂等键 {}），跳过发送", bizKey);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean sendToUsername(String username, String msgType, String title, String content,
                                  String level, String bizType, Long bizId, String bizKey) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        SysUser receiver = resolveUser(username.trim());
        if (receiver == null) {
            // ★ 不静默：这是"通知没送达"的唯一线索。
            // 用 WARN 不用 ERROR —— 外部临时维修工本来就没有账号，属于正常情况
            log.warn("找不到用户「{}」，消息未能送达。类型={}，标题={}",
                    username, msgType, title);
            return false;
        }
        if (!"正常".equals(receiver.getStatus())) {
            log.warn("用户「{}」已停用，消息未能送达。标题={}", username, title);
            return false;
        }
        return send(receiver.getId(), msgType, title, content, level, bizType, bizId, bizKey);
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public long countUnread(Long userId) {
        return userId == null ? 0 : messageRepository.countUnread(userId);
    }

    @Override
    public List<SysMessage> recent(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(0, Math.max(limit, 1));
        return messageRepository.findByReceiverIdOrderByCreateTimeDesc(userId, pageable).getContent();
    }

    @Override
    public PageResult<SysMessage> page(Long userId, Boolean readFlag, String msgType,
                                       int pageNum, int pageSize) {
        if (userId == null) {
            return new PageResult<>(List.of(), 0, 1, pageSize);
        }

        Specification<SysMessage> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // ★ 第一条件永远是"发给我的"。用 Specification 动态拼条件时，
            // 很容易在加筛选条件的时候把这条漏掉 —— 那就会变成"能看所有人的消息"
            predicates.add(cb.equal(root.get("receiverId"), userId));

            if (readFlag != null) {
                if (Boolean.TRUE.equals(readFlag)) {
                    predicates.add(cb.equal(root.get("readFlag"), true));
                } else {
                    predicates.add(cb.or(cb.isNull(root.get("readFlag")),
                            cb.equal(root.get("readFlag"), false)));
                }
            }
            if (StringUtils.hasText(msgType)) {
                predicates.add(cb.equal(root.get("msgType"), msgType.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(
                Math.max(pageNum, 1) - 1, Math.max(pageSize, 1),
                Sort.by(Sort.Direction.DESC, "createTime", "id"));

        return PageResult.of(messageRepository.findAll(spec, pageable));
    }

    // ============================================================
    // 操作（每一处都要校验归属）
    // ============================================================

    @Override
    @Transactional
    public SysMessage markRead(Long id, Long userId) {
        SysMessage message = getOwned(id, userId);
        if (!message.isRead()) {
            message.setReadFlag(Boolean.TRUE);
            message.setReadTime(LocalDateTime.now());
            messageRepository.save(message);
        }
        return message;
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        // 批量 update，不把几百条未读逐个查出来改
        return messageRepository.markAllRead(userId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        messageRepository.delete(getOwned(id, userId));
    }

    @Override
    @Transactional
    public int deleteAllRead(Long userId) {
        return userId == null ? 0 : messageRepository.deleteAllRead(userId);
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    /**
     * 取出消息并校验它属于当前用户。
     *
     * <p>不属于时抛 **404 而不是 403**：403 等于告诉对方"这条 id 是存在的，
     * 只是不属于你"，可以用来逐个 id 探测出系统里有多少条消息。
     * 返回"不存在"则不泄露任何信息。
     */
    private SysMessage getOwned(Long id, Long userId) {
        SysMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("消息不存在，id = " + id));
        if (!Objects.equals(message.getReceiverId(), userId)) {
            log.warn("用户 {} 试图操作不属于自己的消息 {}", userId, id);
            throw new ResourceNotFoundException("消息不存在，id = " + id);
        }
        return message;
    }

    private SysUser resolveUser(String username) {
        return sysUserRepository.findByUsername(username).orElse(null);
    }

    private String resolveUsername(Long userId) {
        return sysUserRepository.findById(userId).map(SysUser::getUsername).orElse(null);
    }
}
