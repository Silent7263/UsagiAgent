package com.buaa.usagi.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConversationParser 单元测试
 */
class ConversationParserTest {

    @Test
    void parseMixedChinesePrefixes() {
        String text = """
                我：你好
                ChatGPT：你好，有什么可以帮你？
                我：帮我写一个 Python 脚本
                ChatGPT：好的，以下是脚本
                """;
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        assertEquals(4, turns.size());
        assertEquals(ConversationParser.Role.USER, turns.get(0).role());
        assertEquals("你好", turns.get(0).content());
        assertEquals(ConversationParser.Role.ASSISTANT, turns.get(1).role());
        assertEquals("你好，有什么可以帮你？", turns.get(1).content());
        assertEquals(ConversationParser.Role.USER, turns.get(2).role());
        assertEquals(ConversationParser.Role.ASSISTANT, turns.get(3).role());
    }

    @Test
    void parseEnglishPrefixes() {
        String text = """
                User: hello
                Assistant: hi there
                Me: how are you?
                AI: I'm fine, thanks
                """;
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        assertEquals(4, turns.size());
        assertEquals(ConversationParser.Role.USER, turns.get(0).role());
        assertEquals(ConversationParser.Role.ASSISTANT, turns.get(1).role());
        assertEquals(ConversationParser.Role.USER, turns.get(2).role());
        assertEquals(ConversationParser.Role.ASSISTANT, turns.get(3).role());
    }

    @Test
    void parseNoPrefixFallsBackToSingleUserTurn() {
        String text = "这是一段没有说话人前缀的对话内容\n第二行";
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        assertEquals(1, turns.size());
        assertEquals(ConversationParser.Role.USER, turns.get(0).role());
        assertTrue(turns.get(0).content().contains("第二行"));
    }

    @Test
    void parseBoldAndQuotePrefixes() {
        String text = """
                **我：** 第一条消息
                > 用户：第二条消息
                """;
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        assertEquals(2, turns.size());
        assertEquals(ConversationParser.Role.USER, turns.get(0).role());
        assertEquals(ConversationParser.Role.USER, turns.get(1).role());
    }

    @Test
    void parseMultilineContentAccumulates() {
        String text = """
                我：第一行内容
                继续补充第二行
                ChatGPT：AI 的第一行
                AI 的第二行
                """;
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        assertEquals(2, turns.size());
        assertEquals("第一行内容\n继续补充第二行", turns.get(0).content());
        assertEquals("AI 的第一行\nAI 的第二行", turns.get(1).content());
    }

    @Test
    void parseEmptyContentTurnsAreDropped() {
        String text = """
                我：
                ChatGPT：有内容的回复
                """;
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        assertEquals(1, turns.size());
        assertEquals(ConversationParser.Role.ASSISTANT, turns.get(0).role());
    }

    @Test
    void urlLineIsNotTreatedAsTurnPrefix() {
        String text = """
                我：请看一下这个链接
                https://example.com/docs
                ChatGPT：这个链接是文档
                """;
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        assertEquals(2, turns.size());
        assertEquals("请看一下这个链接\nhttps://example.com/docs", turns.get(0).content());
    }

    @Test
    void aiContentWithQuestionWordDoesNotSplit() {
        String text = """
                我：请分析这个需求
                ChatGPT：针对你的问题：可以直接使用现有方案
                另外补充一点
                """;
        List<ConversationParser.Turn> turns = ConversationParser.parse(text);
        assertEquals(2, turns.size());
        assertEquals(ConversationParser.Role.ASSISTANT, turns.get(1).role());
        assertTrue(turns.get(1).content().startsWith("针对你的问题"));
    }

    @Test
    void parseEmptyTextReturnsEmpty() {
        List<ConversationParser.Turn> turns = ConversationParser.parse("   ");
        assertTrue(turns.isEmpty());
    }
}
