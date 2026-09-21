package com.yan.backend.common;

import com.yan.backend.entity.AssetAuditLog;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计动作的中文文案。
 *
 * <p><b>为什么标签放后端而不是前端？</b> 因为导出 Excel 也要用，
 * 而报表是给人看的，里面出现「STOCK_OUT」没人知道是什么。
 * 单纯为了界面显示的话放前端更合适（项目里 {@code MSG_TYPE_META} 就是这么做的），
 * 但这里两处都要用 —— 各写一份必然会出现"页面上叫『领料出库』、
 * 导出里写『出库』"这种不一致。所以标签统一由后端提供，
 * 前端只负责决定用什么颜色（那才是纯 UI 的事）。
 *
 * <p>存进库里的是动作**代码**（{@code STOCK_OUT}）而不是文案：
 * 万一以后要把「领料出库」改成「出库」，历史记录不该跟着变形 ——
 * 审计资料的措辞应该固定在当时。
 */
public final class AuditActions {

    private AuditActions() {
    }

    private static final Map<String, String> LABELS = buildLabels();

    private static Map<String, String> buildLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(AssetAuditLog.ACTION_CREATE, "新建");
        m.put(AssetAuditLog.ACTION_UPDATE, "编辑");
        m.put(AssetAuditLog.ACTION_DELETE, "删除");
        m.put(AssetAuditLog.ACTION_BORROW, "借用");
        m.put(AssetAuditLog.ACTION_RETURN, "归还");
        m.put(AssetAuditLog.ACTION_REPAIR, "报修");
        m.put(AssetAuditLog.ACTION_TRANSFER, "调拨");
        m.put(AssetAuditLog.ACTION_SCRAP, "报废");
        m.put(AssetAuditLog.ACTION_RESTORE, "恢复");
        m.put(AssetAuditLog.ACTION_STOCK_IN, "入库");
        m.put(AssetAuditLog.ACTION_STOCK_OUT, "出库");
        return m;
    }

    /** 查不到就原样返回代码。宁可界面上露出一个英文代码，也不要显示成空白 */
    public static String labelOf(String action) {
        if (action == null) {
            return "";
        }
        return LABELS.getOrDefault(action, action);
    }

    /**
     * 全部动作的代码 → 文案。
     *
     * <p>给前端做筛选下拉用。做成接口返回、而不是让前端再抄一份中文，
     * 是因为文案一旦两边各存一份就必然会出现
     * 「下拉里写『领料出库』、表格里写『出库』」这种对不上的情况。
     * 前端只负责决定用什么颜色（那才是纯 UI 的事）。
     */
    public static Map<String, String> all() {
        return LABELS;
    }

    /** 业务类型的中文文案 */
    public static String bizTypeLabelOf(String bizType) {
        if (AssetAuditLog.BIZ_PART.equals(bizType)) {
            return "配件";
        }
        if (AssetAuditLog.BIZ_DEVICE.equals(bizType)) {
            return "设备";
        }
        return bizType == null ? "" : bizType;
    }
}
