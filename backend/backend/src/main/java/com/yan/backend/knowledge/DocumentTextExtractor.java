package com.yan.backend.knowledge;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 把上传的文件读成纯文本。
 *
 * <p>支持 pdf / txt / md 三种。**只做抽取，不做切分** ——
 * 切分是 {@link TextChunker} 的事，两者的失败原因完全不同
 * （抽不出文字 vs 切得不好），分开才好定位。
 */
@Component
public class DocumentTextExtractor {

    /** 中文技术文档常见的 GB 系编码。GB18030 是 GBK 的超集，能覆盖更多生僻字 */
    private static final Charset GB_CHARSET = Charset.forName("GB18030");

    private static final String UTF8_BOM = "﻿";

    // ---------- Markdown 的轻量清理 ----------
    // 目标只是"降低噪声、让向量更贴内容"，不追求把 Markdown 完整解析成结构化文本。
    // 所以只去掉最常见、且**去掉之后一定不会改变语义**的标记。

    /** 代码围栏行（``` 或 ~~~ 开头）。围栏本身没有语义，留着纯是噪声 */
    private static final Pattern MD_FENCE = Pattern.compile("(?m)^\\s*(```|~~~).*$");
    /** 行首的标题井号：`## 标题` → `标题` */
    private static final Pattern MD_HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+");
    /** 链接和图片：`[文字](url)` → `文字`，`![文字](url)` → `文字` */
    private static final Pattern MD_LINK = Pattern.compile("!?\\[([^\\]]*)]\\([^)]*\\)");
    /** 强调：`**粗**` / `__粗__` → `粗`。
     *  ⚠️ 刻意**不动单个 `*` 和 `_`** —— 技术文档里全是 `device_id`
     *  这种标识符，把下划线去掉会直接改坏内容 */
    private static final Pattern MD_BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");

    /**
     * 抽取正文。
     *
     * @param fileType 小写扩展名（pdf / txt / md）
     * @throws IllegalArgumentException 类型不支持，或内容抽不出文字
     */
    public String extract(byte[] content, String fileType) {
        String text = switch (fileType == null ? "" : fileType) {
            case "pdf" -> extractPdf(content);
            case "txt" -> decodeText(content);
            case "md" -> cleanMarkdown(decodeText(content));
            default -> throw new IllegalArgumentException(
                    "不支持的文件类型：." + fileType + "（知识库只收 pdf / txt / md）");
        };

        String normalized = text == null ? "" : text.strip();
        if (normalized.isEmpty()) {
            // 扫描版 PDF 会走到这里：它每一页都是图片，抽出来是空的。
            // 必须明确报出来 —— 否则用户会以为"上传成功了但检索不到"，
            // 而真正的原因是这份文件里根本没有可检索的文字（需要 OCR）
            throw new IllegalArgumentException(
                    "没能从文件里抽取出任何文字。如果这是扫描版 PDF，"
                            + "需要先做 OCR 转成文字版才能入库");
        }
        return normalized;
    }

    // ============================================================
    // PDF
    // ============================================================

    private String extractPdf(byte[] content) {
        // ⚠️ PDFBox 3.x 用 Loader.loadPDF，2.x 的 PDDocument.load 已经删除。
        // 网上大量 2.x 的示例直接抄会编译不过
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按位置排序后再输出：不排的话多栏排版的文档会串行，
            // 左栏第一行接右栏第一行，读起来完全不通
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException ex) {
            throw new IllegalArgumentException("PDF 解析失败：" + ex.getMessage());
        }
    }

    // ============================================================
    // 编码
    // ============================================================

    /**
     * 把字节解码成文本。
     *
     * <p><b>为什么不能一律按 UTF-8 读</b>：国内大量 .txt 技术文档是 GBK/GB18030 编码的
     * （Windows 记事本早期默认就是 GBK）。按 UTF-8 硬读不会抛异常，只会得到
     * 一屏乱码 —— 而且乱码照样能切块、能嵌入，最后表现为"检索出来的东西看不懂"，
     * 极难归因。
     *
     * <p>做法：先用**严格模式**试 UTF-8（遇到非法字节立刻失败而不是替换成 ），
     * 失败了再退回 GB18030。这个顺序是对的 —— UTF-8 的字节序列约束很强，
     * 一段真正的 GBK 文本几乎不可能碰巧通过 UTF-8 的严格校验。
     */
    private String decodeText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String decoded = tryDecode(bytes, StandardCharsets.UTF_8);
        if (decoded == null) {
            decoded = tryDecode(bytes, GB_CHARSET);
        }
        if (decoded == null) {
            // 两种都失败（极其罕见），退回"尽力而为"的 UTF-8，
            // 至少不丢内容，比直接报错好
            decoded = new String(bytes, StandardCharsets.UTF_8);
        }
        // 去 BOM：留着会粘在第一个词前面，影响第一块的嵌入
        return decoded.startsWith(UTF8_BOM) ? decoded.substring(1) : decoded;
    }

    /** 严格解码。遇到非法字节返回 null 而不是抛异常 */
    private String tryDecode(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
            return buffer.toString();
        } catch (CharacterCodingException ex) {
            return null;
        }
    }

    // ============================================================
    // Markdown
    // ============================================================

    private String cleanMarkdown(String text) {
        String cleaned = MD_FENCE.matcher(text).replaceAll("");
        cleaned = MD_HEADING.matcher(cleaned).replaceAll("");
        cleaned = MD_LINK.matcher(cleaned).replaceAll("$1");
        cleaned = MD_BOLD.matcher(cleaned).replaceAll("$2");
        return cleaned;
    }
}
