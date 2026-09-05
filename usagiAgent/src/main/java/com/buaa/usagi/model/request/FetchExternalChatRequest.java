package com.buaa.usagi.model.request;

import lombok.Builder;
import lombok.Data;

/**
 * 外部对话抓取请求：通过分享链接获取对话内容
 */
@Data
@Builder
public class FetchExternalChatRequest {

    /**
     * 分享链接，如 https://chat.deepseek.com/share/xxxxxxxx
     */
    private String url;
}
