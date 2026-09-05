package com.buaa.usagi.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ReAct 子 Agent：基础聊天 + 工具调用（think-execute 循环）。
 *
 * 作为多 Agent 流水线（PipelineExecutor）中逐阶段生产的子 Agent 执行器，
 * 由旧的示例 Agent 重构提升而来。
 *
 * 核心功能：
 * - 使用 ChatClient 与 LLM 交互
 * - 使用 ChatMemory 管理对话历史
 * - 支持系统提示词
 * - 支持工具调用（Tool Calling）
 * - 实现 think-execute 循环，自动执行工具并处理结果
 * - 不传工具时退化为基础聊天模式
 */
@Slf4j
public class ReActAgent {
    // 名称
    protected String name;

    // 描述
    protected String description;

    // 系统提示词
    protected String systemPrompt;

    // ChatClient 实例
    protected ChatClient chatClient;

    // 聊天记忆
    protected ChatMemory chatMemory;

    // 状态
    protected AgentState agentState;

    // 会话 ID（用于内存管理）
    protected String sessionId;

    // 可用的工具列表
    protected List<ToolCallback> availableTools;

    // 工具调用管理器
    protected ToolCallingManager toolCallingManager;

    // ChatOptions
    protected ChatOptions chatOptions;

    // 最后一次的 ChatResponse
    protected ChatResponse lastChatResponse;

    // 最多循环次数
    private static final Integer MAX_STEPS = 20;

    private static final Integer DEFAULT_MAX_MESSAGES = 20;

    public ReActAgent() {}

    /**
     * 基础聊天模式（不提供工具，退化为普通对话）
     */
    public ReActAgent(String name,
                  String description,
                  String systemPrompt,
                  ChatClient chatClient,
                  Integer maxMessages,
                  String sessionId) {
        this(name, description, systemPrompt, chatClient, maxMessages, sessionId, null);
    }

    /**
     * 完整构造：支持工具调用（ReAct 模型）
     */
    public ReActAgent(String name,
                  String description,
                  String systemPrompt,
                  ChatClient chatClient,
                  Integer maxMessages,
                  String sessionId,
                  List<ToolCallback> availableTools) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.chatClient = chatClient;
        this.sessionId = sessionId != null ? sessionId : "default-session";
        this.agentState = AgentState.IDLE;
        this.availableTools = availableTools;

        // 初始化聊天记忆
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(maxMessages != null ? maxMessages : DEFAULT_MAX_MESSAGES)
                .build();

        // 添加系统提示词
        if (StringUtils.hasLength(systemPrompt)) {
            this.chatMemory.add(this.sessionId, new SystemMessage(systemPrompt));
        }

        // 关闭 SpringAI 自带的内部工具调用自动执行功能
        // 我们需要手动控制工具调用的执行流程
        this.chatOptions = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();

        // 初始化工具调用管理器
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    /**
     * 处理用户输入并返回 AI 回复
     */
    public String chat(String userInput) {
        Assert.notNull(userInput, "用户输入不能为空");

        if (agentState != AgentState.IDLE) {
            throw new IllegalStateException("Agent 状态不是 IDLE，当前状态：" + agentState);
        }

        try {
            agentState = AgentState.THINKING;

            // 添加用户消息到记忆
            UserMessage userMessage = new UserMessage(userInput);
            chatMemory.add(sessionId, userMessage);

            // 执行 think-execute 循环
            for (int i = 0; i < MAX_STEPS && agentState != AgentState.FINISHED; i++) {
                step();
                if (i >= MAX_STEPS - 1) {
                    agentState = AgentState.FINISHED;
                    log.warn("达到最大步骤数，停止 Agent");
                }
            }

            // 获取最后的 AI 回复
            List<Message> history = chatMemory.get(sessionId);
            String aiResponse = "";
            for (int i = history.size() - 1; i >= 0; i--) {
                Message msg = history.get(i);
                if (msg instanceof AssistantMessage) {
                    aiResponse = ((AssistantMessage) msg).getText();
                    break;
                }
            }

            agentState = AgentState.FINISHED;

            return aiResponse;
        } catch (Exception e) {
            agentState = AgentState.ERROR;
            log.error("Agent 运行过程中发生错误", e);
            throw new RuntimeException("Agent 运行过程中发生错误", e);
        } finally {
            // 重置状态以便下次使用
            agentState = AgentState.IDLE;
        }
    }

    /**
     * 单个步骤：think -> execute（如果需要）
     */
    protected void step() {
        if (think()) {
            // 有工具调用，执行工具
            execute();
        } else {
            // 没有工具调用，直接结束
            agentState = AgentState.FINISHED;
        }
    }

    /**
     * Think 阶段：思考并决定是否需要调用工具
     * @return 如果有工具调用，返回 true；否则返回 false
     */
    protected boolean think() {
        String thinkPrompt = """
                现在你是一个智能的「决策模块」。
                请根据当前对话上下文，决定下一步的动作。
                如果需要调用工具来完成任务，请调用相应的工具。
                """;

        // 构建提示词
        Prompt prompt = Prompt.builder()
                .chatOptions(this.chatOptions)
                .messages(chatMemory.get(sessionId))
                .build();

        // 调用 LLM，传入工具回调
        this.lastChatResponse = chatClient
                .prompt(prompt)
                .system(thinkPrompt)
                .toolCallbacks(availableTools != null ? availableTools.toArray(new ToolCallback[0]) : new ToolCallback[0])
                .call()
                .chatClientResponse()
                .chatResponse();

        Assert.notNull(lastChatResponse, "Last chat response cannot be null");

        AssistantMessage output = this.lastChatResponse
                .getResult()
                .getOutput();

        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        // 打印工具调用信息
        logToolCalls(toolCalls);

        // 如果没有工具调用，将 AI 回复添加到记忆
        // 如果有工具调用，则在 execute() 中统一处理，避免出现未完成的工具调用
        if (toolCalls.isEmpty()) {
            chatMemory.add(sessionId, output);
        }

        // 如果工具调用不为空，则进入执行阶段
        return !toolCalls.isEmpty();
    }

    /**
     * Execute 阶段：执行工具调用
     */
    protected void execute() {
        Assert.notNull(this.lastChatResponse, "Last chat response cannot be null");

        if (!this.lastChatResponse.hasToolCalls()) {
            return;
        }

        // 构建提示词，包含当前的对话历史
        // 注意：此时 chatMemory 中还没有带有 tool_calls 的 AssistantMessage
        // ToolCallingManager.executeToolCalls() 会从 lastChatResponse 中获取 AssistantMessage
        // 并将其添加到对话历史中
        Prompt prompt = Prompt.builder()
                .messages(chatMemory.get(sessionId))
                .chatOptions(this.chatOptions)
                .build();

        // 执行工具调用
        // ToolCallingManager.executeToolCalls() 会：
        // 1. 从 prompt 中获取当前的对话历史
        // 2. 从 lastChatResponse 中获取带有 tool_calls 的 AssistantMessage，并添加到对话历史
        // 3. 执行工具调用
        // 4. 添加 ToolResponseMessage 到对话历史
        // toolExecutionResult.conversationHistory() 会包含完整的对话历史：
        // - 之前的对话历史
        // - 带有 tool_calls 的 AssistantMessage
        // - ToolResponseMessage（工具调用结果）
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, this.lastChatResponse);

        // 更新记忆：清除旧记忆，添加新的对话历史（包含工具调用结果）
        // 这样确保对话历史中，带有 tool_calls 的 AssistantMessage 后面一定有对应的 ToolResponseMessage
        chatMemory.clear(sessionId);
        chatMemory.add(sessionId, toolExecutionResult.conversationHistory());

        // 获取工具响应消息
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult
                .conversationHistory()
                .get(toolExecutionResult.conversationHistory().size() - 1);

        // 打印工具调用结果
        String result = toolResponseMessage.getResponses()
                .stream()
                .map(resp -> "工具 " + resp.name() + " 的返回结果为：" + resp.responseData())
                .collect(Collectors.joining("\n"));

//        log.info("工具调用结果：{}", result);

        // 检查是否有终止工具调用
        if (toolResponseMessage.getResponses()
                .stream()
                .anyMatch(resp -> resp.name().equals("terminate"))) {
            this.agentState = AgentState.FINISHED;
            log.info("任务结束");
        }
    }

    /**
     * 打印工具调用信息
     */
    protected void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.info("\n\n[ToolCalling] 无工具调用");
            return;
        }
        String logMessage = IntStream.range(0, toolCalls.size())
                .mapToObj(i -> {
                    AssistantMessage.ToolCall call = toolCalls.get(i);
                    return String.format(
                            "[ToolCalling #%d]\n- name      : %s\n- arguments : %s",
                            i + 1,
                            call.name(),
                            call.arguments()
                    );
                })
                .collect(Collectors.joining("\n\n"));
        log.info("\n\n========== Tool Calling ==========\n{}\n=================================\n", logMessage);
    }

    /**
     * 获取当前对话历史
     */
    public List<Message> getConversationHistory() {
        return chatMemory.get(sessionId);
    }

    /**
     * 重置对话历史
     */
    public void reset() {
        chatMemory.clear(sessionId);
        if (StringUtils.hasLength(systemPrompt)) {
            chatMemory.add(sessionId, new SystemMessage(systemPrompt));
        }
        agentState = AgentState.IDLE;
    }

    @Override
    public String toString() {
        return "ReActAgent {" +
                "name = " + name + ",\n" +
                "description = " + description + ",\n" +
                "systemPrompt = " + systemPrompt + "}";
    }
}
