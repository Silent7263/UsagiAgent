package com.buaa.usagi.service;

import com.buaa.usagi.model.request.CreateChatSessionRequest;
import com.buaa.usagi.model.request.UpdateChatSessionRequest;
import com.buaa.usagi.model.response.CreateChatSessionResponse;
import com.buaa.usagi.model.response.GetChatSessionResponse;
import com.buaa.usagi.model.response.GetChatSessionsResponse;

public interface ChatSessionFacadeService {
    GetChatSessionsResponse getChatSessions();

    GetChatSessionResponse getChatSession(String chatSessionId);

    GetChatSessionsResponse getChatSessionsByAgentId(String agentId);

    CreateChatSessionResponse createChatSession(CreateChatSessionRequest request);

    void deleteChatSession(String chatSessionId);

    void updateChatSession(String chatSessionId, UpdateChatSessionRequest request);
}
