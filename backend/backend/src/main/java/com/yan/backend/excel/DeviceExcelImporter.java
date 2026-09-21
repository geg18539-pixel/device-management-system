package com.yan.backend.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备批量导入：生成模板 + 解析上传文件。
 *
 * <p>格式定义和解析逻辑放在同一个类里，是为了**它们不会走散**：
 * 列的顺序、表头文案、哪些是必填，这套约定只有一处定义。
 * 把它们拆到两个类里，改了一边忘了另一边就会出现"模板和解析对不上"，
 * 而用户看到的只是"导入的数据串列了"。
 *
 * <p>本类**只负责把 Excel 读成字符串**，不做任何业务校验
 * （那些在 {@code DeviceImportService} 里）。这样规则改动不用碰 Excel 解析。
 */
@Component
public class DeviceExcelImporter {

    /**
     * 列定义。顺序就是 Excel 里的列顺序。
     *
     * <p>带星号的表示必填 —— 表头文案里直接标出来，用户不用翻说明。
     */
    public static final String[] COLUMNS = {
            "设备名称*", "资产编号", "序列号", "设备分类", "所属部门",
            "型号", "生产厂商", "存放位置",
            "连通状态", "资产状态",
            "采购日期", "保修到期", "备注"
    };

    /** 各列宽度（单位 1/256 字符宽） */
    private static final int[] WIDTHS = {
            22, 18, 18, 14, 14,
            16, 16, 16,
            12, 12,
            14, 14, 26
    };

    private static final int WIDTH_UNIT = 256;

    /**
     * 单次导入的行数上限。
     *
     * <p>不是技术限制，是**为了不把接口拖到超时**：导入手是同步处理的
     * （用户点一下就该看到结果），而前端 axios 超时是 10 秒。
     * 超过这个行数请分批导入 —— 报错信息里会提示这一点。
     */
    public static final int MAX_ROWS = 2000;

    /**
     * 一行数据。
     *
     * <p>⚠️ 名字**不能叫 Row** —— 那个名字被 POI 的
     * {@code org.apache.poi.ss.usermodel.Row} 占了。嵌套类型会遮蔽外层导入的类，
     * 于是同一份文件里 `cellString(Row row, ...)` 的参数类型会突然变成这个 record，
     * 编译报一堆「incompatible types: org.apache.poi.ss.usermodel.Row cannot be converted to ...」。
     *
     * <p>全部是**字符串**，因为这里只负责读 Excel。
     * 日期已经被归一成 ISO 格式（{@code yyyy-MM-dd}），
     * 服务层直接 {@code LocalDate.parse} 即可。
     */
    public record ImportRow(
            int rowNum,
            String deviceName,
            String assetCode,
            String serialNumber,
            String categoryName,
            String deptName,
            String model,
            String manufacturer,
            String location,
            String status,
            String lifecycleStatus,
            String purchaseDate,
            String warrantyDate,
            String description) {
    }

    /** Excel 里可能是日期格式也可能是文本，统一转成字符串。用它可以避免科学计数法 */
    private static final DataFormatter FORMATTER = new DataFormatter();

    // ============================================================
    // 解析
    // ============================================================

    /**
     * 解析上传的 Excel。
     *
     * <p>用 {@link WorkbookFactory#create} 而不是直接 new XSSFWorkbook：
     * 它会自己判断是 .xlsx 还是老的 .xls，用户存错格式也能读进来。
     */
    public List<ImportRow> parse(InputStream inputStream) throws IOException {
        List<ImportRow> rows = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("文件里没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);

            // 第 0 行是表头，从第 1 行开始读
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlankRow(excelRow)) {
                    // 空行直接跳过，不算一行数据、也不报错 ——
                    // 用户在 Excel 里删数据时经常留下空行
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw new IllegalArgumentException(
                            "文件超过 " + MAX_ROWS + " 行，请拆分后分批导入");
                }
                rows.add(toRow(excelRow, i + 1));
            }
        }
        return rows;
    }

    /** 行号对外**从 1 开始且含表头**，和用户在 Excel 里看到的行号一致 */
    private ImportRow toRow(Row excelRow, int rowNum) {
        return new ImportRow(
                rowNum,
                cellString(excelRow, 0),
                cellString(excelRow, 1),
                cellString(excelRow, 2),
                cellString(excelRow, 3),
                cellString(excelRow, 4),
                cellString(excelRow, 5),
                cellString(excelRow, 6),
                cellString(excelRow, 7),
                cellString(excelRow, 8),
                cellString(excelRow, 9),
                cellString(excelRow, 10),
                cellString(excelRow, 11),
                cellString(excelRow, 12));
    }

    /**
     * 读一个单元格为字符串。
     *
     * <p><b>日期单元格单独处理</b>：Excel 里的日期底层是个数字，
     * `DataFormatter` 会按单元格的显示格式输出 —— 如果用户把格式设成
     * "2026年9月20日" 或 "9/20/26"，得到的字符串服务层解析不了。
     * 所以识别出日期单元格之后直接转成 ISO 格式输出，
     * 让服务层只需要支持一种日期写法。
     */
    private String cellString(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        String value = FORMATTER.formatCellValue(cell);
        return value == null ? "" : value.trim();
    }

    /** 整行都是空格子才算空行 */
    private boolean isBlankRow(Row row) {
        for (int i = 0; i < COLUMNS.length; i++) {
            if (!cellString(row, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ============================================================
    // 模板
    // ============================================================

    /**
     * 输出导入模板。
     *
     * <p>模板里放**一行示例数据** + 一个「填写说明」工作表。
     * 只给表头的话，用户不知道"设备分类"该填什么、日期该写什么格式，
     * 而这些恰好是最容易填错的两列。
     */
    public void writeTemplate(OutputStream outputStream,
                              List<String> sampleCategories,
                              List<String> sampleDepts) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeDataSheet(workbook);
            writeHelpSheet(workbook, sampleCategories, sampleDepts);
            workbook.write(outputStream);
        }
    }

    private void writeDataSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("设备导入");
        sheet.createFreezePane(0, 1);

        CellStyle headerStyle = buildHeaderStyle(workbook);
        CellStyle bodyStyle = buildBodyStyle(workbook);

        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int i = 0; i < COLUMNS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(COLUMNS[i]);
            cell.setCellStyle(headerStyle);
        }

        // 示例行。用户照着改比看说明快得多
        Row sample = sheet.createRow(1);
        String[] demo = {
                "示例：车间温控器", "ZC-2026-0100", "SN-DEMO-01", "传感器", "一号车间",
                "SHT-2000", "中科传感", "一号车间东侧",
                "在线", "正常",
                LocalDate.now().toString(), LocalDate.now().plusYears(2).toString(),
                "这一行是示例，导入前请删除",
        };
        for (int i = 0; i < COLUMNS.length; i++) {
            Cell cell = sample.createCell(i);
            cell.setCellValue(demo[i]);
            cell.setCellStyle(bodyStyle);
        }

        for (int i = 0; i < WIDTHS.length; i++) {
            sheet.setColumnWidth(i, WIDTHS[i] * WIDTH_UNIT);
        }
    }

    private void writeHelpSheet(XSSFWorkbook workbook,
                                List<String> categories, List<String> depts) {
        Sheet sheet = workbook.createSheet("填写说明");
        sheet.setColumnWidth(0, 20 * WIDTH_UNIT);
        sheet.setColumnWidth(1, 80 * WIDTH_UNIT);

        CellStyle titleStyle = buildHeaderStyle(workbook);
        CellStyle bodyStyle = buildBodyStyle(workbook);

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("列");
        header.createCell(1).setCellValue("说明");
        header.getCell(0).setCellStyle(titleStyle);
        header.getCell(1).setCellStyle(titleStyle);

        String[][] help = {
                {"设备名称*", "必填。不能为空"},
                {"资产编号", "选填，但**整表内和系统里都不能重复**。例如 ZC-2026-0100"},
                {"序列号", "选填，同样不能重复"},
                {"设备分类", "选填，填分类名称（见下方可选项）。填了系统里没有的名称，这一行会被跳过"},
                {"所属部门", "选填，填部门名称（见下方可选项）"},
                {"型号 / 生产厂商 / 存放位置", "选填"},
                {"连通状态", "选填，只能填：在线 / 离线 / 维修中 / 使用中。留空按「在线」处理"},
                {"资产状态", "选填，只能填：正常 / 维修 / 报废 / 停用。留空按「正常」处理"},
                {"采购日期 / 保修到期", "选填，写成 2026-09-20 这种格式，或者直接用 Excel 的日期单元格"},
                {"备注", "选填"},
                {"", ""},
                {"注意", "导入是逐行处理的：格式不对的行会被跳过并列出来，其余行正常导入"},
                {"可选的分类", categories.isEmpty() ? "（系统里还没有分类）" : String.join("、", categories)},
                {"可选的部门", depts.isEmpty() ? "（系统里还没有部门）" : String.join("、", depts)},
        };

        int rowIndex = 1;
        for (String[] line : help) {
            Row row = sheet.createRow(rowIndex++);
            Cell c0 = row.createCell(0);
            c0.setCellValue(line[0]);
            c0.setCellStyle(bodyStyle);
            Cell c1 = row.createCell(1);
            c1.setCellValue(line[1].replace("**", ""));
            c1.setCellStyle(bodyStyle);
        }
    }

    // ---------- 样式 ----------

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
}
