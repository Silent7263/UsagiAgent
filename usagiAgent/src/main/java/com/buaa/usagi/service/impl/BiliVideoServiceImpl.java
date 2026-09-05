package com.buaa.usagi.service.impl;

import com.buaa.usagi.config.ChatClientRegistry;
import com.buaa.usagi.service.BiliVideoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BiliVideoServiceImpl implements BiliVideoService {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final Pattern BVID_PATTERN = Pattern.compile("BV[0-9A-Za-z]{10,}");
    private static final Pattern TS_PATTERN = Pattern.compile("(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})");
    private static final int MAX_SUBTITLE_CHARS = 8000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ChatClientRegistry chatClientRegistry;

    /** 可选：B 站登录 Cookie（SESSDATA=xxx），配置后可获取 AI 字幕 */
    private final String biliCookie;

    public BiliVideoServiceImpl(
            ObjectMapper objectMapper,
            ChatClientRegistry chatClientRegistry,
            @Value("${bilibili.cookie:}") String biliCookie) {
        this.objectMapper = objectMapper;
        this.chatClientRegistry = chatClientRegistry;
        this.biliCookie = biliCookie == null ? "" : biliCookie.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String analyzeVideo(String urlOrBvid) {
        // 1. 提取 BV 号
        Matcher bvMatcher = BVID_PATTERN.matcher(urlOrBvid);
        if (!bvMatcher.find()) {
            return "错误：无法从输入中识别 B 站 BV 号。请提供形如 https://www.bilibili.com/video/BV1xx411x7xx/ 的链接或直接给 BV 号。";
        }
        String bvid = bvMatcher.group();

        // 2. 获取视频信息（cid / 标题 / 简介）
        JsonNode videoInfo = getJson("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid, null);
        if (videoInfo == null) {
            return "错误：请求 B 站视频信息失败（网络或接口不可达），请稍后重试。";
        }
        if (videoInfo.path("code").asInt(-1) != 0) {
            int code = videoInfo.path("code").asInt(-1);
            String message = videoInfo.path("message").asText("未知错误");
            if (code == -404) {
                return "错误：视频不存在或已被删除（BV 号：" + bvid + "）。";
            }
            return "错误：B 站接口返回失败（code=" + code + "，" + message + "）。";
        }
        JsonNode data = videoInfo.path("data");
        String title = data.path("title").asText("未命名视频");
        String desc = data.path("desc").asText("");
        String cid = data.path("cid").asText("");
        String owner = data.path("owner").path("name").asText("");

        // 3. 优先尝试 CC 字幕
        String subtitleText = fetchSubtitle(bvid, cid);

        // 4. 无字幕时回退到简介时间戳目录
        List<String> timelineFromDesc = subtitleText == null ? parseDescTimeline(desc) : null;
        if (subtitleText == null && (timelineFromDesc == null || timelineFromDesc.isEmpty())) {
            String cookieHint = biliCookie.isEmpty()
                    ? "\n提示：在 application.yaml 中配置 bilibili.cookie（登录 B 站后的 SESSDATA）可获取该视频的 AI 字幕。"
                    : "";
            return String.format(
                    "该视频【%s】（up主：%s）没有可用的 CC 字幕，简介中也没有时间戳目录，无法生成时间轴。%s\n可尝试：①换用有字幕的视频；②后续接入语音转写方案后即可分析任意视频。",
                    title, owner, cookieHint);
        }

        // 5. 交给大模型整理成时间轴 + 重难点
        String rawText;
        if (subtitleText != null) {
            rawText = "（视频字幕）\n" + subtitleText;
        } else {
            rawText = "（视频简介中的时间戳目录）\n" + String.join("\n", timelineFromDesc);
        }
        return summarize(title, owner, rawText);
    }

    /**
     * 通过字幕接口获取带时间戳的字幕文本；无字幕返回 null。
     */
    private String fetchSubtitle(String bvid, String cid) {
        try {
            JsonNode player = getJson(
                    "https://api.bilibili.com/x/player/v2?bvid=" + bvid + "&cid=" + cid,
                    null);
            if (player == null || player.path("code").asInt(-1) != 0) {
                return null;
            }
            JsonNode subtitles = player.path("data").path("subtitle").path("subtitles");
            if (!subtitles.isArray() || subtitles.isEmpty()) {
                return null;
            }
            String subtitleUrl = null;
            for (JsonNode sub : subtitles) {
                String lan = sub.path("lan").asText("");
                if (lan.toLowerCase(Locale.ROOT).startsWith("zh")) {
                    subtitleUrl = sub.path("subtitle_url").asText("");
                    break;
                }
            }
            if (subtitleUrl == null || subtitleUrl.isEmpty()) {
                subtitleUrl = subtitles.get(0).path("subtitle_url").asText("");
            }
            if (subtitleUrl.isEmpty()) {
                return null;
            }
            if (subtitleUrl.startsWith("//")) {
                subtitleUrl = "https:" + subtitleUrl;
            }
            JsonNode body = getJson(subtitleUrl, "https://www.bilibili.com");
            if (body == null || !body.path("body").isArray()) {
                return null;
            }
            List<String> lines = new ArrayList<>();
            for (JsonNode seg : body.path("body")) {
                double from = seg.path("from").asDouble(-1);
                String content = seg.path("content").asText("");
                if (from < 0 || content.isEmpty()) {
                    continue;
                }
                lines.add("[" + formatTime(from) + "] " + content);
            }
            if (lines.isEmpty()) {
                return null;
            }
            String joined = String.join("\n", lines);
            return joined.length() > MAX_SUBTITLE_CHARS ? joined.substring(0, MAX_SUBTITLE_CHARS) + "\n…（字幕过长已截断）" : joined;
        } catch (Exception e) {
            log.warn("获取B站字幕失败 bvid={} cid={}: {}", bvid, cid, e.getMessage());
            return null;
        }
    }

    /**
     * 从简介中解析时间戳目录（兼容两种格式：
     * 每行以时间戳开头，如 "00:00 开场"；或一行内多个时间戳，如 "章节：00:00 引言 01:00 主体"）。
     */
    private List<String> parseDescTimeline(String desc) {
        if (desc == null || desc.isBlank()) {
            return null;
        }
        Matcher m = TS_PATTERN.matcher(desc);
        List<String> result = new ArrayList<>();
        int lastEnd = -1;
        String lastTimeText = null;
        while (m.find()) {
            String timeText = normalizeTs(m);
            // 上一段时间戳到当前时间戳之间的文本作为标题
            if (lastTimeText != null && lastEnd >= 0) {
                String title = desc.substring(lastEnd, m.start()).trim()
                        .replaceAll("^[\\s:：\\-–—>＞]+", "")
                        .replaceAll("[\\s:：\\-–—<＜]+$", "");
                if (title.length() >= 2) {
                    result.add(lastTimeText + " " + title);
                }
            }
            lastTimeText = timeText;
            lastEnd = m.end();
        }
        // 最后一段
        if (lastTimeText != null && lastEnd >= 0 && lastEnd < desc.length()) {
            String title = desc.substring(lastEnd).trim()
                    .replaceAll("^[\\s:：\\-–—>＞]+", "")
                    .replaceAll("[\\s:：\\-–—<＜]+$", "");
            if (title.length() >= 2) {
                result.add(lastTimeText + " " + title);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String normalizeTs(Matcher m) {
        if (m.group(1) != null) {
            return String.format("%s:%s:%s", m.group(1), m.group(2), m.group(3));
        }
        return String.format("%s:%s", m.group(2), m.group(3));
    }

    private String formatTime(double seconds) {
        int total = (int) seconds;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s);
    }

    /**
     * 大模型提炼：字幕/目录 → 时间轴 + 重难点。
     */
    private String summarize(String title, String owner, String rawText) {
        ChatClient chatClient = chatClientRegistry.get("deepseek-chat");
        if (chatClient == null) {
            chatClient = chatClientRegistry.get("glm-4.6");
        }
        if (chatClient == null) {
            // 无可用模型时，直接返回原始带时间戳文本
            return "视频：《" + title + "》\n" + rawText;
        }
        String systemPrompt = "你是视频学习助手。用户会给你一段带时间戳的B站视频字幕（或简介时间戳目录）。请整理为【时间轴与重难点】："
                + "1) 按内容切分成 5-12 个逻辑章节，每章给出起止时间、章节标题、一句话要点；"
                + "2) 关键概念、易错点、高频考点章节用 ⭐ 标记为重难点；"
                + "3) 结尾给出 🎯 学习建议（一句话）。"
                + "输出用 Markdown，格式严格为：\n"
                + "- [mm:ss - mm:ss] 章节标题 —— 一句话要点（重难点加 ⭐）\n"
                + "只输出整理结果，不要任何多余解释。";
        String userText = "视频标题：《" + title + "》（up主：" + owner + "）\n\n" + rawText;
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userText)
                    .call()
                    .content();
            return "视频：《" + title + "》（up主：" + owner + "）\n\n" + (content == null ? rawText : content);
        } catch (Exception e) {
            log.warn("字幕提炼失败，返回原始文本: {}", e.getMessage());
            return "视频：《" + title + "》\n" + rawText;
        }
    }

    /**
     * GET JSON，带可选 Referer 与登录 Cookie。失败/非2xx 返回 null。
     */
    private JsonNode getJson(String url, String referer) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .GET();
            if (referer != null) {
                builder.header("Referer", referer);
            }
            if (!biliCookie.isEmpty()) {
                builder.header("Cookie", biliCookie);
            }
            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("B站接口非200: {} -> {}", url, resp.statusCode());
                return null;
            }
            return objectMapper.readTree(resp.body());
        } catch (Exception e) {
            log.warn("B站接口请求失败: {} -> {}", url, e.getMessage());
            return null;
        }
    }
}
