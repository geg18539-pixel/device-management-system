package com.yan.backend.service.impl;

import com.yan.backend.entity.SysDictItem;
import com.yan.backend.entity.SysDictType;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysDictItemRepository;
import com.yan.backend.repository.SysDictTypeRepository;
import com.yan.backend.service.SysDictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SysDictServiceImpl implements SysDictService {

    private static final Logger log = LoggerFactory.getLogger(SysDictServiceImpl.class);

    /** 缓存有效期：5 分钟。和系统参数、权限缓存保持一致 */
    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    private final SysDictTypeRepository typeRepository;
    private final SysDictItemRepository itemRepository;

    /** dictType -> 启用中的字典项。读多写少，整块缓存在内存里 */
    private volatile Map<String, List<SysDictItem>> cache;
    private volatile long expiresAt;

    public SysDictServiceImpl(SysDictTypeRepository typeRepository,
                              SysDictItemRepository itemRepository) {
        this.typeRepository = typeRepository;
        this.itemRepository = itemRepository;
    }

    // ============================================================
    // 字典类型
    // ============================================================

    @Override
    public List<SysDictType> listTypes() {
        return typeRepository.findAllByOrderByIdAsc();
    }

    @Override
    @Transactional
    public SysDictType createType(SysDictType type) {
        type.setId(null);
        if (typeRepository.existsByDictType(type.getDictType())) {
            throw new IllegalStateException("字典类型编码已存在：" + type.getDictType());
        }
        if (!StringUtils.hasText(type.getStatus())) {
            type.setStatus(SysDictType.STATUS_NORMAL);
        }
        return typeRepository.save(type);
    }

    @Override
    @Transactional
    public SysDictType updateType(Long id, SysDictType type) {
        SysDictType existing = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("字典类型不存在，id = " + id));

        existing.setDictName(type.getDictName());
        existing.setRemark(type.getRemark());
        if (StringUtils.hasText(type.getStatus())) {
            existing.setStatus(type.getStatus());
        }

        // ★ 刻意**不接收** dictType（编码）。
        // 它是代码里引用这个字典的标识（如 fault_type），改了之后
        // 所有读它的地方都会查不到 —— 而且不报错，只是下拉变成空的。
        // 想换编码请新建一个字典，把旧的数据迁移过去。

        SysDictType saved = typeRepository.save(existing);
        evictAfterCommit();
        return saved;
    }

    @Override
    @Transactional
    public void deleteType(Long id) {
        SysDictType type = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("字典类型不存在，id = " + id));

        long count = itemRepository.countByDictType(type.getDictType());
        if (count > 0) {
            // 不级联删：一句话就删掉一整组字典项太容易出事，
            // 而且业务表里可能还存着这些值（删了之后历史数据就显示不出来了）
            throw new IllegalStateException("「" + type.getDictName() + "」下还有 " + count
                    + " 个字典项，请先删除这些字典项再删除字典");
        }
        typeRepository.delete(type);
        evictAfterCommit();
    }

    // ============================================================
    // 字典项
    // ============================================================

    @Override
    public List<SysDictItem> listItems(String dictType, boolean onlyEnabled) {
        if (!StringUtils.hasText(dictType)) {
            throw new IllegalArgumentException("字典类型不能为空");
        }
        return onlyEnabled
                ? itemRepository.findByDictTypeAndStatusOrderBySortOrderAscIdAsc(
                        dictType, SysDictItem.STATUS_NORMAL)
                : itemRepository.findByDictTypeOrderBySortOrderAscIdAsc(dictType);
    }

    @Override
    @Transactional
    public SysDictItem createItem(SysDictItem item) {
        item.setId(null);

        // 字典类型必须真实存在。不校验的话会造出"挂在不存在的字典下"的孤儿项，
        // 后台页面上永远看不到它，但它确实占着数据库
        if (!typeRepository.existsByDictType(item.getDictType())) {
            throw new IllegalArgumentException("字典类型不存在：" + item.getDictType());
        }
        if (itemRepository.existsByDictTypeAndItemValue(item.getDictType(), item.getItemValue())) {
            throw new IllegalStateException("该字典下已存在键值：" + item.getItemValue());
        }
        if (!StringUtils.hasText(item.getStatus())) {
            item.setStatus(SysDictItem.STATUS_NORMAL);
        }
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        SysDictItem saved = itemRepository.save(item);
        evictAfterCommit();
        return saved;
    }

    @Override
    @Transactional
    public SysDictItem updateItem(Long id, SysDictItem item) {
        SysDictItem existing = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("字典项不存在，id = " + id));

        // 键值改了要查重（和"改成自己原来的值"区分开，否则自己和自己撞）
        if (!existing.getItemValue().equals(item.getItemValue())
                && itemRepository.existsByDictTypeAndItemValue(
                        existing.getDictType(), item.getItemValue())) {
            throw new IllegalStateException("该字典下已存在键值：" + item.getItemValue());
        }

        existing.setItemLabel(item.getItemLabel());
        existing.setItemValue(item.getItemValue());
        existing.setRemark(item.getRemark());
        if (item.getSortOrder() != null) {
            existing.setSortOrder(item.getSortOrder());
        }
        if (StringUtils.hasText(item.getStatus())) {
            existing.setStatus(item.getStatus());
        }

        // 同样不接收 dictType：字典项不能从一个字典挪到另一个字典，
        // 那等于把业务表里已存的值换成了另一套语义

        SysDictItem saved = itemRepository.save(existing);
        evictAfterCommit();
        return saved;
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        SysDictItem item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("字典项不存在，id = " + id));
        itemRepository.delete(item);
        evictAfterCommit();
    }

    // ============================================================
    // 给业务代码用的读取入口
    // ============================================================

    @Override
    public List<SysDictItem> enabledItems(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return List.of();
        }
        List<SysDictItem> items = snapshot().get(dictType);
        return items == null ? List.of() : items;
    }

    @Override
    public boolean isValidValue(String dictType, String value) {
        if (!StringUtils.hasText(value)) {
            return true;   // 空值由调用方自己决定要不要允许
        }
        List<SysDictItem> items = enabledItems(dictType);
        // ★ 字典压根没配（或这次没读到）时**放行**，不拦。
        // 拦的话，一旦字典表为空或缓存加载失败，报修这类功能会整体不可用 ——
        // 一个"可选的分类字段"不该有这种杀伤力
        if (items.isEmpty()) {
            return true;
        }
        return items.stream().anyMatch(i -> value.equals(i.getItemValue()));
    }

    @Override
    public void evictCache() {
        cache = null;
        expiresAt = 0;
    }

    private Map<String, List<SysDictItem>> snapshot() {
        Map<String, List<SysDictItem>> current = cache;
        if (current != null && System.currentTimeMillis() < expiresAt) {
            return current;
        }
        synchronized (this) {
            if (cache != null && System.currentTimeMillis() < expiresAt) {
                return cache;
            }
            Map<String, List<SysDictItem>> loaded = new HashMap<>();
            try {
                for (SysDictItem item : itemRepository.findAll()) {
                    if (SysDictItem.STATUS_NORMAL.equals(item.getStatus())) {
                        loaded.computeIfAbsent(item.getDictType(),
                                k -> new java.util.ArrayList<>()).add(item);
                    }
                }
                // 排序在内存里做一次，省得为每个字典各查一次库
                loaded.values().forEach(list -> list.sort(
                        java.util.Comparator.comparing(
                                (SysDictItem i) -> i.getSortOrder() == null ? 0 : i.getSortOrder())
                                .thenComparing(SysDictItem::getId)));
            } catch (Exception e) {
                // 读不到就当作"没有字典"：所有校验放行、下拉为空。
                // 不抛异常 —— 这个方法在报修这类核心路径上被调用
                log.warn("读取字典失败，本次按空字典处理：{}", e.getMessage());
            }
            cache = loaded;
            expiresAt = System.currentTimeMillis() + TTL_MILLIS;
            return loaded;
        }
    }

    /** 提交后再清缓存，避免"改完立刻读还是旧值"（和权限缓存是同一个坑） */
    private void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictCache();
                }
            });
        } else {
            evictCache();
        }
    }
}
