package com.buaa.usagi.agent.pipeline;

import com.buaa.usagi.agent.ReActAgent;
import com.buaa.usagi.config.ChatClientRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 流水线执行器：按照计划逐阶段「生产」子 Agent 并执行。
 *
 * 每个阶段：
 * 1. 根据阶段定义动态生产一个 ReActAgent 实例（专属 systemPrompt + 自动装配的技能）
 * 2. 把用户需求与前序阶段产物作为输入注入
 * 3. 执行子 Agent 的完整 ReAct 循环（可自行调用工具）
 * 4. 产物保存并作为下一阶段的上下文
 */
@Component
public class PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);

    // 默认使用的模型（ChatClientRegistry 中的 bean 名）
    private static final String DEFAULT_MODEL = "deepseek-chat";

    private static final Integer MAX_MESSAGES = 20;

    private final SkillRegistry skillRegistry;

    private final ChatClientRegistry chatClientRegistry;

    public PipelineExecutor(SkillRegistry skillRegistry, ChatClientRegistry chatClientRegistry) {
        this.skillRegistry = skillRegistry;
        this.chatClientRegistry = chatClientRegistry;
    }

    /**
     * 执行流水线计划
     *
     * @param plan        流水线计划
     * @param requirement 用户原始需求
     */
    public PipelineResult execute(PipelinePlan plan, String requirement) {
        long startedAt = System.currentTimeMillis();
        PipelineResult result = new PipelineResult();
        result.setObjective(plan.getObjective());

        ChatClient chatClient = chatClientRegistry.get(DEFAULT_MODEL);
        if (chatClient == null) {
            throw new IllegalStateException("未找到默认模型 ChatClient: " + DEFAULT_MODEL);
        }

        List<PipelineStep> steps = plan.getSteps();
        // 上游产物链（第 0 步时为原始需求）
        StringBuilder upstreamContext = new StringBuilder(requirement == null ? "" : requirement);

        try {
            for (int i = 0; i < steps.size(); i++) {
                PipelineStep step = steps.get(i);
                long stepStartedAt = System.currentTimeMillis();
                log.info("[Pipeline] 阶段 {}/{} 开始：{}（技能：{}）",
                        i + 1, steps.size(), step.getRole(), step.getSkills());

                // 1. 生产该阶段的子 Agent（自动装配技能）
                List<ToolCallback> toolCallbacks = skillRegistry.buildToolCallbacks(step.getSkills());
                ReActAgent subAgent = new ReActAgent(
                        step.getRole(),
                        step.getRole(),
                        step.getSystemPrompt(),
                        chatClient,
                        MAX_MESSAGES,
                        "pipeline-" + UUID.randomUUID(),
                        toolCallbacks);

                // 2. 构建输入：原始需求 + 上游阶段产物
                String input = buildStageInput(requirement, upstreamContext, step, i);

                // 3. 执行
                String output = subAgent.chat(input);

                // 4. 记录产物并更新上游上下文
                PipelineResult.StepOutput stepOutput = new PipelineResult.StepOutput(
                        i + 1,
                        step.getRole(),
                        step.getSkills(),
                        output,
                        System.currentTimeMillis() - stepStartedAt);
                result.getSteps().add(stepOutput);

                upstreamContext.setLength(0);
                upstreamContext.append(output);
                log.info("[Pipeline] 阶段 {}/{} 完成，耗时 {}ms",
                        i + 1, steps.size(), stepOutput.getDurationMs());
            }

            result.setFinalAnswer(result.getSteps().isEmpty()
                    ? ""
                    : result.getSteps().get(result.getSteps().size() - 1).getOutput());
            result.setSuccess(true);
        } catch (Exception e) {
            log.error("[Pipeline] 流水线执行失败", e);
            result.setSuccess(false);
            result.setError(e.getMessage());
            // 保留已完成阶段的产物
            result.setFinalAnswer(result.getSteps().isEmpty()
                    ? ""
                    : result.getSteps().get(result.getSteps().size() - 1).getOutput());
        } finally {
            result.setTotalDurationMs(System.currentTimeMillis() - startedAt);
        }
        return result;
    }

    /**
     * 构建阶段输入：
     * - 第 0 阶段：用户原始需求
     * - 后续阶段：用户原始需求 + 上游全部阶段产物
     */
    private String buildStageInput(String requirement, StringBuilder upstreamContext,
                                   PipelineStep step, int index) {
        if (index == 0) {
            return requirement == null ? "" : requirement;
        }
        return """
                用户原始需求：
                %s

                【上游阶段产物】（由之前的阶段生成，请基于此继续完成你的任务）
                %s

                现在开始执行你的阶段任务（角色：%s）。
                """.formatted(
                requirement == null ? "" : requirement,
                upstreamContext,
                step.getRole());
    }
}
