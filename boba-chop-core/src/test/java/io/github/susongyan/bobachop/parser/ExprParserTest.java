package io.github.susongyan.bobachop.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.ast.BetweenExpr;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.CaseExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.FunctionCall;
import io.github.susongyan.bobachop.ast.InExpr;
import io.github.susongyan.bobachop.ast.IsNullExpr;
import io.github.susongyan.bobachop.ast.LikeExpr;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.LiteralKind;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.Star;
import io.github.susongyan.bobachop.ast.UnaryExpr;
import io.github.susongyan.bobachop.ast.WhenClause;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;

/**
 * 表达式黄金用例（DESIGN T6 DoD）：优先级/结合性/全部特判/白名单外拒绝。
 */
class ExprParserTest {

    @Test
    void multiplicationBindsTighterThanAddition() {
        final BinaryExpr top = parseBinary("a + b * c");
        assertEquals("+", top.operator());
        assertEquals(0, top.start());
        assertEquals(8, top.stop());
        assertEquals("a", name(top.left()));
        final BinaryExpr mul = parseBinary(top.right());
        assertEquals("*", mul.operator());
        assertEquals("b", name(mul.left()));
        assertEquals("c", name(mul.right()));
    }

    @Test
    void parensOverridePrecedence() {
        final BinaryExpr top = parseBinary("(a + b) * c");
        assertEquals("*", top.operator());
        // 括号分组透明：节点从内层表达式起算
        assertEquals(1, top.start());
        final BinaryExpr sum = parseBinary(top.left());
        assertEquals("+", sum.operator());
        assertEquals(1, sum.start());
        assertEquals(5, sum.stop());
    }

    @Test
    void binaryOperatorsLeftAssociate() {
        final BinaryExpr top = parseBinary("a - b - c");
        assertEquals("-", top.operator());
        assertInstanceOf(BinaryExpr.class, top.left());
        assertEquals("c", name(top.right()));
    }

    @Test
    void unaryMinusBindsTighterThanMultiplication() {
        final BinaryExpr top = parseBinary("-a * b");
        assertEquals("*", top.operator());
        final UnaryExpr neg = (UnaryExpr) top.left();
        assertEquals("-", neg.operator());
        assertEquals(0, neg.start());
        assertEquals(1, neg.stop());
        assertEquals("a", name(neg.operand()));
    }

    @Test
    void notIsLooserThanComparison() {
        final UnaryExpr not = (UnaryExpr) ExprParser.parse("NOT a = b");
        assertEquals("NOT", not.operator());
        assertEquals(0, not.start());
        final BinaryExpr eq = parseBinary(not.operand());
        assertEquals("=", eq.operator());
    }

    @Test
    void logicPrecedenceOrBelowAnd() {
        final BinaryExpr or = parseBinary("a = 1 AND b <> 2 OR c");
        assertEquals("OR", or.operator());
        final BinaryExpr and = parseBinary(or.left());
        assertEquals("AND", and.operator());
        assertEquals("c", name(or.right()));
    }

    @Test
    void betweenSwallowsItsOwnAnd() {
        final BetweenExpr between = (BetweenExpr) ExprParser.parse("a BETWEEN 1 AND 10");
        assertEquals(0, between.start());
        assertEquals(17, between.stop());
        assertEquals("1", ((Literal) between.low()).text());
        assertEquals("10", ((Literal) between.high()).text());
        // BETWEEN 之后的逻辑 AND 属于外层
        final BinaryExpr and = parseBinary("a BETWEEN 1 AND 10 AND b = 2");
        assertEquals("AND", and.operator());
        assertInstanceOf(BetweenExpr.class, and.left());
        final BetweenExpr notBetween = (BetweenExpr) ExprParser.parse("a NOT BETWEEN 1 AND 2");
        assertTrue(notBetween.not());
    }

    @Test
    void isNullPredicate() {
        IsNullExpr is = (IsNullExpr) ExprParser.parse("a IS NULL");
        assertEquals("a", name(is.target()));
        is = (IsNullExpr) ExprParser.parse("a IS NOT NULL");
        assertTrue(is.not());
        assertThrows(UnsupportedSyntaxException.class, () -> ExprParser.parse("a IS TRUE"));
    }

    @Test
    void likePredicateWithEscape() {
        LikeExpr like = (LikeExpr) ExprParser.parse("a LIKE 'x%'");
        assertEquals("'x%'", ((Literal) like.pattern()).text());
        assertNull(like.escape());
        like = (LikeExpr) ExprParser.parse("a NOT LIKE 'x%' ESCAPE '\\\\'");
        assertTrue(like.not());
        assertEquals("'\\\\'", ((Literal) like.escape()).text());
    }

    @Test
    void inListVersusInSubquery() {
        final InExpr in = (InExpr) ExprParser.parse("a IN (1, 2, ?)");
        assertNull(in.query());
        assertEquals(3, in.values().size());
        final ParameterMarker marker = (ParameterMarker) in.values().get(2);
        assertEquals(0, marker.index());
        assertTrue(((InExpr) ExprParser.parse("a NOT IN (1)")).not());
        assertThrows(UnsupportedSyntaxException.class, () -> ExprParser.parse("a IN (SELECT id FROM t)"));
        assertThrows(ParseException.class, () -> ExprParser.parse("a IN ()"));
    }

    @Test
    void caseSearchedAndSimple() {
        final CaseExpr searched = (CaseExpr) ExprParser.parse("CASE WHEN a = 1 THEN 'x' ELSE 'y' END");
        assertNull(searched.operand());
        assertEquals(1, searched.whens().size());
        assertEquals("'y'", ((Literal) searched.elseResult()).text());
        final WhenClause when = searched.whens().get(0);
        assertInstanceOf(BinaryExpr.class, when.condition());
        assertEquals("'x'", ((Literal) when.result()).text());
        final CaseExpr simple = (CaseExpr) ExprParser.parse("CASE a WHEN 1 THEN 2 END");
        assertEquals("a", name(simple.operand()));
        assertNull(simple.elseResult());
    }

    @Test
    void functionCalls() {
        final FunctionCall call = (FunctionCall) ExprParser.parse("f(a, b)");
        assertEquals("f", call.name());
        assertEquals(2, call.arguments().size());
        final FunctionCall countStar = (FunctionCall) ExprParser.parse("COUNT(*)");
        assertInstanceOf(Star.class, countStar.arguments().get(0));
        final FunctionCall distinct = (FunctionCall) ExprParser.parse("COUNT(DISTINCT a)");
        assertTrue(distinct.distinct());
        assertEquals("a", name(distinct.arguments().get(0)));
    }

    @Test
    void qualifiedNamesKeepSourceForm() {
        assertEquals("t", ((ColumnRef) ExprParser.parse("t.c")).qualifier());
        assertEquals("c", ((ColumnRef) ExprParser.parse("t.c")).name());
        assertEquals("db.t", ((ColumnRef) ExprParser.parse("db.t.c")).qualifier());
        // 源形态：多段限定名内部空格保留（qualifier = 末段名之前的整段）
        assertEquals("db . t", ((ColumnRef) ExprParser.parse("db . t . c")).qualifier());
        assertEquals("c", ((ColumnRef) ExprParser.parse("db . t . c")).name());
        // 反引号包裹段保原文
        final ColumnRef quoted = (ColumnRef) ExprParser.parse("`t`.`c`");
        assertEquals("`t`", quoted.qualifier());
        assertEquals("`c`", quoted.name());
        final Star qualifiedStar = (Star) ExprParser.parse("t.*");
        assertEquals("t", qualifiedStar.qualifier());
    }

    @Test
    void parameterMarkersIndexByVisitOrder() {
        final BinaryExpr sum = parseBinary("? + ?");
        assertEquals(0, ((ParameterMarker) sum.left()).index());
        assertEquals(1, ((ParameterMarker) sum.right()).index());
    }

    @Test
    void literalKinds() {
        assertEquals(LiteralKind.NULL, ((Literal) ExprParser.parse("NULL")).kind());
        assertEquals(LiteralKind.TRUE, ((Literal) ExprParser.parse("TRUE")).kind());
        assertEquals(LiteralKind.HEX, ((Literal) ExprParser.parse("0x1F")).kind());
        assertEquals(LiteralKind.BIT, ((Literal) ExprParser.parse("0b01")).kind());
        final Literal temporal = (Literal) ExprParser.parse("DATE '2020-01-01'");
        assertEquals(LiteralKind.TEMPORAL, temporal.kind());
        assertEquals("DATE '2020-01-01'", temporal.text());
    }

    @Test
    void mysqlSpecificOperatorPrecedence() {
        // || 最松（默认 sql_mode 下为逻辑 OR 级）
        final BinaryExpr or = parseBinary("a || b AND c");
        assertEquals("||", or.operator());
        assertInstanceOf(BinaryExpr.class, or.right());
        // ^ 比乘除紧
        final BinaryExpr mul = parseBinary("a ^ b * c");
        assertEquals("*", mul.operator());
        assertInstanceOf(BinaryExpr.class, mul.left());
        assertEquals("^", parseBinary(mul.left()).operator());
        // 关键字运算符保原文
        assertEquals("DIV", parseBinary("1 DIV 2").operator());
        assertEquals("MOD", parseBinary("1 MOD 2").operator());
        assertEquals("REGEXP", parseBinary("a REGEXP 'x'").operator());
    }

    @Test
    void projectionListDrivesMultipleExpressions() {
        final List<Expr> items = ExprParser.parseList("a + b, COUNT(*), t.c");
        assertEquals(3, items.size());
        assertInstanceOf(BinaryExpr.class, items.get(0));
        assertInstanceOf(FunctionCall.class, items.get(1));
        assertInstanceOf(ColumnRef.class, items.get(2));
    }

    @Test
    void whitelistRejections() {
        assertThrows(UnsupportedSyntaxException.class, () -> ExprParser.parse("(a, b)"));
        assertThrows(UnsupportedSyntaxException.class, () -> ExprParser.parse("CAST(a AS INT)"));
        assertThrows(UnsupportedSyntaxException.class, () -> ExprParser.parse("EXISTS (SELECT 1)"));
        assertThrows(UnsupportedSyntaxException.class, () -> ExprParser.parse("CONVERT(a, CHAR)"));
    }

    @Test
    void syntaxErrors() {
        assertThrows(ParseException.class, () -> ExprParser.parse("a b"));
        assertThrows(ParseException.class, () -> ExprParser.parse("a AND"));
        assertThrows(ParseException.class, () -> ExprParser.parse("SELECT"));
        assertThrows(ParseException.class, () -> ExprParser.parse("INTERVAL 1 DAY"));
        assertThrows(ParseException.class, () -> ExprParser.parse("(a + b"));
        assertThrows(ParseException.class, () -> ExprParser.parse("a IS NOT 1"));
    }

    private static BinaryExpr parseBinary(final String sql) {
        return parseBinary(ExprParser.parse(sql));
    }

    private static BinaryExpr parseBinary(final Expr expr) {
        return assertInstanceOf(BinaryExpr.class, expr);
    }

    private static String name(final Expr expr) {
        return ((ColumnRef) expr).name();
    }
}
