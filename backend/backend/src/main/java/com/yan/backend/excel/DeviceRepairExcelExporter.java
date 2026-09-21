package com.yan.backend.excel;

import com.yan.backend.common.DictTypes;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.SysDictItem;
import com.yan.backend.service.SysDictService;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 维修工单列表导出。
 *
 * <p>列的顺序刻意按"读一份维修报告"的思路排：先能认出是哪台设备、什么毛病、
 * 处理到哪一步了，再往后才是人和时间，最后是结论和费用。
 */
@Component
public class DeviceRepairExcelExporter {

    private final SysDictService sysDictService;

    public DeviceRepairExcelExporter(SysDictService sysDictService) {
        this.sysDictService = sysDictService;
    }

    /**
     * 把故障类型的**编码**翻成展示文案。
     *
     * <p>导出的报表是给人看的，看到 MECH 没人知道是什么。
     * 字典里查不到（比如这一项被删了）时退回原值 —— 至少不丢信息，
     * 总比导出个空白让人以为这单没填类型要好。
     */
    private String faultTypeLabel(String faultType) {
        if (faultType == null || faultType.isBlank()) {
            return "";
        }
        return sysDictService.enabledItems(DictTypes.FAULT_TYPE).stream()
                .filter(item -> faultType.equals(item.getItemValue()))
                .map(SysDictItem::getItemLabel)
                .findFirst()
                .orElse(faultType);
    }

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] COLUMNS = {
            "工单号", "设备名称", "故障描述", "故障类型", "工单状态", "报修人", "维修人",
            "报修时间", "受理时间", "完工时间", "关闭时间",
            "维修结果", "维修费用", "备注"
    };

    /** 工单号这种窄列 12 字符宽就够，描述类给宽一点 */
    private static final int[] WIDTHS = {
            10, 20, 34, 14, 10, 12, 12,
            20, 20, 20, 20,
            40, 12, 24
    };

    private static final int WIDTH_UNIT = 256;

    public void write(List<DeviceRepair> repairs, OutputStream outputStream) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("维修工单");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle bodyStyle = buildBodyStyle(workbook);
            // 长文本列单独用"自动换行"样式，否则维修结果会挤成一条看不见头尾的横线
            CellStyle wrapStyle = buildWrapStyle(workbook);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (DeviceRepair repair : repairs) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < COLUMNS.length; i++) {
                    // 故障描述和维修结果用自动换行样式
                    row.createCell(i).setCellStyle((i == 2 || i == 11) ? wrapStyle : bodyStyle);
                }

                row.getCell(0).setCellValue(repair.getId() == null ? 0 : repair.getId());
                row.getCell(1).setCellValue(nullToEmpty(repair.getDeviceName()));
                row.getCell(2).setCellValue(nullToEmpty(repair.getFaultDesc()));
                // 故障类型导出的是**展示文案**（字典的 label），不是存进库里的编码 ——
                // 导出的报表是给人看的，看到 MECH 没人知道是什么。
                // 查不到对应项时退回原值，至少不丢信息
                row.getCell(3).setCellValue(faultTypeLabel(repair.getFaultType()));
                // ★ 旧值「待维修」统一显示成「待受理」。
                // 导出是给人看的，不能因为库里存的是历史值就让报告里出现两套状态说法
                row.getCell(4).setCellValue(
                        nullToEmpty(DeviceRepair.normalizeStatus(repair.getRepairStatus())));
                row.getCell(5).setCellValue(nullToEmpty(repair.getReporter()));
                row.getCell(6).setCellValue(nullToEmpty(repair.getRepairer()));
                row.getCell(7).setCellValue(formatTime(repair.getReportTime()));
                row.getCell(8).setCellValue(formatTime(repair.getAcceptTime()));
                row.getCell(9).setCellValue(formatTime(repair.getFinishTime()));
                row.getCell(10).setCellValue(formatTime(repair.getCloseTime()));
                row.getCell(11).setCellValue(nullToEmpty(repair.getRepairResult()));

                // 费用写数值，Excel 里能直接求和（一份维修报告最常被问的就是"一共花了多少"）
                BigDecimal cost = repair.getCost();
                if (cost == null) {
                    row.getCell(12).setCellValue("");
                } else {
                    row.getCell(12).setCellValue(cost.doubleValue());
                }
                row.getCell(13).setCellValue(nullToEmpty(repair.getRemark()));
            }

            for (int i = 0; i < WIDTHS.length; i++) {
                sheet.setColumnWidth(i, WIDTHS[i] * WIDTH_UNIT);
            }

            workbook.write(outputStream);
        }
    }

    // ---------------- 样式 ----------------

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

    private CellStyle buildWrapStyle(XSSFWorkbook workbook) {
        CellStyle style = buildBodyStyle(workbook);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_TIME);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
