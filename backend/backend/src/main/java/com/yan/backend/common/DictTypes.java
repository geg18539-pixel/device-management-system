package com.yan.backend.common;

/**
 * 字典类型编码。
 *
 * <p>和 {@link ConfigKeys} 是同一个理由：字典类型编码会同时出现在
 * 代码里的读取点、种子数据的默认项、以及页面的下拉请求里。
 * 任何一处拼错都**不会报错**，只会表现为"这个下拉是空的"。
 *
 * <p>命名用下划线小写，和 {@code sys_dict_type.dict_type} 的校验规则一致。
 */
public final class DictTypes {

    private DictTypes() {
    }

    /**
     * 故障类型。
     *
     * <p>用在：报修时选择、工单列表筛选、导出列。
     */
    public static final String FAULT_TYPE = "fault_type";
}
