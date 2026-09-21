package com.yan.backend.excel;

import com.yan.backend.dto.DeviceLedgerVO;
import com.yan.backend.dto.LedgerItemVO;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.SysDept;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.SysDeptRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备列表 / 设备台账的 Excel 导出。
 *
 * <p>用 XSSFWorkbook（整个工作簿建在内存里）而不是流式的 SXSSFWorkbook：
 * 导出条数被上限卡住了，内存放得下，而 SXSSF 要手动 dispose 清临时文件，
 * 少写一句就漏文件。简单场景别给自己找麻烦。
 *
 * <p>这个类**直接注入了两个仓库**去查部门名和分类名。看着有点越界，
 * 但导出要的就是"把 id 翻译成人能看懂的字段"，让每个调用方自己去拼映射
 * 反而会重复三遍。这里只读参考数据，不碰设备本身。
 */
@Component
public class DeviceExcelExporter {

    private final SysDeptRepository sysDeptRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;

    public DeviceExcelExporter(SysDeptRepository sysDeptRepository,
                               DeviceCategoryRepository deviceCategoryRepository) {
        this.sysDeptRepository = sysDeptRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
    }

    private static final int COLUMN_WIDTH = 18 * 256;

    private static final String[] DEVICE_COLUMNS = {
            "资产编号", "设备名称", "分类", "所属部门", "型号", "生产厂商", "序列号",
            "连通状态", "资产状态", "采购日期", "保修到期", "存放位置", "借用人", "登记时间"
    };

    private static final String[] LEDGER_COLUMNS = {
            "分组", "设备合计", "正常", "维修", "报废", "停用"
    };

    // ============================================================
    // 设备列表导出
    // ============================================================

    public void writeDevices(List<Device> devices, OutputStream outputStream) throws IOException {
        Map<Long, String> deptNames = deptNameMap();
        Map<Long, String> categoryNames = categoryNameMap();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("设备列表");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle bodyStyle = buildBodyStyle(workbook);
            writeHeader(sheet, DEVICE_COLUMNS, headerStyle);

            int rowIndex = 1;
            for (Device device : devices) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < DEVICE_COLUMNS.length; i++) {
                    row.createCell(i).setCellStyle(bodyStyle);
                }
                row.getCell(0).setCellValue(nullToEmpty(device.getAssetCode()));
                row.getCell(1).setCellValue(nullToEmpty(device.getDeviceName()));
                row.getCell(2).setCellValue(resolve(categoryNames, device.getCategoryId(), "未分类"));
                row.getCell(3).setCellValue(resolve(deptNames, device.getDeptId(), "未分配"));
                row.getCell(4).setCellValue(nullToEmpty(device.getModel()));
                row.getCell(5).setCellValue(nullToEmpty(device.getManufacturer()));
                row.getCell(6).setCellValue(nullToEmpty(device.getSerialNumber()));
                row.getCell(7).setCellValue(nullToEmpty(device.getStatus()));
                // 老数据的 lifecycleStatus 可能是 null，按"正常"显示
                row.getCell(8).setCellValue(
                        device.getLifecycleStatus() == null ? Device.LIFECYCLE_NORMAL
                                : device.getLifecycleStatus());
                row.getCell(9).setCellValue(formatDate(device.getPurchaseDate()));
                row.getCell(10).setCellValue(formatDate(device.getWarrantyDate()));
                row.getCell(11).setCellValue(nullToEmpty(device.getLocation()));
                row.getCell(12).setCellValue(nullToEmpty(device.getBorrower()));
                row.getCell(13).setCellValue(formatTime(device.getCreateTime()));
            }

            applyWidths(sheet, DEVICE_COLUMNS.length);
            workbook.write(outputStream);
        }
    }

    // ============================================================
    // 设备台账导出（三个 sheet）
    // ============================================================

    /**
     * 台账导出：明细 + 按部门汇总 + 按分类汇总，放在同一个工作簿里。
     *
     * <p>为什么合成一个文件而不是三个：给财务/运维的是一个"台账"，
     * 拆成三个文件在邮件里很快就会散掉。而且汇总和明细必须能对着看，
     * 同一个工作簿里切换 sheet 最方便。
     */
    public void writeLedger(List<Device> devices, DeviceLedgerVO ledger,
                            OutputStream outputStream) throws IOException {
        Map<Long, String> deptNames = deptNameMap();
        Map<Long, String> categoryNames = categoryNameMap();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle bodyStyle = buildBodyStyle(workbook);

            // ---------- sheet 1：台账明细 ----------
            Sheet detail = workbook.createSheet("设备台账明细");
            detail.createFreezePane(0, 1);
            writeHeader(detail, DEVICE_COLUMNS, headerStyle);

            int rowIndex = 1;
            for (Device device : devices) {
                Row row = detail.createRow(rowIndex++);
                for (int i = 0; i < DEVICE_COLUMNS.length; i++) {
                    row.createCell(i).setCellStyle(bodyStyle);
                }
                row.getCell(0).setCellValue(nullToEmpty(device.getAssetCode()));
                row.getCell(1).setCellValue(nullToEmpty(device.getDeviceName()));
                row.getCell(2).setCellValue(resolve(categoryNames, device.getCategoryId(), "未分类"));
                row.getCell(3).setCellValue(resolve(deptNames, device.getDeptId(), "未分配"));
                row.getCell(4).setCellValue(nullToEmpty(device.getModel()));
                row.getCell(5).setCellValue(nullToEmpty(device.getManufacturer()));
                row.getCell(6).setCellValue(nullToEmpty(device.getSerialNumber()));
                row.getCell(7).setCellValue(nullToEmpty(device.getStatus()));
                row.getCell(8).setCellValue(
                        device.getLifecycleStatus() == null ? Device.LIFECYCLE_NORMAL
                                : device.getLifecycleStatus());
                row.getCell(9).setCellValue(formatDate(device.getPurchaseDate()));
                row.getCell(10).setCellValue(formatDate(device.getWarrantyDate()));
                row.getCell(11).setCellValue(nullToEmpty(device.getLocation()));
                row.getCell(12).setCellValue(nullToEmpty(device.getBorrower()));
                row.getCell(13).setCellValue(formatTime(device.getCreateTime()));
            }
            applyWidths(detail, DEVICE_COLUMNS.length);

            // ---------- sheet 2 / 3：两个维度的汇总 ----------
            writeLedgerSheet(workbook, "按部门统计", ledger.getDeptItems(), headerStyle, bodyStyle);
            writeLedgerSheet(workbook, "按分类统计", ledger.getCategoryItems(), headerStyle, bodyStyle);

            workbook.write(outputStream);
        }
    }

    private void writeLedgerSheet(XSSFWorkbook workbook, String sheetName,
                                  List<LedgerItemVO> items,
                                  CellStyle headerStyle, CellStyle bodyStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.createFreezePane(0, 1);
        writeHeader(sheet, LEDGER_COLUMNS, headerStyle);

        int rowIndex = 1;
        for (LedgerItemVO item : items) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < LEDGER_COLUMNS.length; i++) {
                row.createCell(i).setCellStyle(bodyStyle);
            }
            row.getCell(0).setCellValue(nullToEmpty(item.getName()));
            // 数字写成数值而不是文本，财务在 Excel 里能直接求和
            row.getCell(1).setCellValue(item.getTotal());
            row.getCell(2).setCellValue(item.getNormal());
            row.getCell(3).setCellValue(item.getRepairing());
            row.getCell(4).setCellValue(item.getScrapped());
            row.getCell(5).setCellValue(item.getDisabled());
        }
        applyWidths(sheet, LEDGER_COLUMNS.length);
    }

    // ============================================================
    // 辅助
    // ============================================================

    private Map<Long, String> deptNameMap() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (SysDept dept : sysDeptRepository.findAllByOrderBySortOrderAscIdAsc()) {
            map.put(dept.getId(), dept.getDeptName());
        }
        return map;
    }

    private Map<Long, String> categoryNameMap() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (DeviceCategory category : deviceCategoryRepository.findAll()) {
            map.put(category.getId(), category.getCategoryName());
        }
        return map;
    }

    private String resolve(Map<Long, String> names, Long id, String nullLabel) {
        if (id == null) {
            return nullLabel;
        }
        return names.getOrDefault(id, "已删除");
    }

    private void writeHeader(Sheet sheet, String[] columns, CellStyle style) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(style);
        }
    }

    private void applyWidths(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.setColumnWidth(i, COLUMN_WIDTH);
        }
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private CellStyle buildBodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(style);
        return style;
    }

    private void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private String formatTime(java.time.LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
