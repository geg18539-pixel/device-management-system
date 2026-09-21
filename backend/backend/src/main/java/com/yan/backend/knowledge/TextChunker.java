package com.yan.backend.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 把长文本切成带重叠的小块。
 *
 * <p><b>为什么要切</b>：整篇文档一个向量的话，一本 50 页的手册会被压成一个点，
 * 里面具体哪一段讲了什么完全丢失。切块之后，用户问"XX 型号的额定电压是多少"，
 * 命中的就是讲这件事的那一段。
 *
 * <p><b>切法的取舍</b>：先按段落/句子切成"单元"，再把单元**贪心地攒成块**，
 * 而不是按固定字数硬切。硬切会把一句话拦腰截断，检索命中半句话是没法用的。
 * 只有当一个单元本身就超过块大小时，才退化成硬切（并保留重叠）。
 */
@Component
public class TextChunker {

    /** 句子边界：中英文的句末标点，以及换行。用零宽断言在标点**之后**切 */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？；!?;])|\\n");

    /**
     * 切分。
     *
     * @param text       已抽取的正文
     * @param chunkSize  每块的目标字符数
     * @param chunkOverlap 相邻块的重叠字符数（必须小于 chunkSize）
     * @param maxChunks  最多切多少块，超出的部分丢弃
     */
    public List<String> chunk(String text, int chunkSize, int chunkOverlap, int maxChunks) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        // 防御一下配置写反：重叠大于等于块大小时，攒块逻辑会原地打转
        int overlap = Math.max(0, Math.min(chunkOverlap, chunkSize / 2));

        List<String> units = splitIntoUnits(normalized, chunkSize, overlap);
        return pack(units, chunkSize, overlap, maxChunks);
    }

    // ============================================================
    // 归一化
    // ============================================================

    /**
     * 统一空白。
     *
     * <p>PDF 抽出来的文本经常带一大串连续空格（表格、缩进留下的），
     * 这些空白既占字符数又完全不带信息，还会把块大小提前撑满。
     * 全部压成单个空格。
     */
    private String normalize(String text) {
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        // 先把连续空格/制表符压掉，再处理空行 —— 顺序反了的话
        // "　 \n　 " 这种"看起来是空行、其实有内容"的行会被漏掉
        s = s.replaceAll("[ \\t\\u00A0\\u3000]+", " ");
        s = s.replaceAll(" *\n *", "\n");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.strip();
    }

    // ============================================================
    // 切成单元
    // ============================================================

    /**
     * 切成"单元"：优先段落，段落太长就按句子，句子还太长就硬切。
     *
     * <p>单元是攒块的原子，所以**每个单元都必须不大于块大小**，
     * 否则攒块时它一个人就超标，块会失控地大。
     */
    private List<String> splitIntoUnits(String text, int chunkSize, int overlap) {
        List<String> units = new ArrayList<>();
        for (String paragraph : text.split("\n{2,}")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= chunkSize) {
                units.add(p + "\n");
                continue;
            }
            // 段落太长：按句子拆
            for (String sentence : SENTENCE_END.split(p)) {
                String s = sentence.strip();
                if (s.isEmpty()) {
                    continue;
                }
                if (s.length() <= chunkSize) {
                    units.add(s);
                } else {
                    // 单句还超长（比如一整段没有标点的表格数据），只能硬切
                    units.addAll(hardSlice(s, chunkSize, overlap));
                }
            }
        }
        return units;
    }

    private List<String> hardSlice(String value, int size, int overlap) {
        List<String> out = new ArrayList<>();
        int step = Math.max(1, size - overlap);
        for (int i = 0; i < value.length(); i += step) {
            out.add(value.substring(i, Math.min(value.length(), i + size)));
            if (i + size >= value.length()) {
                break;
            }
        }
        return out;
    }

    // ============================================================
    // 攒成块
    // ============================================================

    private List<String> pack(List<String> units, int chunkSize, int overlap, int maxChunks) {
        List<String> chunks = new ArrayList<>();
        List<String> window = new ArrayList<>();
        int windowLength = 0;

        for (String unit : units) {
            if (windowLength > 0 && windowLength + unit.length() > chunkSize) {
                chunks.add(String.join("", window).strip());
                if (chunks.size() >= maxChunks) {
                    return chunks;
                }
                // 从尾部往回取，凑够重叠长度。按"整句"取而不是按字数截，
                // 免得重叠部分是从半个词开始的
                List<String> keep = new ArrayList<>();
                int keepLength = 0;
                for (int i = window.size() - 1; i >= 0; i--) {
                    String candidate = window.get(i);
                    if (keepLength + candidate.length() > overlap) {
                        break;
                    }
                    keep.add(0, candidate);
                    keepLength += candidate.length();
                }
                // 连重叠都放不下（说明这个单元本身就接近块大小），
                // 那就干脆不重叠。不加这个判断的话，window 永远清不空，
                // 攒块会原地打转切出无穷多个相同的块
                if (keepLength + unit.length() > chunkSize) {
                    keep = new ArrayList<>();
                    keepLength = 0;
                }
                window = keep;
                windowLength = keepLength;
            }
            window.add(unit);
            windowLength += unit.length();
        }

        if (windowLength > 0) {
            chunks.add(String.join("", window).strip());
        }
        return chunks;
    }
}
