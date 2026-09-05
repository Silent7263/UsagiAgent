package com.buaa.usagi.event.listener;

import com.buaa.usagi.agent.Chat;
import com.buaa.usagi.agent.ChatFactory;
import com.buaa.usagi.event.ChatEvent;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ChatEventListener {

    private final ChatFactory chatFactory;

    @Async
    @EventListener
    public void handle(ChatEvent event) {
        // 创建一个 Agent 实例处理聊天事件
        Chat chat = chatFactory.create(event.getAgentId(), event.getSessionId());
        chat.run();
    }
}
