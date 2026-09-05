package com.buaa.usagi.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PdfMarkdownConverter 单元测试：用 PDFBox 生成样例 PDF，验证转换输出
 */
class PdfMarkdownConverterTest {

    /** 尝试加载中文字体（Windows 黑体），失败返回 null */
    private PDFont loadChineseFont(PDDocument document) {
        String[] candidates = {
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simsun.ttc"
        };
        for (String path : candidates) {
            try {
                if (Files.exists(Paths.get(path))) {
                    return PDType0Font.load(document, new FileInputStream(path));
                }
            } catch (Exception ignore) {
                // 尝试下一个候选字体
            }
        }
        return null;
    }

    private byte[] buildEnglishPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                // 标题（大字）
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 18);
                cs.newLineAtOffset(50, 750);
                cs.showText("Chapter 1 Overview");
                cs.endText();

                // 正文第一段
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 720);
                cs.showText("The bus transfer cycle usually contains one or more bus clock cycles.");
                cs.endText();

                // 正文第二段
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("The bus working frequency is determined by the bus clock frequency.");
                cs.endText();

                // 列表
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 680);
                cs.showText("- request bus");
                cs.endText();
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 665);
                cs.showText("- data transfer");
                cs.endText();
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                document.save(baos);
                return baos.toByteArray();
            }
        }
    }

    private byte[] buildChinesePdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadChineseFont(document);
            if (font == null) {
                return null;
            }
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                // 标题（大字）
                cs.beginText();
                cs.setFont(font, 18);
                cs.newLineAtOffset(50, 750);
                cs.showText("第一章 总线概述");
                cs.endText();

                // 正文
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 720);
                cs.showText("总线传输周期通常包含一个或多个总线时钟周期。");
                cs.endText();
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                document.save(baos);
                return baos.toByteArray();
            }
        }
    }

    @Test
    void convert_shouldExtractTitleAndText() throws Exception {
        byte[] pdf = buildEnglishPdf();
        PdfMarkdownConverter converter = new PdfMarkdownConverter();

        String markdown = converter.convert(new ByteArrayInputStream(pdf));

        // 标题被识别为 Markdown 标题
        assertTrue(markdown.contains("# Chapter 1 Overview"),
                "标题应被识别为 # 标题，实际输出：" + markdown);
        // 正文文本被保留
        assertTrue(markdown.contains("The bus transfer cycle usually contains one or more bus clock cycles."),
                "正文第一段应被保留，实际输出：" + markdown);
        assertTrue(markdown.contains("The bus working frequency is determined by the bus clock frequency."),
                "正文第二段应被保留，实际输出：" + markdown);
    }

    @Test
    void convert_chinesePdf_shouldExtractText() throws Exception {
        byte[] pdf = buildChinesePdf();
        assumeTrue(pdf != null, "系统缺少中文字体，跳过中文 PDF 测试");

        PdfMarkdownConverter converter = new PdfMarkdownConverter();
        String markdown = converter.convert(new ByteArrayInputStream(pdf));

        assertTrue(markdown.contains("# 第一章 总线概述"),
                "中文标题应被识别为 # 标题，实际输出：" + markdown);
        assertTrue(markdown.contains("总线传输周期通常包含一个或多个总线时钟周期。"),
                "中文正文应被保留，实际输出：" + markdown);
    }

    @Test
    void convert_emptyPdf_shouldReturnEmpty() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                document.save(baos);
                PdfMarkdownConverter converter = new PdfMarkdownConverter();
                String markdown = converter.convert(new ByteArrayInputStream(baos.toByteArray()));
                assertTrue(markdown == null || markdown.isEmpty());
            }
        }
    }
}
