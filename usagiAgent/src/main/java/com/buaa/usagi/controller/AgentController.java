package com.buaa.usagi.controller;

import com.buaa.usagi.model.common.ApiResponse;
import com.buaa.usagi.model.request.CreateAgentRequest;
import com.buaa.usagi.model.request.UpdateAgentRequest;
import com.buaa.usagi.model.response.CreateAgentResponse;
import com.buaa.usagi.model.response.GetAgentsResponse;
import com.buaa.usagi.service.AgentFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class AgentController {

    private final AgentFacadeService agentFacadeService;

    // 查询 agents
    @GetMapping("/agents")
    public ApiResponse<GetAgentsResponse> getAgents() {
        return ApiResponse.success(agentFacadeService.getAgents());
    }

    // 创建 agent
    @PostMapping("/agents")
    public ApiResponse<CreateAgentResponse> createAgent(@RequestBody CreateAgentRequest request) {
        return ApiResponse.success(agentFacadeService.createAgent(request));
    }

    // 删除 agent
    @DeleteMapping("/agents/{agentId}")
    public ApiResponse<Void> deleteAgent(@PathVariable String agentId) {
        agentFacadeService.deleteAgent(agentId);
        return ApiResponse.success();
    }

    // 更新 agent
    @PatchMapping("/agents/{agentId}")
    public ApiResponse<Void> updateAgent(@PathVariable String agentId, @RequestBody UpdateAgentRequest request) {
        agentFacadeService.updateAgent(agentId, request);
        return ApiResponse.success();
    }
}
