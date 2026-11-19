package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.LiteralKind;
import io.github.susongyan.bobachop.ast.SelectItem;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.Statement;

/**
 * {@link DiffHarness} 编排语义验证：成功对成功、同拒同收、单侧失败、异常兜底、渲染。
 */
class DiffHarnessTest {

    @Test
    void bothSuccessWithIdenticalTreesIsIdentical() {
        final DiffHarness harness = new DiffHarness(fixed("ref", ParseResult.success(select("1"))),
                fixed("bobachop", ParseResult.success(select("1"))));
        assertTrue(harness.diff("SELECT * FROM t WHERE id = 1").identical());
    }

    @Test
    void bothSuccessWithDifferentTreesCarriesAstDiffPath() {
        final DiffHarness harness = new DiffHarness(fixed("ref", ParseResult.success(select("1"))),
                fixed("bobachop", ParseResult.success(select("2"))));
        final DiffReport report = harness.diff("SELECT * FROM t WHERE id = ?");
        assertFalse(report.identical());
        assertEquals("where.right.text", report.differences().get(0).path());
    }

    @Test
    void bothRejectedCountsAsAgreement() {
        final DiffHarness harness = new DiffHarness(fixed("ref", ParseResult.failure("UnsupportedSyntaxException: 窗口函数")),
                fixed("bobachop", ParseResult.failure("UnsupportedSyntaxException: 窗口函数")));
        assertTrue(harness.diff("SELECT rank() OVER () FROM t").identical());
    }

    @Test
    void singleSidedFailureIsStructuralDifference() {
        final DiffHarness harness = new DiffHarness(fixed("ref", ParseResult.success(select("1"))),
                fixed("bobachop", ParseResult.failure("UnsupportedSyntaxException: CTE")));
        final DiffReport report = harness.diff("WITH c AS (SELECT 1) SELECT * FROM c");
        assertEquals(1, report.differences().size());
        assertEquals("(statement)", report.differences().get(0).path());
        assertTrue(report.differences().get(0).expected().startsWith("解析成功"));
        assertTrue(report.differences().get(0).actual().contains("UnsupportedSyntaxException"));
    }

    @Test
    void throwingExecutorIsCapturedAsFailure() {
        final DiffHarness harness = new DiffHarness(fixed("ref", ParseResult.success(select("1"))), throwing("bobachop"));
        final DiffReport report = harness.diff("SELECT 1");
        assertFalse(report.identical());
        assertTrue(report.differences().get(0).actual().contains("IllegalStateException: boom"));
    }

    @Test
    void diffAllKeepsPerInputOrderAndSql() {
        final DiffHarness harness = new DiffHarness(fixed("ref", ParseResult.success(select("1"))),
                fixed("bobachop", ParseResult.success(select("1"))));
        final List<DiffReport> reports = harness.diffAll(Arrays.asList("SELECT 1", "SELECT 2", "SELECT 3"));
        assertEquals(3, reports.size());
        assertEquals("SELECT 2", reports.get(1).sql());
    }

    @Test
    void renderSummarizesAndListsMismatches() {
        final DiffHarness harness = new DiffHarness(fixed("ref", ParseResult.success(select("1"))), dynamic("bobachop"));
        final String text = harness.render(harness.diffAll(Arrays.asList("SELECT a", "SELECT b", "SELECT c")));
        assertTrue(text.contains("3 条，一致 2，不一致 1"));
        assertTrue(text.contains("expected=ref, actual=bobachop"));
        assertTrue(text.contains("--- 不一致 #1 ---"));
        assertTrue(text.contains("SELECT b"));
    }

    private static Statement select(final String literalText) {
        return new SelectStatement(0, 40, false,
                Collections.singletonList(new SelectItem(7, 9, new ColumnRef(7, 9, null, "id"), null)),
                null,
                new BinaryExpr(10, 24, "=", new ColumnRef(10, 19, null, "order_id"),
                        new Literal(21, 22, LiteralKind.NUMBER, literalText)),
                null, null, null, null);
    }

    /** 固定观测值执行器：任何输入返回同一结果。 */
    private static ParseExecutor fixed(final String name, final ParseResult result) {
        return new ParseExecutor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ParseResult parse(final String sql) {
                return result;
            }
        };
    }

    /** 按 SQL 尾字符判定成败的执行器：仅 "SELECT b" 与期望侧不一致。 */
    private static ParseExecutor dynamic(final String name) {
        return new ParseExecutor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ParseResult parse(final String sql) {
                return "SELECT b".equals(sql) ? ParseResult.success(select("2")) : ParseResult.success(select("1"));
            }
        };
    }

    private static ParseExecutor throwing(final String name) {
        return new ParseExecutor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ParseResult parse(final String sql) {
                throw new IllegalStateException("boom");
            }
        };
    }
}
