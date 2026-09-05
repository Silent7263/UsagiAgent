package com.buaa.usagi.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PDF → Markdown 转换器。
 *
 * 使用 PDFBox 提取文本，并做轻量级结构恢复：
 * - 明显的标题（短行 + 无句末标点 + 后随空行）转为 # 标题
 * - 列表符号（- * 数字.）保留为列表
 * - 其余按段落组织（空行分隔）
 *
 * 由于 PDF 文本提取本身会丢失版式，这里只保证"草稿质量"，
 * 后续由 {@link MarkdownCorrectionService} 通过大模型做结构化纠错。
 */
@Component
public class PdfMarkdownConverter {

    /** 句末标点：行尾以这些字符结尾时视为正文，而不是标题 */
    private static final Pattern SENTENCE_END = Pattern.compile("[。．.！？!?；;，,:：…]$");

    /** 标题最大长度（超过则视为正文） */
    private static final int TITLE_MAX_LEN = 60;

    /** 列表符号：行首为 "-"、"*"、"•" 或 "1."、"1、" 等 */
    private static final Pattern LIST_PREFIX = Pattern.compile("^\\s*(?:[-*•]|\\d+[.、)])?\\s*");

    public String convert(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按阅读位置排序，避免多栏/多列文本乱序
            stripper.setSortByPosition(true);
            String raw = stripper.getText(document);
            return structure(raw);
        }
    }

    /**
     * 对提取出的原始文本做轻量级 Markdown 结构化。
     */
    private String structure(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String[] lines = raw.split("\\r?\\n");
        List<String> out = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.isEmpty()) {
                flushParagraph(paragraph, out);
                continue;
            }

            boolean nextBlank = i + 1 < lines.length && lines[i + 1].trim().isEmpty();
            // 标题候选：短行、无句末标点、独立成段（前后有空行或文档边界）
            if (line.length() <= TITLE_MAX_LEN
                    && !SENTENCE_END.matcher(line).find()
                    && (nextBlank || i == 0 || isBlank(lines[i - 1]))) {
                flushParagraph(paragraph, out);
                out.add("# " + line);
                continue;
            }

            paragraph.add(line);
        }
        flushParagraph(paragraph, out);
        return String.join("\n", out);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void flushParagraph(List<String> paragraph, List<String> out) {
        if (paragraph.isEmpty()) {
            return;
        }
        // 段落内若为列表行，逐行保留；否则合并为一段
        boolean allListItems = paragraph.stream()
                .allMatch(l -> LIST_PREFIX.matcher(l).lookingAt() && !l.startsWith("#"));
        if (allListItems) {
            out.addAll(paragraph);
        } else {
            out.add(String.join("\n", paragraph));
        }
        // 段落之间空一行
        if (!out.isEmpty()) {
            out.add("");
        }
        paragraph.clear();
    }
}
