package com.buaa.usagi.agent.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水线执行结果：记录每个阶段的产物与最终汇总答案。
 */
public class PipelineResult {

    private String objective;

    private List<StepOutput> steps;

    private String finalAnswer;

    private boolean success;

    private String error;

    private long totalDurationMs;

    public PipelineResult() {
        this.steps = new ArrayList<>();
    }

    /**
     * 单个阶段的执行产物
     */
    public static class StepOutput {

        private int index;

        private String role;

        private List<String> skills;

        private String output;

        private long durationMs;

        public StepOutput() {
        }

        public StepOutput(int index, String role, List<String> skills, String output, long durationMs) {
            this.index = index;
            this.role = role;
            this.skills = skills;
            this.output = output;
            this.durationMs = durationMs;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<String> getSkills() {
            return skills;
        }

        public void setSkills(List<String> skills) {
            this.skills = skills;
        }

        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public List<StepOutput> getSteps() {
        return steps;
    }

    public void setSteps(List<StepOutput> steps) {
        this.steps = steps;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }
}
