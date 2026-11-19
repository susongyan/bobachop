package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.LiteralKind;
import io.github.susongyan.bobachop.ast.SelectItem;
import io.github.susongyan.bobachop.ast.SelectStatement;

/**
 * {@link AstDiff} 深度比较规则验证：偏移、值字段、集合、null、类型、枚举。
 */
class AstDiffTest {

    @Test
    void identicalTreesProduceNoDifferences() {
        assertTrue(AstDiff.compare(select(false, eq("1"), null), select(false, eq("1"), null)).isEmpty());
    }

    @Test
    void reportsPathOfDifferingLiteralText() {
        final List<DiffDifference> diffs = AstDiff.compare(select(false, eq("1"), null), select(false, eq("2"), null));
        assertEquals(1, diffs.size());
        assertEquals("where.right.text", diffs.get(0).path());
        assertEquals("'1'", diffs.get(0).expected());
        assertEquals("'2'", diffs.get(0).actual());
    }

    @Test
    void comparesPositionOffsets() {
        final Expr narrow = new BinaryExpr(10, 24, "=", col(10, 19), lit(21, 22, "1"));
        final Expr wide = new BinaryExpr(10, 25, "=", col(10, 19), lit(21, 22, "1"));
        final List<DiffDifference> diffs = AstDiff.compare(select(false, narrow, null), select(false, wide, null));
        assertEquals(1, diffs.size());
        assertEquals("where.stop", diffs.get(0).path());
        assertEquals("24", diffs.get(0).expected());
        assertEquals("25", diffs.get(0).actual());
    }

    @Test
    void reportsListSizeMismatchWithoutDiving() {
        final List<DiffDifference> diffs = AstDiff.compare(select(false, null, Arrays.asList(lit(0, 1, "1"), lit(3, 4, "2"))),
                select(false, null, Collections.singletonList(lit(0, 1, "1"))));
        assertEquals(1, diffs.size());
        assertEquals("groupBy.size", diffs.get(0).path());
        assertEquals("2", diffs.get(0).expected());
        assertEquals("1", diffs.get(0).actual());
    }

    @Test
    void comparesListElementsWithIndex() {
        final List<DiffDifference> diffs = AstDiff.compare(select(false, null, Arrays.asList(lit(0, 1, "1"), lit(3, 4, "2"))),
                select(false, null, Arrays.asList(lit(0, 1, "1"), lit(3, 4, "3"))));
        assertEquals(1, diffs.size());
        assertEquals("groupBy[1].text", diffs.get(0).path());
    }

    @Test
    void reportsNullVersusPresentNode() {
        final List<DiffDifference> diffs = AstDiff.compare(select(false, eq("1"), null), select(false, null, null));
        assertEquals(1, diffs.size());
        assertEquals("where", diffs.get(0).path());
        assertEquals("BinaryExpr", diffs.get(0).expected());
        assertEquals("null", diffs.get(0).actual());
    }

    @Test
    void reportsNodeTypeMismatch() {
        final List<DiffDifference> diffs = AstDiff.compare(select(false, eq("1"), null),
                select(false, lit(10, 12, "1"), null));
        assertEquals(1, diffs.size());
        assertEquals("where", diffs.get(0).path());
        assertEquals("BinaryExpr", diffs.get(0).expected());
        assertEquals("Literal", diffs.get(0).actual());
    }

    @Test
    void comparesEnumAndPrimitiveFlag() {
        final List<DiffDifference> kindDiffs = AstDiff.compare(
                select(false, new BinaryExpr(10, 24, "=", col(10, 19), lit(21, 22, LiteralKind.NUMBER, "1")), null),
                select(false, new BinaryExpr(10, 24, "=", col(10, 19), lit(21, 22, LiteralKind.STRING, "1")), null));
        assertEquals(1, kindDiffs.size());
        assertEquals("where.right.kind", kindDiffs.get(0).path());
        assertEquals("NUMBER", kindDiffs.get(0).expected());
        assertEquals("STRING", kindDiffs.get(0).actual());

        final List<DiffDifference> flagDiffs = AstDiff.compare(select(false, null, null), select(true, null, null));
        assertEquals(1, flagDiffs.size());
        assertEquals("distinct", flagDiffs.get(0).path());
        assertEquals("false", flagDiffs.get(0).expected());
        assertEquals("true", flagDiffs.get(0).actual());
    }

    private static SelectStatement select(final boolean distinct, final Expr where, final List<Expr> groupBy) {
        return new SelectStatement(0, 40, distinct,
                Collections.singletonList(new SelectItem(7, 9, new ColumnRef(7, 9, null, "id"), "uid")),
                null, where, groupBy, null, null, null);
    }

    private static BinaryExpr eq(final String literalText) {
        return new BinaryExpr(10, 24, "=", col(10, 19), lit(21, 22, literalText));
    }

    private static ColumnRef col(final int start, final int stop) {
        return new ColumnRef(start, stop, null, "order_id");
    }

    private static Literal lit(final int start, final int stop, final String text) {
        return new Literal(start, stop, LiteralKind.NUMBER, text);
    }

    private static Literal lit(final int start, final int stop, final LiteralKind kind, final String text) {
        return new Literal(start, stop, kind, text);
    }
}
