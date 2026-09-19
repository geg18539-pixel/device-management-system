package com.yan.backend.service.impl;

import com.yan.backend.common.PasswordPolicy;
import com.yan.backend.common.UserContext;
import com.yan.backend.dto.BatchResultVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SysUserQuery;
import com.yan.backend.dto.SysUserSaveRequest;
import com.yan.backend.dto.SysUserVO;
import com.yan.backend.entity.SysRole;
import com.yan.backend.entity.SysUser;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysRoleRepository;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.PermissionService;
import com.yan.backend.service.SysUserService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SysUserServiceImpl implements SysUserService {

    /** 内置超管用户名，不允许被删除或停用 */
    private static final String BUILTIN_ADMIN = "admin";

    /** 账号状态取值 */
    private static final String STATUS_NORMAL = "正常";
    private static final String STATUS_DISABLED = "停用";

    /** 单次导出最多多少条，防止把整个工作簿建进内存时撑爆堆 */
    private static final int EXPORT_LIMIT = 10_000;

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;

    public SysUserServiceImpl(SysUserRepository sysUserRepository,
                              SysRoleRepository sysRoleRepository,
                              PasswordEncoder passwordEncoder,
                              PermissionService permissionService) {
        this.sysUserRepository = sysUserRepository;
        this.sysRoleRepository = sysRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public PageResult<SysUserVO> page(SysUserQuery query, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(
                Math.max(pageNum, 1) - 1, pageSize, buildSort(query));

        Page<SysUser> page = sysUserRepository.findAll(buildSpec(query), pageable);

        // 映射必须在事务内做：toVO 里会读 user.getRoles()，
        // 出了事务 Session 就关了，会抛 LazyInitializationException
        return PageResult.of(page, this::toVO);
    }

    @Override
    public List<SysUserVO> listForExport(SysUserQuery query) {
        // 导出不分页，但要有个上限：万一以后数据涨到几十万，
        // 一次性把整个工作簿建在内存里会把堆撑爆。
        // 超出上限时按当前排序取前 EXPORT_LIMIT 条。
        List<SysUser> users = sysUserRepository.findAll(buildSpec(query), buildSort(query))
                .stream()
                .limit(EXPORT_LIMIT)
                .toList();
        return users.stream().map(this::toVO).toList();
    }

    /**
     * 动态拼查询条件。
     *
     * <p>用 Specification 而不是写一堆 findByUsernameContainingAndStatusAndRolessId...
     * 的组合方法：条件有 5 个，组合有 2^5 种，方法名会爆炸。
     */
    private Specification<SysUser> buildSpec(SysUserQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query.getUsername())) {
                predicates.add(cb.like(root.get("username"),
                        "%" + query.getUsername().trim() + "%"));
            }

            if (query.getRoleId() != null) {
                // 角色是多对多，得 join 中间表。join 会让"一个用户匹配多个角色"
                // 时出现重复行，所以要配合 distinct —— 见下面的 if
                Join<SysUser, SysRole> roleJoin = root.join("roles");
                predicates.add(cb.equal(roleJoin.get("id"), query.getRoleId()));
            }

            if (StringUtils.hasText(query.getStatus())) {
                predicates.add(cb.equal(root.get("status"), query.getStatus().trim()));
            }

            if (query.getCreateTimeBegin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"),
                        query.getCreateTimeBegin().atStartOfDay()));
            }

            if (query.getCreateTimeEnd() != null) {
                // ★ 结束日期必须取当天最后一刻。
                // 用 atStartOfDay() 的话，"创建时间到 9 月 19 日"会把 9 月 19 日
                // 当天创建的用户全部漏掉 —— 日期范围查询最经典的 off-by-one。
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"),
                        query.getCreateTimeEnd().atTime(LocalTime.MAX)));
            }

            // 因为 join 了角色，"一个用户有多个角色"会产生重复行，去重
            if (query.getRoleId() != null && criteriaQuery != null) {
                criteriaQuery.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 排序：字段走白名单，避免前端传个任意字段名导致 SQL 报错或注入风险 */
    private Sort buildSort(SysUserQuery query) {
        String field = SysUserQuery.ALLOWED_SORT_FIELDS.contains(query.getSortField())
                ? query.getSortField() : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(query.getSortOrder())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    @Override
    public SysUserVO findById(Long id) {
        return toVO(getUser(id));
    }

    // ============================================================
    // 增删改
    // ============================================================

    @Override
    @Transactional
    public SysUserVO create(SysUserSaveRequest request) {
        if (sysUserRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("用户名已存在：" + request.getUsername());
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("新增用户时密码不能为空");
        }
        String weak = PasswordPolicy.validate(request.getPassword());
        if (weak != null) {
            throw new IllegalArgumentException(weak);
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_NORMAL);
        user.setRoles(resolveRoles(request.getRoleIds()));

        return toVO(sysUserRepository.save(user));
    }

    @Override
    @Transactional
    public SysUserVO update(Long id, SysUserSaveRequest request) {
        SysUser existing = getUser(id);

        if (!existing.getUsername().equals(request.getUsername())
                && sysUserRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("用户名已存在：" + request.getUsername());
        }

        existing.setUsername(request.getUsername());
        existing.setNickname(request.getNickname());
        existing.setEmail(request.getEmail());
        existing.setPhone(request.getPhone());
        if (StringUtils.hasText(request.getStatus())) {
            existing.setStatus(request.getStatus());
        }

        // 密码留空表示不修改。这里不能写成 existing.setPassword(request.getPassword())，
        // 那会把 null 或空串写进去，用户就再也登录不了了
        if (StringUtils.hasText(request.getPassword())) {
            String weak = PasswordPolicy.validate(request.getPassword());
            if (weak != null) {
                throw new IllegalArgumentException(weak);
            }
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // roleIds 为 null 表示这次请求不涉及角色，保持原样；传空数组才是"清空角色"
        if (request.getRoleIds() != null) {
            existing.setRoles(resolveRoles(request.getRoleIds()));
            permissionService.evictAfterCommit();
        }

        return toVO(sysUserRepository.save(existing));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = getUser(id);

        String reason = checkDeletable(user);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }

        sysUserRepository.deleteById(id);
        permissionService.evictAfterCommit();
    }

    // ============================================================
    // 批量操作
    //
    // 三条规则完全一致：**不做"全成功或全失败"**。
    // 选中的 10 个里有 1 个是受保护的，就跳过那 1 个、其余的照做，
    // 并返回"成功了几个 + 哪几个被跳过、为什么"。
    // 全部回滚不合理（用户想处理的其余 9 个明明可以），静默跳过也不好（会以为处理干净了）。
    // ============================================================

    @Override
    @Transactional
    public BatchResultVO batchDelete(List<Long> ids) {
        List<Long> targetIds = normalizeIds(ids, "请先选择要删除的用户");
        List<SysUser> users = sysUserRepository.findByIdIn(targetIds);

        List<BatchResultVO.SkippedItem> skipped = new ArrayList<>();
        int success = 0;

        for (SysUser user : users) {
            String reason = checkDeletable(user);
            if (reason != null) {
                skipped.add(new BatchResultVO.SkippedItem(user.getId(), user.getUsername(), reason));
                continue;
            }
            sysUserRepository.delete(user);
            success++;
        }

        appendMissingIds(targetIds, users, skipped);

        if (success > 0) {
            permissionService.evictAfterCommit();
        }
        return new BatchResultVO(success, skipped);
    }

    @Override
    @Transactional
    public BatchResultVO batchUpdateStatus(List<Long> ids, String status) {
        if (!STATUS_NORMAL.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new IllegalArgumentException("状态只能是「正常」或「停用」");
        }
        List<Long> targetIds = normalizeIds(ids, "请先选择要操作的用户");
        List<SysUser> users = sysUserRepository.findByIdIn(targetIds);

        List<BatchResultVO.SkippedItem> skipped = new ArrayList<>();
        Long currentUserId = UserContext.getUserId();
        int success = 0;

        for (SysUser user : users) {
            String reason = checkStatusChangeable(user, status, currentUserId);
            if (reason != null) {
                skipped.add(new BatchResultVO.SkippedItem(user.getId(), user.getUsername(), reason));
                continue;
            }
            if (status.equals(user.getStatus())) {
                skipped.add(new BatchResultVO.SkippedItem(user.getId(), user.getUsername(),
                        "状态已经是「" + status + "」"));
                continue;
            }
            user.setStatus(status);
            sysUserRepository.save(user);
            success++;
        }

        appendMissingIds(targetIds, users, skipped);
        // 停用账号会让它的权限不再被统计（perms 查询里过滤了用户，这里主要是保险）
        permissionService.evictAfterCommit();
        return new BatchResultVO(success, skipped);
    }

    @Override
    @Transactional
    public BatchResultVO batchResetPassword(List<Long> ids, String password) {
        String weak = PasswordPolicy.validate(password);
        if (weak != null) {
            throw new IllegalArgumentException(weak);
        }

        List<Long> targetIds = normalizeIds(ids, "请先选择要重置密码的用户");
        List<SysUser> users = sysUserRepository.findByIdIn(targetIds);

        List<BatchResultVO.SkippedItem> skipped = new ArrayList<>();
        int success = 0;

        for (SysUser user : users) {
            // 内置超管不允许批量重置 —— 批量操作一把改掉别人的密码已经很敏感了，
            // 再把管理员账号一起改了，万一密码传播失控后果很严重。
            // 需要改超管密码请用单条的重置密码功能。
            if (BUILTIN_ADMIN.equals(user.getUsername())) {
                skipped.add(new BatchResultVO.SkippedItem(user.getId(), user.getUsername(),
                        "内置管理员账号不允许批量重置密码，请单独操作"));
                continue;
            }
            user.setPassword(passwordEncoder.encode(password));
            sysUserRepository.save(user);
            success++;
        }

        appendMissingIds(targetIds, users, skipped);
        return new BatchResultVO(success, skipped);
    }

    // ============================================================
    // 分配角色 / 重置密码
    // ============================================================

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = getUser(userId);
        user.setRoles(resolveRoles(roleIds));
        sysUserRepository.save(user);
        permissionService.evictAfterCommit();
    }

    @Override
    public List<Long> findRoleIds(Long userId) {
        return getUser(userId).getRoles().stream()
                .map(SysRole::getId)
                .toList();
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        String weak = PasswordPolicy.validate(newPassword);
        if (weak != null) {
            throw new IllegalArgumentException(weak);
        }
        SysUser user = getUser(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserRepository.save(user);
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    private SysUser getUser(Long id) {
        return sysUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在，id = " + id));
    }

    /** 去重 + 判空，并给出可读的报错 */
    private List<Long> normalizeIds(List<Long> ids, String emptyMessage) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return distinct;
    }

    /** 请求里带了、但库里找不到的 id 也要如实告诉用户，不能静默吞掉 */
    private void appendMissingIds(List<Long> requested, List<SysUser> found,
                                  List<BatchResultVO.SkippedItem> skipped) {
        Set<Long> foundIds = found.stream().map(SysUser::getId).collect(Collectors.toSet());
        for (Long id : requested) {
            if (!foundIds.contains(id)) {
                skipped.add(new BatchResultVO.SkippedItem(id, "—", "用户不存在或已被删除"));
            }
        }
    }

    private String checkDeletable(SysUser user) {
        if (BUILTIN_ADMIN.equals(user.getUsername())) {
            return "内置管理员账号不允许删除";
        }
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(user.getId())) {
            return "不能删除当前登录的账号";
        }
        return null;
    }

    private String checkStatusChangeable(SysUser user, String targetStatus, Long currentUserId) {
        if (!STATUS_DISABLED.equals(targetStatus)) {
            return null;   // 启用（改成"正常"）没有额外限制
        }
        // 停用是危险操作：把自己停用会立刻登不进来；把超管停用会让系统失去管理入口
        if (BUILTIN_ADMIN.equals(user.getUsername())) {
            return "内置管理员账号不允许停用";
        }
        if (currentUserId != null && currentUserId.equals(user.getId())) {
            return "不能停用当前登录的账号";
        }
        return null;
    }

    /** 把角色 id 列表解析成角色实体集合，非法 id 直接报错而不是静默忽略 */
    private Set<SysRole> resolveRoles(List<Long> roleIds) {
        Set<SysRole> roles = new HashSet<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return roles;
        }

        // 一次查完，不要循环 findById（N 次查询）
        List<SysRole> found = sysRoleRepository.findAllById(roleIds);
        if (found.size() != roleIds.stream().distinct().count()) {
            throw new IllegalArgumentException("部分角色不存在，请刷新后重试");
        }
        roles.addAll(found);
        return roles;
    }

    private SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLastLoginIp(user.getLastLoginIp());

        // VO 里干脆没有 password 属性，比在实体上靠 @JsonIgnore 更保险
        vo.setRoleIds(user.getRoles().stream().map(SysRole::getId).toList());
        vo.setRoleNames(user.getRoles().stream()
                .sorted(Comparator.comparing(SysRole::getId))
                .map(SysRole::getRoleName).toList());
        return vo;
    }
}
