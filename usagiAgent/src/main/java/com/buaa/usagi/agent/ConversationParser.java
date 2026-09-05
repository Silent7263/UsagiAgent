package com.buaa.usagi.agent;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部对话解析器：把从其他大模型（ChatGPT/Claude/DeepSeek/Kimi 等）复制来的对话文本，
 * 解析为多轮 USER / ASSISTANT 消息，供整理对话与接替任务使用。
 *
 * 支持常见说话人前缀（中文或英文，英文不区分大小写）：
 * - 用户侧：我 / 用户 / 人类 / 提问 / me / user / human / q
 * - AI 侧：你 / AI / 助手 / 机器人 / ChatGPT / Claude / DeepSeek / Kimi / 豆包 /
 *          文心一言 / 通义千问 / 讯飞星火 / assistant / a / ai助手
 *
 * 前缀后可跟中文冒号「：」或英文冒号「:」，并容忍行首的 **加粗**、引用 > 等常见格式。
 * 无法识别任何说话人前缀时，整段文本退化为一条 USER 消息。
 */
public final class ConversationParser {

    private ConversationParser() {}

    /** 对话轮次 */
    public record Turn(Role role, String content) {}

    /** 角色 */
    public enum Role { USER, ASSISTANT }

    /** 用户侧说话人标识（英文统一小写比较） */
    private static final List<String> USER_PREFIXES = List.of(
            "我", "用户", "人类", "我的提问", "me", "user", "human", "q"
    );

    /** AI 侧说话人标识（英文统一小写比较） */
    private static final List<String> ASSISTANT_PREFIXES = List.of(
            "你", "ai", "助手", "机器人", "chatgpt", "claude", "deepseek", "kimi",
            "豆包", "文心一言", "通义千问", "讯飞星火", "assistant", "a", "ai助手"
    );

    /** 最多解析轮数，超出部分并入最后一轮，防止超长输入拖垮后续处理 */
    private static final int MAX_TURNS = 100;

    /** 行首匹配：可选的引用/加粗修饰 + 说话人前缀 + 冒号 + 内容 */
    private static final Pattern TURN_START = Pattern.compile(
            "^(?:\\s*>\\s*|\\s*\\*\\*\\s*)?([\\p{L}0-9_\\- ]{1,24}?)\\s*[：:]\\s*(.*)$");

    /**
     * 解析对话文本为多轮消息
     */
    public static List<Turn> parse(String text) {
        List<Turn> turns = new ArrayList<>();
        if (!StringUtils.hasLength(text)) {
            return turns;
        }

        StringBuilder currentContent = new StringBuilder();
        Role currentRole = null;

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            Matcher matcher = TURN_START.matcher(line);
            if (matcher.matches()) {
                Role role = resolveRole(matcher.group(1).trim());
                if (role != null) {
                    // 上一轮收尾
                    appendTurn(turns, currentRole, currentContent);
                    // 开启新一轮
                    currentRole = role;
                    currentContent.setLength(0);
                    String rest = matcher.group(2).trim();
                    if (StringUtils.hasLength(rest)) {
                        currentContent.append(rest);
                    }
                    continue;
                }
            }
            // 普通内容行：追加到当前轮
            if (currentContent.length() > 0) {
                currentContent.append("\n");
            }
            currentContent.append(line);
        }
        // 最后一轮收尾
        appendTurn(turns, currentRole, currentContent);

        // 完全没解析出任何带角色的轮次 → 整段视为一条用户消息
        if (turns.isEmpty() && StringUtils.hasLength(text.trim())) {
            turns.add(new Turn(Role.USER, text.trim()));
        }
        return turns;
    }

    /**
     * 把累积的内容追加为一条轮次
     */
    private static void appendTurn(List<Turn> turns, Role role, StringBuilder content) {
        if (role == null) {
            return;
        }
        String text = content.toString().trim();
        if (!StringUtils.hasLength(text)) {
            return;
        }
        // 超出轮数上限时并入最后一轮
        if (turns.size() >= MAX_TURNS) {
            Turn last = turns.get(turns.size() - 1);
            turns.set(turns.size() - 1, new Turn(last.role(), last.content() + "\n" + text));
            return;
        }
        turns.add(new Turn(role, text));
    }

    /**
     * 根据说话人前缀解析角色；无法识别时返回 null
     */
    private static Role resolveRole(String prefix) {
        if (!StringUtils.hasLength(prefix)) {
            return null;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        if (USER_PREFIXES.contains(lower)) {
            return Role.USER;
        }
        if (ASSISTANT_PREFIXES.contains(lower)) {
            return Role.ASSISTANT;
        }
        return null;
    }
}
