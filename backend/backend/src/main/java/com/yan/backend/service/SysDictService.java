package com.yan.backend.service;

import com.yan.backend.entity.SysDictItem;
import com.yan.backend.entity.SysDictType;

import java.util.List;

public interface SysDictService {

    // ---------------- 字典类型 ----------------

    List<SysDictType> listTypes();

    SysDictType createType(SysDictType type);

    /** 改字典。**类型编码不允许改** —— 改了等于断掉所有引用它的地方 */
    SysDictType updateType(Long id, SysDictType type);

    /** 删字典。下面还有字典项时会拒绝（避免误删一整组数据） */
    void deleteType(Long id);

    // ---------------- 字典项 ----------------

    /**
     * 某个字典下的项。
     *
     * @param onlyEnabled true 只看启用中的（给下拉用），false 看全部（后台管理页用）
     */
    List<SysDictItem> listItems(String dictType, boolean onlyEnabled);

    SysDictItem createItem(SysDictItem item);

    SysDictItem updateItem(Long id, SysDictItem item);

    void deleteItem(Long id);

    // ---------------- 给业务代码用的读取入口 ----------------

    /**
     * 取某个字典启用中的项，**带缓存**。
     *
     * <p>业务代码（比如校验故障类型是否合法）走这个入口，
     * 而不是每次查库 —— 字典读多写少，和系统参数是同一类东西。
     */
    List<SysDictItem> enabledItems(String dictType);

    /** 校验某个值是不是该字典下启用中的合法值。字典本身不存在时返回 true（宽松放行） */
    boolean isValidValue(String dictType, String value);

    /** 清空字典缓存。字典改动后调 */
    void evictCache();
}
