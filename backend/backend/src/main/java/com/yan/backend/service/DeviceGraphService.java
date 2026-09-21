package com.yan.backend.service;

import com.yan.backend.dto.DeviceGraphVO;

/**
 * 设备关系图谱。
 *
 * <p>把一台设备在系统里的直接关系（部门、分类、维修工单、保养计划、
 * 工单领用过的配件）拼成一张可画的图。
 *
 * <p><b>这不是 AI 功能。</b> 虽然它在计划里和 AI 那一批一起提的，
 * 但实现上完全是确定性的关系查询 —— 没有模型参与、结果可复现、
 * 同一份数据每次画出来都一样。所以它挂在「资产」菜单下，
 * 而不是「AI 能力」组里：放进那一组会让人以为"这图是模型画的"。
 */
public interface DeviceGraphService {

    /**
     * 取某台设备的直接关系图。
     *
     * @throws com.yan.backend.exception.ResourceNotFoundException 设备不存在时
     */
    DeviceGraphVO graph(Long deviceId);
}
