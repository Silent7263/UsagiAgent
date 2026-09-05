package com.buaa.usagi.agent.pipeline;

import com.buaa.usagi.agent.tools.Tool;
import com.buaa.usagi.service.ToolFacadeService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能注册中心：管理项目中的全部技能（工具），并负责把技能名装配为可执行的工具回调。
 *
 * 职责：
 * 1. 向规划器（PipelinePlanner）提供技能白名单（名称 + 描述）
 * 2. 按名称解析技能实现（Tool），未知技能名直接报错，防止规划器幻觉出不存在的技能
 * 3. 把一组技能名转换为 Spring AI 的 ToolCallback 列表，供子 Agent 使用
 */
@Component
public class SkillRegistry {

    private final ToolFacadeService toolFacadeService;

    // 技能名 -> 技能实现（初始化时构建索引）
    private final Map<String, Tool> skillIndex = new LinkedHashMap<>();

    public SkillRegistry(ToolFacadeService toolFacadeService) {
        this.toolFacadeService = toolFacadeService;
        for (Tool tool : toolFacadeService.getAllTools()) {
            skillIndex.put(tool.getName(), tool);
        }
    }

    /**
     * 技能白名单（供规划器选择）
     */
    public List<SkillInfo> listSkills() {
        return toolFacadeService.getAllTools().stream()
                .map(tool -> new SkillInfo(
                        tool.getName(),
                        tool.getDescription(),
                        tool.getType().name()))
                .collect(Collectors.toList());
    }

    /**
     * 按技能名解析技能实现
     *
     * @throws IllegalArgumentException 存在未知技能名时抛出
     */
    public List<Tool> resolveTools(List<String> skillNames) {
        List<Tool> resolved = new ArrayList<>();
        if (skillNames == null) {
            return resolved;
        }
        for (String skillName : skillNames) {
            if (!StringUtils.hasLength(skillName)) {
                continue;
            }
            Tool tool = skillIndex.get(skillName);
            if (tool == null) {
                throw new IllegalArgumentException("未知技能：" + skillName
                        + "，可用技能：" + skillIndex.keySet());
            }
            resolved.add(tool);
        }
        return resolved;
    }

    /**
     * 把技能名列表装配为 ToolCallback 列表（与 ChatFactory 装配方式保持一致）
     */
    public List<ToolCallback> buildToolCallbacks(List<String> skillNames) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Tool tool : resolveTools(skillNames)) {
            Object target = resolveToolTarget(tool);
            ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(target)
                    .build()
                    .getToolCallbacks();
            callbacks.addAll(Arrays.asList(toolCallbacks));
        }
        return callbacks;
    }

    private Object resolveToolTarget(Tool tool) {
        try {
            return AopUtils.isAopProxy(tool)
                    ? AopUtils.getTargetClass(tool)
                    : tool;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "解析技能目标对象失败: " + tool.getName(), e);
        }
    }
}
