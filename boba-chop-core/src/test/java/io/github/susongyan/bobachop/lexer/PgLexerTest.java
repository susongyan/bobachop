package io.github.susongyan.bobachop.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.LexException;

/**
 * PostgreSQL 词法黄金用例（T10 DoD）：ANSI 双引号标识符、E''/B''/X'' 前缀与转义语义、
 * -- 恒注释、无 # 注释、嵌套块注释、无反引号、dollar-quoted 拒绝、关键字表成员资格。
 */
class PgLexerTest {

    private static List<Token> pgLex(final String sql) {
        return Lexer.lex(sql, DialectProfile.POSTGRESQL.lexerConfig());
    }

    private static void assertTok(final Token token, final TokenType type, final String text,
                                  final int start, final int stop, final Kw kw) {
        assertEquals(type, token.type(), "type");
        assertEquals(text, token.text(), "text");
        assertEquals(start, token.start(), "start");
        assertEquals(stop, token.stop(), "stop");
        assertEquals(kw, token.kw(), "kw");
    }

    @Test
    void ansiDoubleQuoteIsQuotedIdentifierNeverKeyword() {
        assertTok(pgLex("\"select\"").get(0), TokenType.QUOTED_IDENTIFIER, "\"select\"", 0, 7, null);
        // 双引号双写转义
        assertTok(pgLex("\"a\"\"b\"").get(0), TokenType.QUOTED_IDENTIFIER, "\"a\"\"b\"", 0, 5, null);
        assertThrows(LexException.class, () -> pgLex("\"abc"));
    }

    @Test
    void backtickIsIllegalUnderPg() {
        final LexException e = assertThrows(LexException.class, () -> pgLex("SELECT `t`"));
        assertEquals(7, e.offset());
    }

    @Test
    void stringPrefixesEBXFoldIntoString() {
        assertTok(pgLex("E'\\''").get(0), TokenType.STRING, "E'\\''", 0, 4, null);
        assertTok(pgLex("B'01'").get(0), TokenType.STRING, "B'01'", 0, 4, null);
        assertTok(pgLex("X'4D'").get(0), TokenType.STRING, "X'4D'", 0, 4, null);
        // N 前缀非 PG 形态：拆为标识符 + 字符串（语法层拒绝）
        assertEquals(TokenType.IDENTIFIER, pgLex("N'x'").get(0).type());
        assertEquals(TokenType.STRING, pgLex("N'x'").get(1).type());
    }

    @Test
    void standardStringsDoNotBackslashEscapeButEPrefixDoes() {
        // 标准字符串：反斜杠为普通字符，'\'' 后的 '' 为双写转义——整体闭合于下一个单引号
        assertTok(pgLex("'a\\''b'").get(0), TokenType.STRING, "'a\\''b'", 0, 6, null);
        // E''：反斜杠转义收尾
        assertTok(pgLex("E'a\\'b'").get(0), TokenType.STRING, "E'a\\'b'", 0, 6, null);
        // 标准：'a\' 即完整字符串（反斜杠无转义力，无需配对）
        assertTok(pgLex("'a\\'").get(0), TokenType.STRING, "'a\\'", 0, 3, null);
        final LexException e = assertThrows(LexException.class, () -> pgLex("E'a\\'"));
        assertEquals(0, e.offset());
    }

    @Test
    void doubleDashAlwaysStartsLineComment() {
        // PG 无 "-- 后须空白" 规则：--2 整体为注释（MySQL 中为两个减号）
        final List<Token> tokens = pgLex("SELECT 1 --2\n, 3");
        assertEquals(5, tokens.size());
        assertEquals(Kw.SELECT, tokens.get(0).kw());
        assertTok(tokens.get(3), TokenType.NUMBER, "3", 15, 15, null);
    }

    @Test
    void hashIsNotACommentUnderPg() {
        // # 非注释起点 = 非法字符
        assertThrows(LexException.class, () -> pgLex("SELECT 1 # c"));
    }

    @Test
    void blockCommentsNest() {
        final List<Token> tokens = pgLex("SELECT /* 外 /* 内 */ 还在注释 */ 1");
        assertTok(tokens.get(1), TokenType.NUMBER, "1", 28, 28, null);
        // 嵌套未配对即未闭合
        assertThrows(LexException.class, () -> pgLex("/* /* 1 */"));
    }

    @Test
    void dollarQuotedRejectedAsIllegalChar() {
        // $$...$$ 以 $ 起始：PG 标识符不可 $ 开头，非法字符显式拒绝（v1 白名单外）
        final LexException e = assertThrows(LexException.class, () -> pgLex("SELECT $$body$$"));
        assertEquals(7, e.offset());
    }

    @Test
    void pgKeywordMembershipAndReservedness() {
        assertEquals(Kw.OFFSET, pgLex("OFFSET").get(0).kw());
        assertEquals(Kw.FETCH, pgLex("fetch").get(0).kw());
        assertEquals(Kw.RETURNING, pgLex("RETURNING").get(0).kw());
        // MySQL 专有词在 PG 为普通标识符；conflict 为 PG 语法键控非保留字（ON CONFLICT 前置归类用）
        assertNull(pgLex("straight_join").get(0).kw());
        assertNull(pgLex("ilike").get(0).kw());
        assertEquals(Kw.CONFLICT, pgLex("conflict").get(0).kw());
        // 保留字裁决：PG 中 OFFSET 保留（MySQL 非保留），JOIN 两方言皆可作标识符
        assertTrue(DialectProfile.POSTGRESQL.reserved(Kw.OFFSET));
        assertFalse(DialectProfile.POSTGRESQL.reserved(Kw.JOIN));
        assertTrue(DialectProfile.MYSQL.reserved(Kw.STRAIGHT_JOIN));
        assertFalse(DialectProfile.POSTGRESQL.reserved(Kw.STRAIGHT_JOIN));
    }
}
