package com.yan.backend.service.impl;

import com.yan.backend.dto.DeviceImportResultVO;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.SysDept;
import com.yan.backend.excel.DeviceExcelImporter;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.SysDeptRepository;
import com.yan.backend.service.DeviceImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class DeviceImportServiceImpl implements DeviceImportService {

    private static final Logger log = LoggerFactory.getLogger(DeviceImportServiceImpl.class);

    /** 失败明细最多返回多少条。格式全错的文件可能有上千条，全返回没意义还会撑爆前端 */
    private static final int MAX_ERROR_ROWS = 50;

    /** 允许的状态值。用 Device 上的常量拼出来，避免在两处各写一份字面量 */
    private static final Set<String> ALLOWED_STATUS = Set.of(
            Device.STATUS_ONLINE, Device.STATUS_OFFLINE,
            Device.STATUS_REPAIRING, Device.STATUS_IN_USE);

    private static final Set<String> ALLOWED_LIFECYCLE = Set.of(
            Device.LIFECYCLE_NORMAL, Device.LIFECYCLE_REPAIR,
            Device.LIFECYCLE_SCRAPPED, Device.LIFECYCLE_DISABLED);

    /**
     * 日期的几种可接受写法。
     *
     * <p>Excel 的日期单元格已经被解析器归一成 ISO 了，这里主要兜住
     * "用户手打成文本"的情况 —— 中文系统的 Excel 里 `2026/9/20` 很常见。
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                    // 2026-09-20
            DateTimeFormatter.ofPattern("yyyy/M/d"),             // 2026/9/20
            DateTimeFormatter.ofPattern("yyyy.M.d"),             // 2026.9.20
            DateTimeFormatter.ofPattern("yyyy年M月d日"),          // 2026年9月20日
            DateTimeFormatter.ofPattern("yyyyMMdd"));            // 20260920

    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final SysDeptRepository sysDeptRepository;
    private final DeviceExcelImporter importer;

    public DeviceImportServiceImpl(DeviceRepository deviceRepository,
                                   DeviceCategoryRepository deviceCategoryRepository,
                                   SysDeptRepository sysDeptRepository,
                                   DeviceExcelImporter importer) {
        this.deviceRepository = deviceRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.sysDeptRepository = sysDeptRepository;
        this.importer = importer;
    }

    // ============================================================
    // 导入
    // ============================================================

    @Override
    @Transactional
    public DeviceImportResultVO importDevices(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的文件");
        }

        List<DeviceExcelImporter.ImportRow> rows;
        try (var in = file.getInputStream()) {
            rows = importer.parse(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败，请确认是有效的 Excel 文件（.xlsx / .xls）", e);
        }

        DeviceImportResultVO result = new DeviceImportResultVO();
        result.setTotal(rows.size());
        if (rows.isEmpty()) {
            return result;
        }

        // ---------- 准备参照数据（一次查完，不在循环里查库）----------
        Map<String, Long> categoryIds = new HashMap<>();
        for (DeviceCategory c : deviceCategoryRepository.findAll()) {
            categoryIds.put(c.getCategoryName(), c.getId());
        }
        Map<String, Long> deptIds = new HashMap<>();
        for (SysDept d : sysDeptRepository.findAll()) {
            deptIds.put(d.getDeptName(), d.getId());
        }

        // 已有的资产编号 / 序列号。查重必须**同时**对比库里和本批次内部 ——
        // 只查库的话，同一批文件里重复两次的行都能过，最后入库时才炸唯一约束
        Set<String> existingAssetCodes = new HashSet<>();
        Set<String> existingSerialNumbers = new HashSet<>();
        for (Device d : deviceRepository.findAll()) {
            if (StringUtils.hasText(d.getAssetCode())) {
                existingAssetCodes.add(d.getAssetCode());
            }
            if (StringUtils.hasText(d.getSerialNumber())) {
                existingSerialNumbers.add(d.getSerialNumber());
            }
        }
        Set<String> batchAssetCodes = new HashSet<>();
        Set<String> batchSerialNumbers = new HashSet<>();

        List<Device> toSave = new ArrayList<>();
        List<DeviceImportResultVO.FailedRow> errors = new ArrayList<>();
        int errorTotal = 0;

        for (DeviceExcelImporter.ImportRow row : rows) {
            // 模板里的示例行：用户经常忘了删。不报错，但给一条提醒
            if (row.deviceName().startsWith("示例")) {
                result.getWarnings().add(
                        "第 " + row.rowNum() + " 行的设备名以「示例」开头，"
                                + "可能是模板自带的示例行，请确认它是否需要导入");
            }

            List<String> rowErrors = new ArrayList<>();

            // --- 设备名称 ---
            String deviceName = row.deviceName();
            if (!StringUtils.hasText(deviceName)) {
                rowErrors.add("设备名称为空");
            } else if (deviceName.length() > 100) {
                rowErrors.add("设备名称超过 100 个字符");
            }

            // --- 分类：按名称解析 ---
            Long categoryId = null;
            if (StringUtils.hasText(row.categoryName())) {
                categoryId = categoryIds.get(row.categoryName());
                if (categoryId == null) {
                    rowErrors.add("设备分类「" + row.categoryName() + "」不存在（可用名称见模板的「填写说明」页）");
                }
            }

            // --- 部门：按名称解析 ---
            Long deptId = null;
            if (StringUtils.hasText(row.deptName())) {
                deptId = deptIds.get(row.deptName());
                if (deptId == null) {
                    rowErrors.add("部门「" + row.deptName() + "」不存在（可用名称见模板的「填写说明」页）");
                }
            }

            // --- 状态：留空给默认值，填了就必须合法 ---
            String status = StringUtils.hasText(row.status()) ? row.status() : Device.STATUS_ONLINE;
            if (!ALLOWED_STATUS.contains(status)) {
                rowErrors.add("连通状态「" + row.status() + "」不合法，只能是："
                        + String.join(" / ", ALLOWED_STATUS));
            }
            String lifecycle = StringUtils.hasText(row.lifecycleStatus())
                    ? row.lifecycleStatus() : Device.LIFECYCLE_NORMAL;
            if (!ALLOWED_LIFECYCLE.contains(lifecycle)) {
                rowErrors.add("资产状态「" + row.lifecycleStatus() + "」不合法，只能是："
                        + String.join(" / ", ALLOWED_LIFECYCLE));
            }

            // --- 资产编号 / 序列号查重（库里 + 批内）---
            //
            // ★ 记下"这个编号是不是**本行**占上的"。
            // 不能无脑 remove：如果这个编号是前面某行成功占上的，
            // 本行只是撞上了它，remove 掉就等于把人家占的位子放出来，
            // 后面再来一行同样的编号就会被放行 → 入库时重复。
            String assetCode = trimToNull(row.assetCode());
            boolean assetClaimedByThisRow = false;
            if (assetCode != null) {
                if (existingAssetCodes.contains(assetCode)) {
                    rowErrors.add("资产编号「" + assetCode + "」在系统里已存在");
                } else if (batchAssetCodes.add(assetCode)) {
                    assetClaimedByThisRow = true;
                } else {
                    rowErrors.add("资产编号「" + assetCode + "」在本次文件里重复了");
                }
            }
            String serialNumber = trimToNull(row.serialNumber());
            boolean serialClaimedByThisRow = false;
            if (serialNumber != null) {
                if (existingSerialNumbers.contains(serialNumber)) {
                    rowErrors.add("序列号「" + serialNumber + "」在系统里已存在");
                } else if (batchSerialNumbers.add(serialNumber)) {
                    serialClaimedByThisRow = true;
                } else {
                    rowErrors.add("序列号「" + serialNumber + "」在本次文件里重复了");
                }
            }

            // --- 日期 ---
            LocalDate purchaseDate = parseDate(row.purchaseDate(), "采购日期", rowErrors);
            LocalDate warrantyDate = parseDate(row.warrantyDate(), "保修到期", rowErrors);

            if (!rowErrors.isEmpty()) {
                errorTotal++;
                // 明细只留前 N 条，但要统计总数（见 errorsTruncated）
                if (errors.size() < MAX_ERROR_ROWS) {
                    errors.add(new DeviceImportResultVO.FailedRow(
                            row.rowNum(), row.deviceName(),
                            String.join("；", rowErrors)));
                }
                // 只释放**本行自己占上**的编号，别动别人占的
                if (assetClaimedByThisRow) {
                    batchAssetCodes.remove(assetCode);
                }
                if (serialClaimedByThisRow) {
                    batchSerialNumbers.remove(serialNumber);
                }
                continue;
            }

            Device device = new Device();
            device.setDeviceName(deviceName);
            device.setAssetCode(assetCode);
            device.setSerialNumber(serialNumber);
            device.setCategoryId(categoryId);
            device.setDeptId(deptId);
            device.setModel(trimToNull(row.model()));
            device.setManufacturer(trimToNull(row.manufacturer()));
            device.setLocation(trimToNull(row.location()));
            device.setStatus(status);
            device.setLifecycleStatus(lifecycle);
            device.setPurchaseDate(purchaseDate);
            device.setWarrantyDate(warrantyDate);
            device.setDescription(trimToNull(row.description()));
            toSave.add(device);
        }

        // 一次性批量插入。逐行 save 会产生 N 条 insert，2000 行就是 2000 次往返
        if (!toSave.isEmpty()) {
            deviceRepository.saveAll(toSave);
        }

        result.setSuccessCount(toSave.size());
        result.setFailCount(errorTotal);
        result.setErrors(errors);
        result.setErrorsTruncated(errorTotal > errors.size());

        log.info("设备批量导入完成：共 {} 行，成功 {} 条，失败 {} 条",
                result.getTotal(), result.getSuccessCount(), result.getFailCount());
        return result;
    }

    // ============================================================
    // 模板
    // ============================================================

    @Override
    public void writeTemplate(OutputStream outputStream) {
        try {
            // 把系统里现有的分类和部门名称放进「填写说明」页 ——
            // 用户照着填就不会出现"名称不存在"的错误
            List<String> categories = deviceCategoryRepository.findAll().stream()
                    .map(DeviceCategory::getCategoryName).sorted().toList();
            List<String> depts = sysDeptRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                    .map(SysDept::getDeptName).sorted().toList();
            importer.writeTemplate(outputStream, categories, depts);
        } catch (IOException e) {
            throw new IllegalStateException("生成模板失败：" + e.getMessage(), e);
        }
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    /** 空串当 null，避免把 "" 写进库里（那样唯一性判断和展示都会很别扭） */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 解析日期，支持几种常见写法。
     *
     * <p>失败时**往 rowErrors 里加原因并返回 null**，不抛异常 ——
     * 一行日期写错不该让整个文件导入失败。
     */
    private LocalDate parseDate(String raw, String fieldName, List<String> rowErrors) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 换下一个格式
            }
        }
        rowErrors.add(fieldName + "「" + value + "」格式不对，写成 2026-09-20 这样即可");
        return null;
    }
}
