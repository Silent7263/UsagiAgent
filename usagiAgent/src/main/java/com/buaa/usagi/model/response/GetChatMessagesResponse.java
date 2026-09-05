package com.buaa.usagi.model.response;

import com.buaa.usagi.model.vo.ChatMessageVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetChatMessagesResponse {
    private ChatMessageVO[] chatMessages;
}

