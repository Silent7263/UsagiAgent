package com.buaa.usagi.agent.tools;

import com.buaa.usagi.service.BiliVideoService;
import org.springframework.stereotype.Component;

@Component
public class BiliSubtitleTools implements Tool {

    private final BiliVideoService biliVideoService;

    public BiliSubtitleTools(BiliVideoService biliVideoService) {
        this.biliVideoService = biliVideoService;
    }

    @Override
    public String getName() {
        return "BiliSubtitleTool";
    }

    @Override
    public String getDescription() {
        return "用于分析B站视频：通过B站字幕API获取视频字幕或简介时间戳，生成结构化时间轴与重难点标注，帮助快速掌握视频内容。";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "analyzeBilibiliVideo",
            description = "分析一个B站视频并生成时间轴与重难点。参数：url（B站视频链接，如 https://www.bilibili.com/video/BV1xx411x7xx/ ，或直接给BV号，必填）。返回Markdown格式的时间轴（起止时间+章节标题+要点）与重难点标注。"
    )
    public String analyzeBilibiliVideo(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "错误：请提供 B 站视频链接或 BV 号";
        }
        try {
            return biliVideoService.analyzeVideo(url.trim());
        } catch (Exception e) {
            return "视频分析失败：" + e.getMessage();
        }
    }
}
