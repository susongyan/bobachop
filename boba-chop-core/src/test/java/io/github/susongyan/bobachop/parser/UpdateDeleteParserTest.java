package io.github.susongyan.bobachop.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Assignment;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.DeleteStatement;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.SubqueryExpr;
import io.github.susongyan.bobachop.ast.UpdateStatement;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;

/**
 * UpdateParser/DeleteParser 黄金用例（T8）：赋值清单/别名/子查询右值/参数访问序/
 * 多表与尾部 ORDER BY/LIMIT 的白名单外分类。
 */
final class UpdateDeleteParserTest {

    @Test
    void UPDATE赋值与WHERE() {
        final UpdateStatement update = (UpdateStatement) BobaChop.parse(
                "UPDATE t_order SET status = 'DONE', retry = retry + 1 WHERE id = 42");
        assertEquals("t_order", update.table().name());
        assertEquals(2, update.assignments().size());
        assertEquals("status", update.assignments().get(0).column().name());
        assertInstanceOf(BinaryExpr.class, update.assignments().get(1).value());
        assertInstanceOf(BinaryExpr.class, update.where());
    }

    @Test
    void UPDATE别名两形态与限定左列() {
        final UpdateStatement bare = (UpdateStatement) BobaChop.parse("UPDATE t_order o SET o.status = 1");
        assertEquals("o", bare.table().alias());
        assertNull(bare.where());
        assertEquals("o", bare.assignments().get(0).column().qualifier());

        final UpdateStatement asForm = (UpdateStatement) BobaChop.parse("UPDATE t_order AS o SET status = 1");
        assertEquals("o", asForm.table().alias());
    }

    @Test
    void UPDATE右值子查询与参数访问序() {
        final UpdateStatement update = (UpdateStatement) BobaChop.parse(
                "UPDATE t SET a = (SELECT MAX(x) FROM src), b = ? WHERE id = ?");
        assertInstanceOf(SubqueryExpr.class, update.assignments().get(0).value());
        assertEquals(0, ((ParameterMarker) update.assignments().get(1).value()).index());
        assertEquals(1, ((ParameterMarker) ((BinaryExpr) update.where()).right()).index());
    }

    @Test
    void UPDATE白名单外与语法错误() {
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("UPDATE t1, t2 SET a = 1"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("UPDATE t1 JOIN t2 ON 1 = 1 SET a = 1"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("UPDATE t SET a = 1 ORDER BY a"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("UPDATE t SET a = 1 LIMIT 2"));
        assertThrows(ParseException.class, () -> BobaChop.parse("UPDATE t a = 1"));
        assertThrows(ParseException.class, () -> BobaChop.parse("UPDATE t SET a 1"));
        assertThrows(ParseException.class, () -> BobaChop.parse("UPDATE t SET"));
    }

    @Test
    void DELETE基本形态() {
        final DeleteStatement delete = (DeleteStatement) BobaChop.parse(
                "DELETE FROM t_order WHERE id = 42 AND status = 'INIT'");
        assertEquals("t_order", delete.table().name());
        assertNull(delete.table().alias());
        assertInstanceOf(BinaryExpr.class, delete.where());

        final DeleteStatement all = (DeleteStatement) BobaChop.parse("DELETE FROM t_order");
        assertNull(all.where());
    }

    @Test
    void DELETE别名与限定表名() {
        final DeleteStatement aliased = (DeleteStatement) BobaChop.parse("DELETE FROM t_order AS o WHERE o.id = 1");
        assertEquals("o", aliased.table().alias());

        final DeleteStatement qualified = (DeleteStatement) BobaChop.parse(
                "DELETE FROM db.t_order WHERE created_at < '2026-01-01'");
        assertEquals("db", qualified.table().qualifier());
    }

    @Test
    void DELETE白名单外与语法错误() {
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("DELETE t1, t2 FROM t1 JOIN t2 USING (id)"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("DELETE FROM t1, t2 USING (t1, t2)"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("DELETE FROM t WHERE id = 1 ORDER BY id"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("DELETE FROM t LIMIT 2"));
        assertThrows(ParseException.class, () -> BobaChop.parse("DELETE FROM"));
        assertThrows(ParseException.class, () -> BobaChop.parse("DELETE FROM t WHERE"));
    }
}
