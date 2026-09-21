package com.yan.backend.excel;

import com.yan.backend.dto.AuditFieldChange;
import com.yan.backend.dto.AuditLogVO;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 资产审计日志导出。
 *
 * <p>列顺序按"读一条审计记录"的思路排：什么时候 → 谁 → 对什么 → 做了什么 →
 * 具体改了什么 → 为什么。所以时间放第一列而不是 ID：
 * 审计是一份按时间追查的流水，ID 对读的人没有意义。
 */
@Component
public class AuditLogExcelExporter {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] COLUMNS = {
            "时间", "操作人", "业务类型", "对象名称", "对象编号",
            "变更动作", "变更字段数", "变更明细", "备注"
    };

    private static final int[] WIDTHS = {
            20, 12, 10, 22, 18,
            12, 12, 52, 26
    };

    private static final int WIDTH_UNIT = 256;

    /** 变更明细列的下标，它用自动换行样式 */
    private static final int COL_CHANGES = 7;

    public void write(List<AuditLogVO> logs, OutputStream outputStream) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("资产审计日志");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle bodyStyle = buildBodyStyle(workbook);
            CellStyle wrapStyle = buildWrapStyle(workbook);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (AuditLogVO log : logs) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < COLUMNS.length; i++) {
                    row.createCell(i).setCellStyle(i == COL_CHANGES ? wrapStyle : bodyStyle);
                }

                row.getCell(0).setCellValue(formatTime(log.getAuditTime()));
                // 操作人导出昵称（人认的是名字），没有昵称就退回用户名 ——
                // 至少这一列不能是空的，否则查不出是谁做的
                row.getCell(1).setCellValue(
                        hasText(log.getOperatorName()) ? log.getOperatorName() : emptyIfNull(log.getOperator()));
                row.getCell(2).setCellValue(emptyIfNull(log.getBizTypeLabel()));
                row.getCell(3).setCellValue(emptyIfNull(log.getBizName()));
                row.getCell(4).setCellValue(emptyIfNull(log.getBizCode()));
                row.getCell(5).setCellValue(emptyIfNull(log.getActionLabel()));
                row.getCell(6).setCellValue(log.getChangeCount() == null ? 0 : log.getChangeCount());
                row.getCell(COL_CHANGES).setCellValue(formatChanges(log.getChanges()));
                row.getCell(8).setCellValue(emptyIfNull(log.getRemark()));
            }

            for (int i = 0; i < WIDTHS.length; i++) {
                sheet.setColumnWidth(i, WIDTHS[i] * WIDTH_UNIT);
            }

            workbook.write(outputStream);
        }
    }

    /**
     * 把字段变更拼成多行文本。
     *
     * <p>一行一个字段（「状态：在线 → 维修中」），配合自动换行样式，
     * 在 Excel 里读起来是一条一条的。拼成一句话的话，
     * 改了十几个字段时根本分不清哪段是哪段。
     *
     * <p>删除这类没有字段变化的动作用「—」占位，而不是留空 ——
     * 空白会让人以为是导出漏了数据。
     */
    private String formatChanges(List<AuditFieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (AuditFieldChange change : changes) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(change.field()).append('：')
                    .append(change.before()).append(" → ").append(change.after());
        }
        return sb.toString();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
