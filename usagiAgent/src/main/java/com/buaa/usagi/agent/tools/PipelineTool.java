package com.buaa.usagi.agent.tools;

import com.buaa.usagi.agent.pipeline.PipelineResult;
import com.buaa.usagi.agent.pipeline.PipelineService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 多智能体协作流水线工具。
 *
 * 把该工具装配给任意 Agent 后，Agent 在对话中遇到复杂需求时即可调用
 * runPipeline：系统会自动分析需求 → 拆解为多阶段流水线 → 为每个阶段生产
 * 一个子 Agent 并自动配置技能 → 协作执行 → 返回各阶段产物与最终答案。
 */
@Component
public class PipelineTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(PipelineTool.class);

    private final PipelineService pipelineService;

    private final ObjectMapper objectMapper;

    /**
     * 构造器说明：
     * PipelineService 依赖 SkillRegistry（收集全部 Tool），而 PipelineTool 本身是
     * ToolFacadeService 收集的工具之一，若在此处立即解析 PipelineService 会形成
     * 构造器循环依赖。因此对 pipelineService 使用 @Lazy 延迟到首次调用 runPipeline 时解析。
     */
    public PipelineTool(@Lazy PipelineService pipelineService, ObjectMapper objectMapper) {
        this.pipelineService = pipelineService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "pipelineTool";
    }

    @Override
    public String getDescription() {
        return "多智能体协作流水线工具：自动把复杂需求拆解为多个子 Agent 协作的流水线，"
                + "为每个子 Agent 自动配置技能并执行，返回分阶段产物与最终答案";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    /**
     * 运行多智能体协作流水线
     *
     * @param requirement 需要完成的需求描述
     * @return 各阶段产物与最终答案（JSON）
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "runPipeline",
            description = "运行多智能体协作流水线，自动拆解需求、生产子 Agent、配置技能并执行。"
                    + "参数：requirement - 需要完成的需求描述"
    )
    public String runPipeline(String requirement) {
        log.info("调用多智能体流水线，需求：{}", requirement);
        PipelineResult result = pipelineService.execute(requirement);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("序列化流水线结果失败", e);
            return "流水线执行" + (result.isSuccess() ? "完成" : "失败")
                    + "，但结果序列化失败：" + e.getMessage();
        }
    }
}
