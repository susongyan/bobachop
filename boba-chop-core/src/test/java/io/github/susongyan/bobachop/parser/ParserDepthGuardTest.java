package io.github.susongyan.bobachop.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.exception.ParseException;

/**
 * 嵌套深度护栏边界（F3 / WHITELIST §2 深度护栏）：护栏计<b>同时</b>深度（=栈占用，
 * 先后嵌套各自退栈不累计）；上限内合法、超限 ParseException（非 StackOverflowError）；
 * 表达式递归与查询递归共享预算，同栈并存时合计计量。
 */
final class ParserDepthGuardTest {

    private static String repeat(final String s, final int n) {
        final StringBuilder out = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            out.append(s);
        }
        return out.toString();
    }

    @Test
    void 深括号_上限内接受_超限ParseException() {
        assertDoesNotThrow(() -> BobaChop.parse("SELECT " + repeat("(", 240) + "1" + repeat(")", 240)));
        assertThrows(ParseException.class,
                () -> BobaChop.parse("SELECT " + repeat("(", 300) + "1" + repeat(")", 300)));
    }

    @Test
    void 深一元链_超限ParseException() {
        // - - - …（空格分隔，非 -- 注释形态）
        assertThrows(ParseException.class,
                () -> BobaChop.parse("SELECT " + repeat("- ", 300) + "1"));
    }

    @Test
    void 深派生表_上限内接受_超限ParseException() {
        assertDoesNotThrow(() -> BobaChop.parse(
                "SELECT * FROM " + repeat("(SELECT * FROM ", 200) + "t x" + repeat(") d", 200)));
        assertThrows(ParseException.class, () -> BobaChop.parse(
                "SELECT * FROM " + repeat("(SELECT * FROM ", 300) + "t x" + repeat(") d", 300)));
    }

    @Test
    void 深子查询_超限ParseException() {
        assertThrows(ParseException.class,
                () -> BobaChop.parse("SELECT " + repeat("(SELECT (", 150) + "1" + repeat("))", 150)));
    }

    @Test
    void 同栈并存合计_先后嵌套不累计() {
        // 并存：派生 150 层 + 最内层括号 200 = 同时 350 > 256 → 拒绝
        assertThrows(ParseException.class, () -> BobaChop.parse(
                "SELECT * FROM " + repeat("(SELECT * FROM ", 150)
                        + "(SELECT " + repeat("(", 200) + "1" + repeat(")", 200) + ") d"
                        + repeat(") d", 150)));
        // 先后：括号 200 闭合后派生 200 层，同时深度各 ≤ ~202 → 合法
        assertDoesNotThrow(() -> BobaChop.parse(
                "SELECT " + repeat("(", 200) + "1" + repeat(")", 200)
                        + " FROM " + repeat("(SELECT * FROM ", 200) + "t x" + repeat(") d", 200)));
    }

    @Test
    void 迭代路径不受护栏约束() {
        // 长 AND 链与长 IN 清单走迭代循环（不递归入栈），20000 项合法接受
        final StringBuilder andChain = new StringBuilder("SELECT ");
        for (int i = 0; i < 20000; i++) {
            andChain.append("1 AND ");
        }
        andChain.append("1");
        assertDoesNotThrow(() -> BobaChop.parse(andChain.toString()));

        final StringBuilder inList = new StringBuilder("SELECT 1 IN (");
        for (int i = 0; i < 20000; i++) {
            inList.append("1, ");
        }
        inList.append("1)");
        assertDoesNotThrow(() -> BobaChop.parse(inList.toString()));
    }
}
