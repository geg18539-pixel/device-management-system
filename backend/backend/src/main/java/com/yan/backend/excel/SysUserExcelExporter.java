package com.yan.backend.excel;

import com.yan.backend.dto.SysUserVO;
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
 * 把用户列表写成 .xlsx。
 *
 * <p>用 XSSFWorkbook（一次性把整个工作簿建在内存里）而不是 SXSSFWorkbook（流式写临时文件）：
 * 导出量被上限卡住了，内存完全放得下，而 SXSSF 需要手动 dispose 清理临时文件，
 * 少写一句就漏临时文件。简单场景别给自己找麻烦。
 */
@Component
public class SysUserExcelExporter {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] COLUMNS = {
            "ID", "用户名", "昵称", "邮箱", "手机号",
            "角色", "状态", "最后登录时间", "最后登录IP", "创建时间"
    };

    /** 各列宽度（单位是 1/256 个字符宽） */
    private static final int COLUMN_WIDTH = 20 * 256;

    public void write(List<SysUserVO> users, OutputStream outputStream) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("用户列表");
            sheet.createFreezePane(0, 1);   // 冻结表头，滚动时表头不动

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle bodyStyle = buildBodyStyle(workbook);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (SysUserVO user : users) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < COLUMNS.length; i++) {
                    row.createCell(i).setCellStyle(bodyStyle);
                }

                // ID 写数字，Excel 里才能当数值排序
                row.getCell(0).setCellValue(user.getId() == null ? 0 : user.getId());
                row.getCell(1).setCellValue(nullToEmpty(user.getUsername()));
                row.getCell(2).setCellValue(nullToEmpty(user.getNickname()));
                row.getCell(3).setCellValue(nullToEmpty(user.getEmail()));
                row.getCell(4).setCellValue(nullToEmpty(user.getPhone()));
                row.getCell(5).setCellValue(user.getRoleNames() == null
                        ? "" : String.join("、", user.getRoleNames()));
                row.getCell(6).setCellValue(nullToEmpty(user.getStatus()));

                // 时间统一按字符串写。
                // 用真正的日期单元格虽然能排序，但要处理时区、显示格式、以及
                // Excel 自己那套 1900 年闰年 bug 的兼容，收益不值这个复杂度。
                row.getCell(7).setCellValue(formatTime(user.getLastLoginTime()));
                row.getCell(8).setCellValue(nullToEmpty(user.getLastLoginIp()));
                row.getCell(9).setCellValue(formatTime(user.getCreateTime()));
            }

            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.setColumnWidth(i, COLUMN_WIDTH);
            }

            workbook.write(outputStream);
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

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_TIME);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
