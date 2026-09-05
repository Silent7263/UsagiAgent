package com.buaa.usagi.model.request;

import lombok.Builder;
import lombok.Data;

/**
 * 外部对话处理请求：传入与其他大模型（ChatGPT/Claude/DeepSeek 等）的对话记录，
 * 由本系统整理对话或接替任务。
 */
@Data
@Builder
public class ExternalChatRequest {

    /**
     * 对话原文（从其他大模型复制或上传文件的文本内容）
     */
    private String conversationText;

    /**
     * 对话分享链接（与 conversationText 二选一，如 https://chat.deepseek.com/share/xxx）
     */
    private String conversationUrl;

    /**
     * 操作类型：
     * - SUMMARIZE 整理对话：生成结构化摘要
     * - TAKEOVER  接替任务：把对话导入智能体会话并继续推进
     */
    private String action;

    /**
     * 接替任务时必填：由哪个智能体接替（agentId）
     */
    private String agentId;

    /**
     * 可选：复用已有会话（不传则新建会话）
     */
    private String chatSessionId;

    /**
     * 可选：接替后的补充指令，如「继续完成上面的方案设计」
     */
    private String followUp;
}
