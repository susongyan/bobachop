package io.github.susongyan.bobachop.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;

/**
 * F4 裁决台账的执行锚：每条已裁决挂账项的裁定结果以黄金用例钉死——裁决改一次，这里红一次。
 * 对应 LEDGER.md L1（|| 方言优先级）/ L4（REGEXP 族词表门禁维持）/ L5（RETURNING INTO
 * 分类修复）/ L6（FETCH PERCENT 与 WITH TIES 分类修复）。
 */
class DialectAdjudicationTest {

    // ---------- L1：|| 方言二义（PG"其他运算符"层 / Oracle 与 + 同层 / MySQL 逻辑 OR） ----------

    @Test
    void pipesConcatBindsAboveComparisonInPostgres() {
        // PG 语法书：|| 归"其他运算符"层，高于比较 → (a || b) = c
        final BinaryExpr top = firstBinary("SELECT a || b = c FROM t", DialectProfile.POSTGRESQL);
        assertEquals("=", top.operator());
        assertEquals("||", binary(top.left()).operator());
        assertEquals("c", name(top.right()));
    }

    @Test
    void pipesConcatBindsAboveComparisonInOracle() {
        final BinaryExpr top = firstBinary("SELECT a || b = c FROM t", DialectProfile.ORACLE);
        assertEquals("=", top.operator());
        assertEquals("||", binary(top.left()).operator());
    }

    @Test
    void pipesIsLogicalOrInMySql() {
        // MySQL 默认 || = OR（PIPES_AS_CONCAT 模式白名单外）→ a || (b = c)
        final BinaryExpr top = firstBinary("SELECT a || b = c FROM t", DialectProfile.MYSQL);
        assertEquals("||", top.operator());
        assertEquals("a", name(top.left()));
        assertEquals("=", binary(top.right()).operator());
    }

    @Test
    void pipesConcatBelowArithmeticInPostgres() {
        // PG：加减高于"其他运算符"层 → a || (b + c)
        final BinaryExpr top = firstBinary("SELECT a || b + c FROM t", DialectProfile.POSTGRESQL);
        assertEquals("||", top.operator());
        assertEquals("+", binary(top.right()).operator());
    }

    @Test
    void pipesConcatLeftAssociates() {
        final BinaryExpr top = firstBinary("SELECT a || b || c FROM t", DialectProfile.POSTGRESQL);
        assertEquals("||", top.operator());
        assertEquals("||", binary(top.left()).operator());
        assertEquals("c", name(top.right()));
    }

    // ---------- L5：Oracle RETURNING INTO 分类修复（ParseException → Unsupported） ----------

    @Test
    void oracleReturningIntoPreclassified() {
        final UnsupportedSyntaxException ex = assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("UPDATE t SET x = 1 RETURNING y INTO o", DialectProfile.ORACLE));
        assertTrue(ex.getMessage().contains("RETURNING INTO"), ex.getMessage());
        assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("DELETE FROM t RETURNING y INTO o", DialectProfile.ORACLE));
        assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("INSERT INTO t (x) VALUES (1) RETURNING y INTO o", DialectProfile.ORACLE));
    }

    @Test
    void oracleReturningUsableAsIdentifier() {
        // RETURNING 入 Oracle 非保留表：标识符用法不受门禁波及
        final SelectStatement select = (SelectStatement) BobaChop.parse("SELECT returning FROM t", DialectProfile.ORACLE);
        assertEquals("returning", ((ColumnRef) select.items().get(0).expr()).name());
    }

    @Test
    void mySqlReturningStaysParseError() {
        // MySQL 词表无 RETURNING：普通标识符，语句尾落"多余 token" ParseException（方言自限不变）
        assertThrows(ParseException.class, () -> BobaChop.parse("UPDATE t SET x = 1 RETURNING y"));
    }

    @Test
    void pgSelectTailReturningStaysParseError() {
        assertThrows(ParseException.class,
                () -> BobaChop.parse("SELECT 1 FROM t RETURNING x", DialectProfile.POSTGRESQL));
    }

    // ---------- L6：FETCH PERCENT / WITH TIES 分类修复（ParseException → Unsupported） ----------

    @Test
    void oracleFetchPercentPreclassified() {
        final UnsupportedSyntaxException ex = assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse(
                "SELECT 1 FROM t OFFSET 0 ROWS FETCH FIRST 10 PERCENT ROWS ONLY", DialectProfile.ORACLE));
        assertTrue(ex.getMessage().contains("PERCENT"), ex.getMessage());
    }

    @Test
    void oracleFetchWithTiesPreclassified() {
        final UnsupportedSyntaxException ex = assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse(
                "SELECT 1 FROM t OFFSET 0 ROWS FETCH FIRST 10 ROWS WITH TIES", DialectProfile.ORACLE));
        assertTrue(ex.getMessage().contains("WITH TIES"), ex.getMessage());
    }

    @Test
    void pgFetchWithTiesPreclassified() {
        // PG 13+ 合法语法（并列行取回），契约无锚 → Unsupported（非语法错误）
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse(
                "SELECT 1 FROM t OFFSET 0 ROWS FETCH FIRST 10 ROWS WITH TIES", DialectProfile.POSTGRESQL));
    }

    @Test
    void pgFetchPercentStaysParseError() {
        // PERCENT 非 PG 词：普通标识符卡在"期望 ROW/ROWS"位 → ParseException（PG 服务器同拒）
        assertThrows(ParseException.class, () -> BobaChop.parse(
                "SELECT 1 FROM t OFFSET 0 ROWS FETCH FIRST 10 PERCENT ROWS ONLY", DialectProfile.POSTGRESQL));
    }

    @Test
    void oraclePercentAndTiesUsableAsIdentifier() {
        final SelectStatement percent = (SelectStatement) BobaChop.parse("SELECT percent FROM t", DialectProfile.ORACLE);
        assertEquals("percent", ((ColumnRef) percent.items().get(0).expr()).name());
        final SelectStatement ties = (SelectStatement) BobaChop.parse("SELECT ties FROM t", DialectProfile.ORACLE);
        assertEquals("ties", ((ColumnRef) ties.items().get(0).expr()).name());
    }

    // ---------- L4：REGEXP 族词表门禁维持（回归锚，裁决=不改代码） ----------

    @Test
    void mySqlRegexpFamilyAccepted() {
        final BinaryExpr top = firstBinary("SELECT 'a' REGEXP 'b' FROM t", DialectProfile.MYSQL);
        assertEquals("REGEXP", top.operator());
    }

    @Test
    void regexpFamilyGatedByWordTableInPostgres() {
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1 REGEXP 2 FROM t", DialectProfile.POSTGRESQL));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1 RLIKE 2 FROM t", DialectProfile.POSTGRESQL));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1 XOR 1 FROM t", DialectProfile.POSTGRESQL));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1 DIV 2 FROM t", DialectProfile.POSTGRESQL));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1 MOD 2 FROM t", DialectProfile.POSTGRESQL));
    }

    @Test
    void regexpFamilyGatedByWordTableInOracle() {
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1 REGEXP 2 FROM t", DialectProfile.ORACLE));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1 XOR 1 FROM t", DialectProfile.ORACLE));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1 DIV 2 FROM t", DialectProfile.ORACLE));
    }

    // ---------- 助手 ----------

    private Statement parse(final String sql, final DialectProfile profile) {
        return BobaChop.parse(sql, profile);
    }

    private BinaryExpr firstBinary(final String sql, final DialectProfile profile) {
        final SelectStatement select = (SelectStatement) parse(sql, profile);
        return binary(select.items().get(0).expr());
    }

    private static BinaryExpr binary(final Expr expr) {
        return assertInstanceOf(BinaryExpr.class, expr);
    }

    private static String name(final Expr expr) {
        return ((ColumnRef) expr).name();
    }
}
