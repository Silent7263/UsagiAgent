package com.buaa.usagi.service.impl;

import com.buaa.usagi.agent.ConversationParser;
import com.buaa.usagi.exception.BizException;
import com.buaa.usagi.model.dto.ChatMessageDTO;
import com.buaa.usagi.model.request.CreateChatMessageRequest;
import com.buaa.usagi.model.request.CreateChatSessionRequest;
import com.buaa.usagi.model.request.ExternalChatRequest;
import com.buaa.usagi.model.response.CreateChatSessionResponse;
import com.buaa.usagi.model.response.ExternalChatResponse;
import com.buaa.usagi.model.response.FetchExternalChatResponse;
import com.buaa.usagi.service.ChatMessageFacadeService;
import com.buaa.usagi.service.ChatSessionFacadeService;
import com.buaa.usagi.service.ExternalChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部对话处理服务实现
 *
 * 流程：
 * 1. 解析对话文本为多轮 USER / ASSISTANT 消息（支持直接粘贴文本，或从分享链接抓取）
 * 2. SUMMARIZE：调用 LLM 生成结构化摘要（对话主题 / 核心要点 / 结论与决策 / 待办事项 / 下一步建议）
 * 3. TAKEOVER：新建（或复用）智能体会话 → 导入对话轮次 → 发送接替指令触发 Agent 运行
 */
@Service
public class ExternalChatServiceImpl implements ExternalChatService {

    private static final Logger log = LoggerFactory.getLogger(ExternalChatServiceImpl.class);

    /** 操作类型：整理对话 */
    public static final String ACTION_SUMMARIZE = "SUMMARIZE";

    /** 操作类型：接替任务 */
    public static final String ACTION_TAKEOVER = "TAKEOVER";

    /** 接替任务的默认指令（用户未提供补充指令时使用） */
    private static final String DEFAULT_FOLLOW_UP =
            "以上是我与其他 AI 助手的对话记录，请理解上下文并接替我之前的任务继续协助我。"
                    + "若任务尚未完成请继续推进；若仅是咨询，请确认已理解并等待我的进一步指示。";

    /** DeepSeek 分享链接匹配：https://chat.deepseek.com/share/{shareId} */
    private static final Pattern DEEPSEEK_SHARE_URL = Pattern.compile(
            "https?://chat\\.deepseek\\.com/share/([a-zA-Z0-9_-]+)");

    /** DeepSeek 分享内容 API */
    private static final String DEEPSEEK_SHARE_API =
            "https://chat.deepseek.com/api/v0/share/content?share_id=%s";

    private static final String HTTP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ChatClient chatClient;

    private final ChatMessageFacadeService chatMessageFacadeService;

    private final ChatSessionFacadeService chatSessionFacadeService;

    private final ObjectMapper objectMapper;

    public ExternalChatServiceImpl(
            @Qualifier("deepseek-chat") ChatClient chatClient,
            ChatMessageFacadeService chatMessageFacadeService,
            ChatSessionFacadeService chatSessionFacadeService,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatSessionFacadeService = chatSessionFacadeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExternalChatResponse process(ExternalChatRequest request) {
        if (request == null) {
            throw new BizException("请求不能为空");
        }
        // 对话内容：优先使用直接粘贴的文本，为空时尝试从分享链接抓取
        String text = request.getConversationText();
        if (!StringUtils.hasLength(text) && StringUtils.hasLength(request.getConversationUrl())) {
            text = fetch(request.getConversationUrl()).getConversationText();
        }
        if (!StringUtils.hasLength(text)) {
            throw new BizException("对话内容不能为空");
        }
        text = text.trim();

        String action = request.getAction();
        if (ACTION_SUMMARIZE.equals(action)) {
            return summarize(text);
        }
        if (ACTION_TAKEOVER.equals(action)) {
            return takeover(request, text);
        }
        throw new BizException("不支持的操作类型：" + action);
    }

    @Override
    public FetchExternalChatResponse fetch(String url) {
        if (!StringUtils.hasLength(url)) {
            throw new BizException("分享链接不能为空");
        }
        String trimmedUrl = url.trim();
        Matcher matcher = DEEPSEEK_SHARE_URL.matcher(trimmedUrl);
        if (!matcher.find()) {
            throw new BizException("暂不支持该链接，目前支持 DeepSeek 分享链接"
                    + "（格式：https://chat.deepseek.com/share/xxx）");
        }
        String shareId = matcher.group(1);
        String apiUrl = String.format(DEEPSEEK_SHARE_API, shareId);
        log.info("[ExternalChat] 抓取 DeepSeek 分享链接：share_id={}", shareId);

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("User-Agent", HTTP_USER_AGENT)
                .header("Referer", trimmedUrl)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("[ExternalChat] 抓取分享链接失败", e);
            throw new BizException("无法访问分享链接，请检查链接是否有效");
        }
        if (response.statusCode() != 200) {
            throw new BizException("获取分享内容失败（HTTP " + response.statusCode() + "）");
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode bizData = root.path("data").path("biz_data");
            if (bizData.isMissingNode() || bizData.isNull()) {
                throw new BizException("分享内容解析失败，链接可能已失效");
            }
            String title = bizData.path("title").asText("Shared Conversation");
            JsonNode messages = bizData.path("messages");

            StringBuilder text = new StringBuilder();
            int count = 0;
            for (JsonNode message : messages) {
                String role = message.path("role").asText("");
                String content = message.path("content").asText("");
                if (!StringUtils.hasLength(content)) {
                    continue;
                }
                String prefix = "USER".equalsIgnoreCase(role) ? "我" : "DeepSeek";
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append(prefix).append("：").append(content);
                count++;
            }
            if (count == 0) {
                throw new BizException("分享链接中没有可用的对话内容");
            }
            return FetchExternalChatResponse.builder()
                    .source("deepseek")
                    .title(title)
                    .messageCount(count)
                    .conversationText(text.toString())
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ExternalChat] 解析分享内容失败", e);
            throw new BizException("分享内容解析失败，链接可能已失效");
        }
    }

    /**
     * 整理对话：生成结构化摘要
     */
    private ExternalChatResponse summarize(String text) {
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        log.info("[ExternalChat] 整理对话：{} 轮，{} 字符", turns.size(), text.length());

        String summary = chatClient.prompt()
                .system("你是一个专业的「对话整理助手」。你擅长从冗长的对话中提炼要点，"
                        + "输出结构清晰、可直接使用的 Markdown 整理结果，不编造对话中不存在的内容。")
                .user(buildSummarizePrompt(text))
                .call()
                .content();

        return ExternalChatResponse.builder()
                .action(ACTION_SUMMARIZE)
                .summary(summary)
                .turnCount(turns.size())
                .charCount(text.length())
                .build();
    }

    /**
     * 接替任务：导入对话到智能体会话并触发 Agent 继续推进
     */
    private ExternalChatResponse takeover(ExternalChatRequest request, String text) {
        if (!StringUtils.hasLength(request.getAgentId())) {
            throw new BizException("接替任务需要指定智能体（agentId）");
        }
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        log.info("[ExternalChat] 接替任务：{} 轮，{} 字符，agent={}",
                turns.size(), text.length(), request.getAgentId());

        // 1. 确定会话：复用已有会话或新建
        String chatSessionId = request.getChatSessionId();
        if (!StringUtils.hasLength(chatSessionId)) {
            CreateChatSessionRequest sessionRequest = new CreateChatSessionRequest();
            sessionRequest.setAgentId(request.getAgentId());
            sessionRequest.setTitle(buildSessionTitle(turns));
            CreateChatSessionResponse session = chatSessionFacadeService.createChatSession(sessionRequest);
            chatSessionId = session.getChatSessionId();
        }

        // 2. 导入对话轮次（不触发 Agent 运行）
        int imported = 0;
        for (ConversationParser.Turn turn : turns) {
            ChatMessageDTO.RoleType role = turn.role() == ConversationParser.Role.USER
                    ? ChatMessageDTO.RoleType.USER
                    : ChatMessageDTO.RoleType.ASSISTANT;
            ChatMessageDTO.MetaData metadata = null;
            if (role == ChatMessageDTO.RoleType.ASSISTANT) {
                // 外部 AI 消息没有工具调用；空列表避免后续加载记忆时出现空指针
                metadata = ChatMessageDTO.MetaData.builder()
                        .toolCalls(List.of())
                        .build();
            }
            chatMessageFacadeService.agentCreateChatMessage(CreateChatMessageRequest.builder()
                    .agentId(request.getAgentId())
                    .sessionId(chatSessionId)
                    .role(role)
                    .content(turn.content())
                    .metadata(metadata)
                    .build());
            imported++;
        }

        // 3. 发送接替指令（触发 Agent 运行，结果通过 SSE 推送给前端）
        String followUp = StringUtils.hasLength(request.getFollowUp())
                ? request.getFollowUp()
                : DEFAULT_FOLLOW_UP;
        chatMessageFacadeService.createChatMessage(CreateChatMessageRequest.builder()
                .agentId(request.getAgentId())
                .sessionId(chatSessionId)
                .role(ChatMessageDTO.RoleType.USER)
                .content(followUp)
                .build());

        return ExternalChatResponse.builder()
                .action(ACTION_TAKEOVER)
                .chatSessionId(chatSessionId)
                .turnCount(turns.size())
                .charCount(text.length())
                .importedMessages(imported)
                .build();
    }

    /**
     * 根据对话内容生成会话标题
     */
    private String buildSessionTitle(List<ConversationParser.Turn> turns) {
        for (ConversationParser.Turn turn : turns) {
            if (turn.role() == ConversationParser.Role.USER
                    && StringUtils.hasLength(turn.content())) {
                String title = turn.content().replaceAll("\\s+", " ").trim();
                return title.length() > 20 ? title.substring(0, 20) + "..." : title;
            }
        }
        return "外部对话接续";
    }

    private String buildSummarizePrompt(String text) {
        return """
                请整理以下对话记录，输出一份 Markdown 结构化整理结果，必须包含以下小节：

                ## 对话主题
                用一句话概括这段对话在做什么。

                ## 核心要点
                - 列出对话中达成的主要结论、关键信息与重要细节

                ## 结论与决策
                - 列出对话中已经确定的结论或做出的决策（没有则写「无」）

                ## 待办事项
                - 列出对话中提出但尚未完成的事项（没有则写「无」）

                ## 下一步建议
                - 如果用户要继续推进，建议接下来做什么

                【注意】只基于对话内容整理，不要编造或补充对话中不存在的信息。

                —— 对话记录 ——
                %s
                """.formatted(text);
    }
}
