package com.yan.backend.service.impl;

import com.yan.backend.common.ConfigKeys;
import com.yan.backend.config.FileStorageProperties;
import com.yan.backend.dto.StoredFileVO;
import com.yan.backend.service.FileStorageService;
import com.yan.backend.service.SysConfigService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    /**
     * 允许出现在服务端文件名里的字符。
     *
     * <p>我们自己生成的名字永远是 {@code UUID.扩展名}，天然只含这些字符。
     * 之所以还要校验，是因为 {@link #load} 和 {@link #delete} 的参数最终
     * **来自 URL**，不校验就允许 {@code ../../etc/passwd} 这种输入去拼路径。
     * 这是防目录穿越的第一道、也是最关键的一道闸。
     */
    private static final Pattern SAFE_STORED_NAME =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}\\.[A-Za-z0-9]{1,10}$");

    private final FileStorageProperties properties;
    private final SysConfigService configService;

    /** 上传根目录的绝对规范路径。启动时算好，之后每次拼路径都以它为基准 */
    private Path baseDir;

    public FileStorageServiceImpl(FileStorageProperties properties,
                                  SysConfigService configService) {
        this.properties = properties;
        this.configService = configService;
    }

    @PostConstruct
    void init() {
        // 启动时就把目录建好并解析成绝对路径。
        // 放在这里而不是每次上传时判断，是为了"配置错立刻发现"——
        // 目录建不出来（权限不足、路径不存在）应该在启动日志里报出来，
        // 而不是等用户第一次上传附件时才失败。
        this.baseDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
            // 存的是**真实路径**而不是规范化路径：下面校验文件是否越界时
            // 要拿它和 toRealPath() 的结果比，两边都必须是已解析软链接的形式，
            // 否则在 /tmp 之类的目录上（macOS 的 /var → /private/var）会误判。
            this.baseDir = baseDir.toRealPath();
            log.info("附件上传目录：{}", baseDir);
        } catch (IOException e) {
            // 这里抛出去让应用起不来。附件功能是这一版的核心能力之一，
            // 目录不可用的话静默降级只会让用户在上传时才遇到莫名其妙的错误
            throw new IllegalStateException("无法创建附件上传目录：" + baseDir, e);
        }
    }

    @Override
    public StoredFileVO store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件为空");
        }

        // 大小上限从**系统参数**读，管理员在「系统设置」里改完即时生效。
        // 注意它和 application.yml 里 spring.servlet.multipart.max-file-size 是配套的：
        // 那个必须设得更大，才能让这里的提示（"最大 N MB"）先触发
        int maxSizeMb = configService.getInt(
                ConfigKeys.UPLOAD_MAX_SIZE_MB, ConfigKeys.UPLOAD_MAX_SIZE_MB_DEFAULT);
        long maxBytes = maxSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件大小超过限制（最大 " + maxSizeMb + " MB）");
        }

        String originalName = cleanOriginalName(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        if (!properties.getAllowedExtensions().contains(extension)) {
            // 不支持的类型直接拒绝。不落盘、也不建数据库记录，避免留下半成品
            throw new IllegalArgumentException(
                    "不支持的文件类型：." + extension + "（允许："
                            + String.join("、", properties.getAllowedExtensions()) + "）");
        }

        // ★ 服务端自己生成文件名：UUID + 扩展名。
        // 用户的原始名一律不参与拼路径，也就不存在重名覆盖和路径穿越。
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = resolveSafely(storedName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败：" + e.getMessage(), e);
        }

        return new StoredFileVO(storedName, originalName, file.getSize(), file.getContentType());
    }

    @Override
    public Resource load(String storedName) {
        Path path = resolveSafely(storedName);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            // 数据库有记录但文件不在了（手工删了、卷没挂上、换机器没带文件）
            throw new IllegalArgumentException("文件不存在或已被移除");
        }
        try {
            // 第二重防线的收尾：解析真实路径（跟随软链接）后再确认一次没越界。
            // 万一有人往上传目录里放了一个指向 /etc 的软链接，前两重校验是看不出来的
            Path real = path.toRealPath();
            if (!real.startsWith(baseDir)) {
                throw new IllegalArgumentException("非法的文件名");
            }
            return new FileSystemResource(real);
        } catch (IOException e) {
            throw new IllegalArgumentException("文件不存在或已被移除");
        }
    }

    @Override
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolveSafely(storedName));
        } catch (IOException | IllegalArgumentException e) {
            // 删除失败（文件被占用、名字非法）不能影响业务：
            // 数据库记录该删还是要删，否则用户看到一个删不掉的附件。
            // 最坏情况是磁盘上留个孤儿文件，属于可接受的代价。
            log.warn("删除附件文件失败，已忽略：{}，原因：{}", storedName, e.getMessage());
        }
    }

    /**
     * 把服务端文件名解析成安全的绝对路径。
     *
     * <p>两层校验，任何一层不过都直接抛异常：
     * <ol>
     *   <li>格式白名单（正则）—— 从源头排除 {@code ..} 和路径分隔符</li>
     *   <li>拼接后 normalize，再确认**结果仍在 baseDir 之内**</li>
     * </ol>
     * 第三层（解析真实路径、跟随软链接后再确认一次）只在 {@link #load} 里做 ——
     * 那需要文件已存在，而 {@link #store} 时文件还没落盘。
     */
    private Path resolveSafely(String storedName) {
        if (storedName == null || !SAFE_STORED_NAME.matcher(storedName).matches()) {
            throw new IllegalArgumentException("非法的文件名");
        }

        Path resolved = baseDir.resolve(storedName).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法的文件名");
        }
        return resolved;
    }

    /**
     * 清理原始文件名，只留文件名本身。
     *
     * <p>有些浏览器（尤其老版本 IE）提交的是完整本地路径，
     * 取最后一段可以剥掉那部分。同时把反斜杠也当分隔符处理。
     */
    private String cleanOriginalName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未命名文件";
        }
        String cleaned = StringUtils.cleanPath(raw.trim());
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        if (!StringUtils.hasText(cleaned)) {
            return "未命名文件";
        }
        // 数据库那一列是 200，超长会被 MySQL 拒绝，这里先截断
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    /** 取扩展名（小写、不含点）。没有扩展名时返回空串，会被白名单挡掉 */
    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
