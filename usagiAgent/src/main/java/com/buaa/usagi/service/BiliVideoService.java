package com.buaa.usagi.service;

/**
 * B 站视频分析服务：通过 B 站字幕 API / 简介时间戳，
 * 生成视频的时间轴与重难点标注。
 */
public interface BiliVideoService {

    /**
     * 分析一个 B 站视频，返回 Markdown 格式的时间轴与重难点。
     *
     * @param urlOrBvid B 站视频链接（https://www.bilibili.com/video/BV...）或 BV 号
     * @return 时间轴 + 重难点文本；无字幕/简介时间戳时返回可读提示
     */
    String analyzeVideo(String urlOrBvid);
}
