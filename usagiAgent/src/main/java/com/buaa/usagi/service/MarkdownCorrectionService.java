package com.buaa.usagi.service;

import com.buaa.usagi.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 纠错服务：调用大模型，将 PDF 提取出的 Markdown 草稿
 * 整理为规范、结构化的 Markdown 文档。
 *
 * 输入可能是 PDF 文本提取产生的草稿（存在格式丢失、断行、乱序等问题），
 * 模型负责恢复标题层级、列表、代码块、表格等结构，同时不增删原文内容。
 */
@Service
@Slf4j
public class MarkdownCorrectionService {

    /** 单块最大字符数（避免超长输入，按段落边界切分） */
    private static final int MAX_CHARS_PER_CHUNK = 6000;

    private static final String SYSTEM_PROMPT =
            "你是一个专业的 Markdown 文档校对助手。用户会给你一段从 PDF 中提取的文本草稿，"
                    + "它可能存在格式丢失、断行错乱、识别错误等问题。请你将其整理为规范、结构清晰的 Markdown 文档。"
                    + "要求：\n"
                    + "1. 完整保留原文信息、专业术语、数据和结论，不得增删或改写实质内容，不得编造原文没有的内容；\n"
                    + "2. 合理恢复结构：标题层级（#/##/###）、无序/有序列表、代码块（```）、表格、引用、加粗等 Markdown 语法；\n"
                    + "3. 修复明显的提取问题：多余空行、断裂的词语、无意义的换行（合并为完整段落）；\n"
                    + "4. 原文没有明显结构的，按自然段落组织；\n"
                    + "5. 直接输出整理后的 Markdown 内容本身，不要输出任何解释、前言或后记。";

    private final ChatClient chatClient;

    public MarkdownCorrectionService(
            @Qualifier("deepseek-chat") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 纠错 PDF 提取的 Markdown 草稿。
     *
     * @param draft PDF 提取出的草稿文本
     * @return 纠错后的 Markdown
     */
    public String correct(String draft) {
        if (!StringUtils.hasLength(draft)) {
            return "";
        }
        List<String> chunks = splitChunks(draft);
        List<String> correctedChunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            log.info("[MarkdownCorrect] 纠错第 {}/{} 块，{} 字符", i + 1, chunks.size(), chunk.length());
            correctedChunks.add(correctChunk(chunk));
        }
        return String.join("\n\n", correctedChunks);
    }

    private String correctChunk(String chunk) {
        try {
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("待整理的文本草稿：\n=====\n" + chunk + "\n=====")
                    .call()
                    .content();
            if (!StringUtils.hasLength(result)) {
                throw new BizException("模型纠错返回为空");
            }
            return result.trim();
        } catch (NonTransientAiException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("401") || msg.contains("Authentication")
                    || msg.contains("authentication_error"))) {
                throw new BizException("模型纠错失败：大模型服务鉴权失败，API Key 未配置或无效，请在 application.yaml 中配置正确的 api-key 后重启服务");
            }
            throw new BizException("模型纠错失败：" + msg);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[MarkdownCorrect] 模型纠错调用失败", e);
            throw new BizException("模型纠错失败：" + e.getMessage());
        }
    }

    /**
     * 按段落边界切分文本，每块不超过 MAX_CHARS_PER_CHUNK。
     */
    private List<String> splitChunks(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // 按空行切分段落
        String[] paragraphs = text.split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;
            }
            // 单段超长：硬切
            while (p.length() > MAX_CHARS_PER_CHUNK) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                chunks.add(p.substring(0, MAX_CHARS_PER_CHUNK));
                p = p.substring(MAX_CHARS_PER_CHUNK);
            }
            if (current.length() + p.length() + 2 > MAX_CHARS_PER_CHUNK) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(p);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
