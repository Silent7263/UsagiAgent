package com.buaa.usagi.model.response;

import lombok.Builder;
import lombok.Data;

/**
 * 外部对话处理响应
 */
@Data
@Builder
public class ExternalChatResponse {

    /**
     * 操作类型：SUMMARIZE / TAKEOVER
     */
    private String action;

    /**
     * 整理对话结果（Markdown 结构化摘要）
     */
    private String summary;

    /**
     * 接替任务：会话 ID
     */
    private String chatSessionId;

    /**
     * 解析出的对话轮数
     */
    private Integer turnCount;

    /**
     * 对话文本字符数
     */
    private Integer charCount;

    /**
     * 接替任务：实际导入的消息条数
     */
    private Integer importedMessages;
}
