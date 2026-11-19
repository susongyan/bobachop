package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.LiteralKind;
import io.github.susongyan.bobachop.ast.SelectItem;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;

/**
 * F1 校验器黄金用例：正向（三方言真实解析全过六条）+ 负向（手造违规 AST，
 * 每条规则必须能着火——校验器自身的灵敏度门禁，防止不变量静默失效）。
 */
final class SpanInvariantsTest {

    @Test
    void 真实解析六条全过_三方言() {
        final String[][] cases = {
            {"M", "  SELECT /* c1 */ a, t.* -- x\n FROM db.t AS q WHERE a BETWEEN 1 AND 2 LIMIT 5, 10 ; "},
            {"M", "SELECT CASE a WHEN 1 THEN 'x' ELSE 'y' END, NOT COUNT(DISTINCT a) FROM t1 UNION ALL SELECT * FROM t2"},
            {"P", "SELECT E'a\\'b', x FROM \"Ta Ble\" AS q LIMIT ? OFFSET 2 ROWS"},
            {"P", "SELECT a FROM t OFFSET 2 ROWS FETCH NEXT 3 ROWS ONLY"},
            {"O", "SELECT a, seq.NEXTVAL FROM t OFFSET ? ROWS FETCH FIRST 3 ROWS ONLY"},
        };
        for (final String[] c : cases) {
            final DialectProfile profile = "M".equals(c[0]) ? DialectProfile.MYSQL
                    : "P".equals(c[0]) ? DialectProfile.POSTGRESQL : DialectProfile.ORACLE;
            final Statement stmt = BobaChop.parse(c[1], profile);
            final List<String> violations = SpanInvariants.check(stmt, c[1], profile);
            assertTrue(violations.isEmpty(), c[1] + "\n  " + String.join("\n  ", violations));
        }
    }

    @Test
    void 规则r1_span倒挂必着火() {
        // Literal[3,2]：stop < start
        final List<String> v = check("SELECT 1", root(0, 7, item(3, 2, leaf(3, 2, "1"))));
        assertRule(v, "R1");
    }

    @Test
    void 规则r2_切进token中间必着火() {
        // "SELECT 1"：token 终点只有 5 与 7；item[6,7] 的 6 不是任何 token 起点
        final List<String> v = check("SELECT 1", root(0, 7, item(6, 7, leaf(7, 7, "1"))));
        assertRule(v, "R2");
    }

    @Test
    void 规则r3_子超父必着火() {
        // "SELECT 11"：item[0,5] 对齐（SELECT 终点 5），Literal[7,8] 对齐但在父外
        final List<String> v = check("SELECT 11", root(0, 8, item(0, 5, leaf(7, 8, "11"))));
        assertRule(v, "R3");
    }

    @Test
    void 规则r4_兄弟重叠必着火() {
        // "SELECT 1, 2"：两个 item 都占 [7,7]
        final List<String> v = check("SELECT 1, 2", root(0, 10,
                item(7, 7, leaf(7, 7, "1")), item(7, 7, leaf(7, 7, "2"))));
        assertRule(v, "R4");
    }

    @Test
    void 规则r5_根未覆盖全句必着火() {
        // "SELECT 1"：根止于 [0,5]（SELECT 本身），未覆盖到末 token [0,7]
        final List<String> v = check("SELECT 1", root(0, 5, item(7, 7, leaf(7, 7, "1"))));
        assertRule(v, "R5");
    }

    @Test
    void 规则r6_叶子存文不符必着火() {
        // 存文 "1" 而切片是 "2"
        final List<String> v = check("SELECT 2", root(0, 7, item(7, 7, leaf(7, 7, "1"))));
        assertRule(v, "R6");
    }

    // ---------- 手造 AST 工具 ----------

    private static List<String> check(final String sql, final Statement stmt) {
        return SpanInvariants.check(stmt, sql, DialectProfile.MYSQL);
    }

    private static Statement root(final int start, final int stop, final SelectItem... items) {
        return new SelectStatement(start, stop, false, Arrays.asList(items), null, null, null, null, null, null);
    }

    private static SelectItem item(final int start, final int stop, final Expr expr) {
        return new SelectItem(start, stop, expr, null);
    }

    private static Expr leaf(final int start, final int stop, final String text) {
        return new Literal(start, stop, LiteralKind.NUMBER, text);
    }

    private static void assertRule(final List<String> violations, final String rule) {
        assertTrue(violations.stream().anyMatch(v -> v.startsWith(rule + " ")),
                rule + " 未着火，违规清单: " + violations);
    }
}
