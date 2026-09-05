package com.buaa.usagi.model.response;

import com.buaa.usagi.model.vo.ChatSessionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class GetChatSessionResponse {
    private ChatSessionVO chatSession;
}
