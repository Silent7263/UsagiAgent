package com.buaa.usagi.service;

import com.buaa.usagi.model.dto.ChatMessageDTO;
import com.buaa.usagi.model.request.CreateChatMessageRequest;
import com.buaa.usagi.model.request.UpdateChatMessageRequest;
import com.buaa.usagi.model.response.CreateChatMessageResponse;
import com.buaa.usagi.model.response.GetChatMessagesResponse;

import java.util.List;

public interface ChatMessageFacadeService {
    GetChatMessagesResponse getChatMessagesBySessionId(String sessionId);

    List<ChatMessageDTO> getChatMessagesBySessionIdRecently(String sessionId, int limit);

    CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request);

    CreateChatMessageResponse createChatMessage(ChatMessageDTO chatMessageDTO);

    CreateChatMessageResponse agentCreateChatMessage(CreateChatMessageRequest request);

    CreateChatMessageResponse appendChatMessage(String chatMessageId, String appendContent);

    void deleteChatMessage(String chatMessageId);

    void updateChatMessage(String chatMessageId, UpdateChatMessageRequest request);
}
