package com.buaa.usagi.service;

import com.buaa.usagi.agent.tools.Tool;

import java.util.List;

public interface ToolFacadeService {
    List<Tool> getAllTools();

    List<Tool> getOptionalTools();

    List<Tool> getFixedTools();
}
