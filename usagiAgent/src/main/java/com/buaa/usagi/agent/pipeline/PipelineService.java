package com.buaa.usagi.agent.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 流水线门面服务：多 Agent 协作的总入口。
 *
 * 流程：技能白名单 → 规划器生成流水线计划 → 执行器逐阶段生产子 Agent 并执行。
 * 任何一步失败都会返回带 error 的 PipelineResult，不会抛出异常到调用方。
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final SkillRegistry skillRegistry;

    private final PipelinePlanner pipelinePlanner;

    private final PipelineExecutor pipelineExecutor;

    public PipelineService(SkillRegistry skillRegistry,
                           PipelinePlanner pipelinePlanner,
                           PipelineExecutor pipelineExecutor) {
        this.skillRegistry = skillRegistry;
        this.pipelinePlanner = pipelinePlanner;
        this.pipelineExecutor = pipelineExecutor;
    }

    /**
     * 执行多 Agent 流水线：分析需求 → 规划 → 生产子 Agent → 协作执行
     *
     * @param requirement 用户需求
     */
    public PipelineResult execute(String requirement) {
        if (!StringUtils.hasLength(requirement)) {
            PipelineResult result = new PipelineResult();
            result.setSuccess(false);
            result.setError("需求描述不能为空");
            return result;
        }

        long startedAt = System.currentTimeMillis();
        log.info("[Pipeline] 收到需求：{}", truncate(requirement, 100));
        try {
            // 1. 技能白名单（规划器只能从中选择）
            List<SkillInfo> skills = skillRegistry.listSkills();

            // 2. 规划：需求 → 流水线计划
            PipelinePlan plan = pipelinePlanner.plan(requirement, skills);
            log.info("[Pipeline] 规划完成：{} 个阶段", plan.getSteps().size());

            // 3. 执行
            PipelineResult result = pipelineExecutor.execute(plan, requirement);
            result.setTotalDurationMs(System.currentTimeMillis() - startedAt);
            log.info("[Pipeline] 执行完成：success={}, 耗时 {}ms",
                    result.isSuccess(), result.getTotalDurationMs());
            return result;
        } catch (Exception e) {
            log.error("[Pipeline] 流水线执行异常", e);
            PipelineResult result = new PipelineResult();
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setTotalDurationMs(System.currentTimeMillis() - startedAt);
            return result;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
