package com.buaa.usagi.agent.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水线计划：由规划器（PipelinePlanner）根据用户需求动态生成。
 *
 * 包含总体目标与一串有序阶段，执行器按顺序为每个阶段生产子 Agent 并执行，
 * 前序阶段的产物会作为上下文传递给后序阶段。
 */
public class PipelinePlan {

    private String objective;

    private List<PipelineStep> steps;

    public PipelinePlan() {
        this.steps = new ArrayList<>();
    }

    public PipelinePlan(String objective, List<PipelineStep> steps) {
        this.objective = objective;
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public List<PipelineStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PipelineStep> steps) {
        this.steps = steps;
    }

    @Override
    public String toString() {
        return "PipelinePlan {" +
                "objective = '" + objective + '\'' +
                ", steps = " + (steps == null ? "[]" : steps.size()) +
                '}';
    }
}
