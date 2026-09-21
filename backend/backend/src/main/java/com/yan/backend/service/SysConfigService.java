package com.yan.backend.service;

import com.yan.backend.entity.SysConfig;

import java.util.List;

/**
 * 系统参数的读取与管理。
 *
 * <p><b>读接口（getXxx）的语义是"库里有就用库里的，没有就用你传的默认值"。</b>
 * 调用方传的默认值一般就是 application.yml 里的值，所以：
 * 空库能跑、管理员误删某一项也能跑，只是用回默认值。
 */
public interface SysConfigService {

    /** 取字符串参数 */
    String getString(String key, String defaultValue);

    /**
     * 取整数参数。
     *
     * <p>值解析不了（被手工改成了 "abc"）时**回退默认值并打警告**，
     * 不抛异常 —— 一个配置项写错不该让整个功能不可用。
     */
    int getInt(String key, int defaultValue);

    long getLong(String key, long defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    /** 全部参数，按分组/排序排好 */
    List<SysConfig> listAll();

    /** 新增参数 */
    SysConfig create(SysConfig config);

    /** 改某一项的值。内置项的 key 不允许改，只能改值 */
    SysConfig updateValue(String key, String value);

    /** 删除参数。内置项不允许删，理由见 SysConfig.builtIn */
    void delete(Long id);

    /** 清空进进程内缓存。种子数据写完参数后要调一次 */
    void evictCache();
}
