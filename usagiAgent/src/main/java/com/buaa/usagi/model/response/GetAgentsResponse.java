package com.buaa.usagi.model.response;

import com.buaa.usagi.model.vo.AgentVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetAgentsResponse {
    private AgentVO[] agents;
}
