package com.buaa.usagi.agent.pipeline;

/**
 * 流水线中的一个阶段（步骤）定义。
 *
 * 每个阶段对应一个动态「生产」出来的子 Agent：
 * - role：阶段角色名（如「调研专员」「数据分析师」）
 * - systemPrompt：该阶段子 Agent 的系统提示词（角色 + 任务 + 输出要求）
 * - skills：该阶段自动装配的技能（工具）名列表，必须是技能白名单中的名称
 */
public class PipelineStep {

    private String role;

    private String systemPrompt;

    private java.util.List<String> skills;

    public PipelineStep() {
    }

    public PipelineStep(String role, String systemPrompt, java.util.List<String> skills) {
        this.role = role;
        this.systemPrompt = systemPrompt;
        this.skills = skills;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public java.util.List<String> getSkills() {
        return skills;
    }

    public void setSkills(java.util.List<String> skills) {
        this.skills = skills;
    }
}
