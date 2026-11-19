package io.github.susongyan.bobachop.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.exception.LexException;

/**
 * MySQL 词法器黄金 token 序列（DESIGN T4 DoD：转义/0x/0b/包裹标识符/注释偏移/运算符最长匹配）。
 */
class LexerTest {

    @Test
    void keywordLookupIsCaseInsensitiveButTextPreserved() {
        final List<Token> tokens = Lexer.lex("SeLeCt * FROM t1");
        assertTok(tokens.get(0), TokenType.KEYWORD, "SeLeCt", 0, 5, Kw.SELECT);
        assertTok(tokens.get(1), TokenType.OPERATOR, "*", 7, 7);
        assertTok(tokens.get(2), TokenType.KEYWORD, "FROM", 9, 12, Kw.FROM);
        assertTok(tokens.get(3), TokenType.IDENTIFIER, "t1", 14, 15);
        assertEof(tokens, 16);
    }

    @Test
    void reservedWordsAreKeywordsUnknownWordsAreIdentifiers() {
        assertEquals(Kw.ORDER, Lexer.lex("order").get(0).kw());
        assertEquals(Kw.ORDER, Lexer.lex("ORDER").get(0).kw());
        assertEquals(TokenType.IDENTIFIER, Lexer.lex("offsetx").get(0).type());
        assertTok(Lexer.lex("offset").get(0), TokenType.KEYWORD, "offset", 0, 5, Kw.OFFSET);
        assertFalse(Kw.OFFSET.reserved());
        assertFalse(Kw.DUPLICATE.reserved());
        assertFalse(Kw.ESCAPE.reserved());
        assertTrue(Kw.SELECT.reserved());
        assertEquals(TokenType.IDENTIFIER, Lexer.lex("a$b").get(0).type());
    }

    @Test
    void backtickQuotedIdentifierNeverHitsKeywordTable() {
        assertTok(Lexer.lex("`select`").get(0), TokenType.QUOTED_IDENTIFIER, "`select`", 0, 7);
        // 反引号双写转义：一个 token
        assertTok(Lexer.lex("`a``b`").get(0), TokenType.QUOTED_IDENTIFIER, "`a``b`", 0, 5);
        final LexException e = assertThrows(LexException.class, () -> Lexer.lex("`abc"));
        assertEquals(0, e.offset());
    }

    @Test
    void stringEscapesQuoteDoublingAndBackslash() {
        assertTok(Lexer.lex("'a''b'").get(0), TokenType.STRING, "'a''b'", 0, 5);
        assertTok(Lexer.lex("'a\\'b'").get(0), TokenType.STRING, "'a\\'b'", 0, 5);
        assertTok(Lexer.lex("\"a\"\"b\"").get(0), TokenType.STRING, "\"a\"\"b\"", 0, 5);
        // 引导形态 N''/X''/B'' 并入 STRING，文本含前缀
        assertTok(Lexer.lex("N'abc'").get(0), TokenType.STRING, "N'abc'", 0, 5);
        assertTok(Lexer.lex("X'4D'").get(0), TokenType.STRING, "X'4D'", 0, 4);
        assertTok(Lexer.lex("B'01'").get(0), TokenType.STRING, "B'01'", 0, 4);
    }

    @Test
    void unterminatedLiteralsAndCommentsReportOffset() {
        assertEquals(0, assertThrows(LexException.class, () -> Lexer.lex("'abc")).offset());
        assertEquals(7, assertThrows(LexException.class, () -> Lexer.lex("SELECT 'a")).offset());
        assertEquals(0, assertThrows(LexException.class, () -> Lexer.lex("'a\\")).offset());
        assertEquals(0, assertThrows(LexException.class, () -> Lexer.lex("/* x")).offset());
    }

    @Test
    void numberFormsPreserveSourceText() {
        assertTok(Lexer.lex("0x1F").get(0), TokenType.HEX, "0x1F", 0, 3);
        assertTok(Lexer.lex("0b0101").get(0), TokenType.BIT, "0b0101", 0, 5);
        assertTok(Lexer.lex("1.5e-3").get(0), TokenType.NUMBER, "1.5e-3", 0, 5);
        assertTok(Lexer.lex(".5").get(0), TokenType.NUMBER, ".5", 0, 1);
        assertTok(Lexer.lex("5.").get(0), TokenType.NUMBER, "5.", 0, 1);
        assertTok(Lexer.lex("1e3").get(0), TokenType.NUMBER, "1e3", 0, 2);
        // 1.5.3 → 两个数字 token（合法词法，语法层拒绝）
        assertTok(Lexer.lex("1.5.3").get(0), TokenType.NUMBER, "1.5", 0, 2);
        assertTok(Lexer.lex("1.5.3").get(1), TokenType.NUMBER, ".3", 3, 4);
    }

    @Test
    void malformedNumbersThrowWithOffset() {
        assertEquals(0, assertThrows(LexException.class, () -> Lexer.lex("1abc")).offset());
        assertEquals(0, assertThrows(LexException.class, () -> Lexer.lex("0x")).offset());
        assertEquals(0, assertThrows(LexException.class, () -> Lexer.lex("0xg")).offset());
        assertEquals(1, assertThrows(LexException.class, () -> Lexer.lex("1e")).offset());
    }

    @Test
    void operatorsMatchLongestFirst() {
        single(TokenType.OPERATOR, "<=>");
        single(TokenType.OPERATOR, "<=");
        single(TokenType.OPERATOR, "<>");
        single(TokenType.OPERATOR, ":=");
        single(TokenType.OPERATOR, "->");
        single(TokenType.OPERATOR, "->>");
        single(TokenType.OPERATOR, "<<");
        single(TokenType.OPERATOR, ">>");
        single(TokenType.OPERATOR, "||");
        single(TokenType.OPERATOR, "&&");
        single(TokenType.OPERATOR, "!=");
        single(TokenType.OPERATOR, ">=");
        single(TokenType.OPERATOR, "<");
        single(TokenType.OPERATOR, "~");
    }

    @Test
    void doubleDashNeedsTrailingSpaceToBeComment() {
        // "--2"：两个减号 + 数字（表达式 1 - (-2) 的前缀形态）
        final List<Token> expr = Lexer.lex("SELECT 1 --2");
        assertTok(expr.get(2), TokenType.OPERATOR, "-", 9, 9);
        assertTok(expr.get(3), TokenType.OPERATOR, "-", 10, 10);
        assertTok(expr.get(4), TokenType.NUMBER, "2", 11, 11);
        // "-- 2"：行注释吞到 EOL
        final List<Token> commented = Lexer.lex("SELECT 1 -- 2");
        assertEquals(3, commented.size());
        assertEof(commented, 13);
        // "#" 行注释
        final List<Token> hash = Lexer.lex("SELECT 1 #c\n2");
        assertTok(hash.get(2), TokenType.NUMBER, "2", 12, 12);
        // /*! */ 版本注释 v1 同按注释跳过
        assertTok(Lexer.lex("/*! v */ 2").get(0), TokenType.NUMBER, "2", 9, 9);
    }

    @Test
    void commentSkippingKeepsAbsoluteOffsets() {
        final List<Token> tokens = Lexer.lex("SELECT /* c */ 1");
        assertTok(tokens.get(0), TokenType.KEYWORD, "SELECT", 0, 5, Kw.SELECT);
        assertTok(tokens.get(1), TokenType.NUMBER, "1", 15, 15);
        assertEof(tokens, 16);
    }

    @Test
    void paramMarkerAndEofSentinel() {
        final List<Token> tokens = Lexer.lex("a = ?");
        assertTok(tokens.get(0), TokenType.IDENTIFIER, "a", 0, 0);
        assertTok(tokens.get(1), TokenType.OPERATOR, "=", 2, 2);
        assertTok(tokens.get(2), TokenType.PARAM, "?", 4, 4);
        assertEof(tokens, 5);
    }

    @Test
    void temporalKeywordAndStringStaySeparateTokens() {
        final List<Token> tokens = Lexer.lex("DATE '2020-01-01'");
        assertTok(tokens.get(0), TokenType.KEYWORD, "DATE", 0, 3, Kw.DATE);
        assertTok(tokens.get(1), TokenType.STRING, "'2020-01-01'", 5, 16);
    }

    @Test
    void illegalCharactersThrowWithOffset() {
        assertEquals(0, assertThrows(LexException.class, () -> Lexer.lex("¥")).offset());
        assertEquals(0, assertThrows(LexException.class, () -> Lexer.lex(":x")).offset());
        assertEquals(2, assertThrows(LexException.class, () -> Lexer.lex("a : b")).offset());
        // v1 边界：字符串/注释外非 ASCII 拒绝
        assertEquals(7, assertThrows(LexException.class, () -> Lexer.lex("SELECT 名")).offset());
    }

    @Test
    void qualifiedStarLexesAsThreeTokens() {
        final List<Token> tokens = Lexer.lex("t.*");
        assertTok(tokens.get(0), TokenType.IDENTIFIER, "t", 0, 0);
        assertTok(tokens.get(1), TokenType.DOT, ".", 1, 1);
        assertTok(tokens.get(2), TokenType.OPERATOR, "*", 2, 2);
    }

    private static void single(final TokenType type, final String sql) {
        assertTok(Lexer.lex(sql).get(0), type, sql, 0, sql.length() - 1);
    }

    private static void assertTok(final Token token, final TokenType type, final String text, final int start, final int stop) {
        assertTok(token, type, text, start, stop, null);
    }

    private static void assertTok(final Token token, final TokenType type, final String text, final int start, final int stop, final Kw kw) {
        assertEquals(type, token.type(), () -> "type of " + token);
        assertEquals(text, token.text(), () -> "text of " + token);
        assertEquals(start, token.start(), () -> "start of " + token);
        assertEquals(stop, token.stop(), () -> "stop of " + token);
        assertEquals(kw, token.kw(), () -> "kw of " + token);
    }

    private static void assertEof(final List<Token> tokens, final int at) {
        final Token eof = tokens.get(tokens.size() - 1);
        assertEquals(TokenType.EOF, eof.type());
        assertEquals(at, eof.start());
        assertEquals(at, eof.stop());
    }
}
