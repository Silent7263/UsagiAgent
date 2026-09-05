package com.buaa.usagi.controller;

import com.buaa.usagi.agent.pipeline.PipelineResult;
import com.buaa.usagi.agent.pipeline.PipelineService;
import com.buaa.usagi.model.common.ApiResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 多 Agent 协作流水线接口：直接提交需求，自动规划并执行流水线。
 */
@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * 运行流水线
     *
     * 请求体示例：{"requirement": "调研 Spring AI 工具调用机制，并写一篇技术总结"}
     */
    @PostMapping("/pipeline/run")
    public ApiResponse<PipelineResult> runPipeline(
            @RequestBody Map<String, String> request) {
        String requirement = request == null ? "" : request.get("requirement");
        return ApiResponse.success(pipelineService.execute(requirement));
    }
}
