package com.buaa.usagi.service;

import com.buaa.usagi.model.request.ExternalChatRequest;
import com.buaa.usagi.model.response.ExternalChatResponse;
import com.buaa.usagi.model.response.FetchExternalChatResponse;

/**
 * 外部对话处理服务：接收与其他大模型的对话记录，
 * 支持「整理对话」（生成结构化摘要）与「接替任务」（导入智能体会话并继续推进）。
 */
public interface ExternalChatService {

    /**
     * 处理外部对话
     */
    ExternalChatResponse process(ExternalChatRequest request);

    /**
     * 从分享链接抓取对话内容（当前支持 DeepSeek 分享链接）
     */
    FetchExternalChatResponse fetch(String url);
}
