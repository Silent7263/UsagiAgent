package com.buaa.usagi.service;

import com.buaa.usagi.model.request.CreateAgentRequest;
import com.buaa.usagi.model.request.UpdateAgentRequest;
import com.buaa.usagi.model.response.CreateAgentResponse;
import com.buaa.usagi.model.response.GetAgentsResponse;

public interface AgentFacadeService {
    GetAgentsResponse getAgents();

    CreateAgentResponse createAgent(CreateAgentRequest request);

    void deleteAgent(String agentId);

    void updateAgent(String agentId, UpdateAgentRequest request);
}
