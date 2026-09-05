package com.buaa.usagi.agent.pipeline;

/**
 * 技能（Skill）元信息：提供给规划器（PipelinePlanner）的技能白名单条目。
 *
 * 技能 = 项目中已有的工具（Tool），规划器只能从该白名单中为流水线阶段选择技能。
 */
public class SkillInfo {

    private String name;

    private String description;

    private String type;

    public SkillInfo() {
    }

    public SkillInfo(String name, String description, String type) {
        this.name = name;
        this.description = description;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "SkillInfo {" +
                "name = '" + name + '\'' +
                ", description = '" + description + '\'' +
                '}';
    }
}
