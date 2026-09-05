package com.buaa.usagi.controller;

import com.buaa.usagi.model.common.ApiResponse;
import com.buaa.usagi.model.request.ExternalChatRequest;
import com.buaa.usagi.model.request.FetchExternalChatRequest;
import com.buaa.usagi.model.response.ExternalChatResponse;
import com.buaa.usagi.model.response.FetchExternalChatResponse;
import com.buaa.usagi.service.ExternalChatService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部对话处理接口：传入与其他大模型的对话记录（粘贴文本 / 分享链接），
 * 整理对话或接替任务。
 *
 * 请求体示例：
 * {
 *   "conversationText": "我：...\nChatGPT：...",   // 与 conversationUrl 二选一
 *   "conversationUrl": "https://chat.deepseek.com/share/xxx",
 *   "action": "SUMMARIZE" | "TAKEOVER",
 *   "agentId": "接替任务时必填",
 *   "chatSessionId": "可选，复用已有会话",
 *   "followUp": "可选，接替后的补充指令"
 * }
 */
@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ExternalChatController {

    private final ExternalChatService externalChatService;

    @PostMapping("/external-chat/process")
    public ApiResponse<ExternalChatResponse> process(@RequestBody ExternalChatRequest request) {
        return ApiResponse.success(externalChatService.process(request));
    }

    @PostMapping("/external-chat/fetch")
    public ApiResponse<FetchExternalChatResponse> fetch(@RequestBody FetchExternalChatRequest request) {
        return ApiResponse.success(externalChatService.fetch(request.getUrl()));
    }
}
