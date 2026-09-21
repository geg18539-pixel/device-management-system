package com.yan.backend.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载响应头的工具方法。
 *
 * <p>抽出来是因为这段逻辑有三个**必须同时做对**的细节，散在各处迟早有人漏：
 * <ol>
 *   <li>中文文件名必须 URL 编码 —— HTTP 头不允许非 ASCII 字符，
 *       直接写 {@code filename="用户列表.xlsx"} 到了浏览器会变成乱码"???.xlsx"。</li>
 *   <li>要用 {@code filename*=UTF-8''xxx} 这种带编码声明的写法，
 *       只写 {@code filename=} 的话现代浏览器也不会按 UTF-8 解。</li>
 *   <li>{@link URLEncoder#encode} 按表单规则会把空格编成 {@code +}，
 *       但 HTTP 头里空格应该是 {@code %20}，所以要替换回来 ——
 *       不换的话文件名里的空格会原样变成加号。</li>
 * </ol>
 */
public final class DownloadUtils {

    private DownloadUtils() {
    }

    /**
     * 构造 Content-Disposition 头的值。
     *
     * @param fileName 展示用的文件名（可含中文、空格）
     * @param inline   true = 让浏览器内联显示（图片预览），false = 触发下载
     */
    public static String contentDisposition(String fileName, boolean inline) {
        String safeName = (fileName == null || fileName.isBlank()) ? "download" : fileName;
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        String type = inline ? "inline" : "attachment";
        return type + "; filename*=UTF-8''" + encoded;
    }
}
