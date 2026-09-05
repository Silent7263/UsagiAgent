package com.buaa.usagi.model.response;

import lombok.Builder;
import lombok.Data;

/**
 * 外部对话抓取响应：从分享链接解析出的对话内容
 */
@Data
@Builder
public class FetchExternalChatResponse {

    /**
     * 来源平台，如 deepseek
     */
    private String source;

    /**
     * 对话标题
     */
    private String title;

    /**
     * 消息条数
     */
    private Integer messageCount;

    /**
     * 转换后的对话文本（带说话人前缀，可直接用于整理/接替）
     */
    private String conversationText;
}
