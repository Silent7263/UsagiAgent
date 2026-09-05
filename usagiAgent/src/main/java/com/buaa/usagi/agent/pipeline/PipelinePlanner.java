package com.buaa.usagi.agent.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 流水线规划器：多 Agent 协作的「大脑」。
 *
 * 接收用户需求与技能白名单，调用 LLM 把需求拆解为一串有序的流水线阶段，
 * 每个阶段声明：角色、系统提示词、需要自动装配的技能。
 *
 * 安全约束：
 * - 技能名只能从白名单中选择，规划器出现白名单外的技能名会被剥离并告警
 * - 输出必须是结构化 JSON，解析失败会重试一次
 */
@Component
public class PipelinePlanner {

    private static final Logger log = LoggerFactory.getLogger(PipelinePlanner.class);

    private static final int MAX_STEPS = 6;

    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;

    public PipelinePlanner(
            @Qualifier("deepseek-chat") ChatClient chatClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成流水线计划
     *
     * @param requirement 用户需求
     * @param skills      技能白名单
     */
    public PipelinePlan plan(String requirement, List<SkillInfo> skills) {
        String skillsJson = serializeSkills(skills);
        String systemPrompt = buildPlannerPrompt(skillsJson);

        // 最多重试 2 次（LLM 输出偶发非 JSON）
        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String raw = chatClient.prompt()
                        .system(systemPrompt)
                        .user(requirement)
                        .call()
                        .content();
                if (!StringUtils.hasLength(raw)) {
                    throw new IllegalStateException("规划器返回空内容");
                }
                PipelinePlan plan = parsePlan(raw);
                sanitizeSkills(plan, skills);
                return plan;
            } catch (Exception e) {
                lastError = e;
                log.warn("规划器解析流水线计划失败（第 {} 次）：{}", attempt, e.getMessage());
            }
        }
        throw new IllegalStateException("流水线规划失败：无法生成有效的计划", lastError);
    }

    private String serializeSkills(List<SkillInfo> skills) {
        try {
            return objectMapper.writeValueAsString(skills == null ? List.of() : skills);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String buildPlannerPrompt(String skillsJson) {
        return """
                你是一个「多智能体流水线规划器」。你的任务：把用户的需求拆解为一串有序的流水线阶段，
                每个阶段将由一个专门的子 Agent 完成，多个子 Agent 协作最终完成用户需求。

                【硬性规则】
                1. 先理解需求，再决定拆成几个阶段（1-%d 个），阶段之间要有清晰的先后依赖：
                   常见模式：调研/收集 → 分析/处理 → 生成/汇总；不要把所有事塞进一个阶段。
                2. 每个阶段必须声明 role（角色名）、systemPrompt（该阶段子 Agent 的系统提示词）、
                   skills（该阶段需要装配的技能名数组，可为空数组 []）。
                3. systemPrompt 必须包含：角色定位、本阶段任务、输入来源说明（第 2 阶段起会收到
                   「上游阶段产物」）、明确的输出要求（输出必须是结构化文本，方便下游阶段继续使用）。
                4. skills 只能从下面的「可用技能」中选择，只能选择与本阶段任务真正相关的技能；
                   不需要工具的阶段 skills 填 []。
                5. 只输出一个 JSON 对象，不要输出任何解释、前后缀或 markdown 代码块。

                【输出 JSON 格式】
                {
                  "objective": "对用户需求的整体描述",
                  "steps": [
                    {
                      "role": "阶段角色名",
                      "systemPrompt": "该阶段子 Agent 的系统提示词",
                      "skills": ["技能名", ...]
                    }
                  ]
                }

                【可用技能】
                %s

                【注意】如果用户需求是简单对话/咨询，无需拆解时，steps 只放 1 个纯对话阶段（skills 为 []）。
                """.formatted(MAX_STEPS, skillsJson);
    }

    /**
     * 解析 LLM 返回的计划 JSON（容忍 markdown 代码块包裹）
     */
    private PipelinePlan parsePlan(String raw) throws Exception {
        String json = raw.trim();
        // 剥离可能的 ```json ... ``` 包裹
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            json = json.substring(firstNewline + 1, lastFence).trim();
        }
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.has("steps") || !root.get("steps").isArray()) {
            throw new IllegalStateException("计划 JSON 缺少 steps 数组");
        }

        String objective = root.hasNonNull("objective")
                ? root.get("objective").asText()
                : "";

        List<PipelineStep> steps = new ArrayList<>();
        for (JsonNode stepNode : root.get("steps")) {
            String role = stepNode.hasNonNull("role") ? stepNode.get("role").asText() : "阶段";
            String systemPrompt = stepNode.hasNonNull("systemPrompt")
                    ? stepNode.get("systemPrompt").asText()
                    : "";
            List<String> skills = new ArrayList<>();
            if (stepNode.has("skills") && stepNode.get("skills").isArray()) {
                stepNode.get("skills").forEach(s -> skills.add(s.asText()));
            }
            steps.add(new PipelineStep(role, systemPrompt, skills));
        }

        if (steps.isEmpty()) {
            throw new IllegalStateException("计划 JSON 的 steps 为空");
        }
        if (steps.size() > MAX_STEPS) {
            steps = steps.subList(0, MAX_STEPS);
            log.warn("规划器输出阶段数超过上限，截断为 {}", MAX_STEPS);
        }
        return new PipelinePlan(objective, steps);
    }

    /**
     * 校验并净化阶段技能：剥离白名单外的技能名（防止规划器幻觉出不存在的技能）
     */
    private void sanitizeSkills(PipelinePlan plan, List<SkillInfo> skills) {
        Set<String> whitelist = new HashSet<>();
        if (skills != null) {
            skills.forEach(s -> whitelist.add(s.getName()));
        }
        for (PipelineStep step : plan.getSteps()) {
            List<String> safe = new ArrayList<>();
            if (step.getSkills() != null) {
                for (String skillName : step.getSkills()) {
                    if (whitelist.contains(skillName)) {
                        safe.add(skillName);
                    } else {
                        log.warn("规划器选择了白名单外的技能「{}」，已剥离", skillName);
                    }
                }
            }
            step.setSkills(safe);
        }
    }
}
