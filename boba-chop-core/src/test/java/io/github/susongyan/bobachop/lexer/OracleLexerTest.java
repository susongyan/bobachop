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
 * Oracle 词法黄金用例（T11 DoD）：ANSI 双引号标识符、N'' 前缀（无 E/B/X）、标准字符串
 * 无反斜杠转义、-- 恒注释、无 # 注释、嵌套块注释、无反引号、q-字符串拆解、关键字表成员资格。
 */
class OracleLexerTest {

    private static List<Token> oraLex(final String sql) {
        return Lexer.lex(sql, DialectProfile.ORACLE.lexerConfig());
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
        assertTok(oraLex("\"select\"").get(0), TokenType.QUOTED_IDENTIFIER, "\"select\"", 0, 7, null);
        assertTok(oraLex("\"a\"\"b\"").get(0), TokenType.QUOTED_IDENTIFIER, "\"a\"\"b\"", 0, 5, null);
        assertThrows(LexException.class, () -> oraLex("\"abc"));
    }

    @Test
    void backtickIsIllegalUnderOracle() {
        final LexException e = assertThrows(LexException.class, () -> oraLex("SELECT `t`"));
        assertEquals(7, e.offset());
    }

    @Test
    void onlyNPrefixFoldsIntoString() {
        assertTok(oraLex("N'x'").get(0), TokenType.STRING, "N'x'", 0, 3, null);
        // E/B/X 非 Oracle 前缀：拆为标识符 + 字符串（语法层拒绝）
        assertEquals(TokenType.IDENTIFIER, oraLex("E'x'").get(0).type());
        assertEquals(TokenType.STRING, oraLex("E'x'").get(1).type());
        // n 开头普通标识符不受前缀折叠影响
        assertEquals(TokenType.IDENTIFIER, oraLex("note").get(0).type());
    }

    @Test
    void standardStringsDoNotBackslashEscape() {
        // 反斜杠为普通字符，'a\' 即完整字符串（无 E 前缀形态可对照，Oracle 全量标准语义）
        assertTok(oraLex("'a\\'").get(0), TokenType.STRING, "'a\\'", 0, 3, null);
        assertTok(oraLex("'a\\''b'").get(0), TokenType.STRING, "'a\\''b'", 0, 6, null);
    }

    @Test
    void doubleDashAlwaysStartsLineComment() {
        final List<Token> tokens = oraLex("SELECT 1 --2\n, 3");
        assertEquals(5, tokens.size());
        assertEquals(Kw.SELECT, tokens.get(0).kw());
        assertTok(tokens.get(3), TokenType.NUMBER, "3", 15, 15, null);
    }

    @Test
    void hashIsNotACommentUnderOracle() {
        assertThrows(LexException.class, () -> oraLex("SELECT 1 # c"));
    }

    @Test
    void blockCommentsNest() {
        final List<Token> tokens = oraLex("SELECT /* 外 /* 内 */ 还在注释 */ 1");
        assertTok(tokens.get(1), TokenType.NUMBER, "1", 28, 28, null);
        assertThrows(LexException.class, () -> oraLex("/* /* 1 */"));
    }

    @Test
    void qStringSplitsIntoIdentifierAndString() {
        // q'[...]'：q 为普通标识符，'[...]' 为普通字符串——语法层落 ParseException（白名单外窄口）
        final List<Token> tokens = oraLex("q'[a b]'");
        assertEquals(3, tokens.size());
        assertTok(tokens.get(0), TokenType.IDENTIFIER, "q", 0, 0, null);
        assertTok(tokens.get(1), TokenType.STRING, "'[a b]'", 1, 7, null);
        assertEquals(TokenType.EOF, tokens.get(2).type());
    }

    @Test
    void oracleKeywordMembershipAndReservedness() {
        assertEquals(Kw.MINUS, oraLex("minus").get(0).kw());
        assertEquals(Kw.ROWNUM, oraLex("rownum").get(0).kw());
        assertEquals(Kw.MERGE, oraLex("merge").get(0).kw());
        assertEquals(Kw.SYSDATE, oraLex("sysdate").get(0).kw());
        // LIMIT/DUAL 非本方言词（DUAL 在 Oracle 为普通表名）；MySQL 专有词同
        assertNull(oraLex("limit").get(0).kw());
        assertNull(oraLex("dual").get(0).kw());
        assertNull(oraLex("straight_join").get(0).kw());
        // 保留字裁决：ROWNUM/MINUS/USER 保留，MERGE/JOIN 非保留（可作标识符）
        assertTrue(DialectProfile.ORACLE.reserved(Kw.ROWNUM));
        assertTrue(DialectProfile.ORACLE.reserved(Kw.MINUS));
        assertTrue(DialectProfile.ORACLE.reserved(Kw.USER));
        assertFalse(DialectProfile.ORACLE.reserved(Kw.MERGE));
        assertFalse(DialectProfile.ORACLE.reserved(Kw.JOIN));
        assertFalse(DialectProfile.ORACLE.reserved(Kw.DUAL));
        // MySQL 回归锚：DUAL 自 T11 起为语法键控非保留（FROM DUAL 虚表目标，不再误拒）
        assertFalse(DialectProfile.MYSQL.reserved(Kw.DUAL));
    }
}
