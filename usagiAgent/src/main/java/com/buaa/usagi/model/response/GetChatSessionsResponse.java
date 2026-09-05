package com.buaa.usagi.model.response;

import com.buaa.usagi.model.vo.ChatSessionVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetChatSessionsResponse {
    private ChatSessionVO[] chatSessions;
}
