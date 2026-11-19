package io.github.susongyan.bobachop.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.CompoundSelect;
import io.github.susongyan.bobachop.ast.DerivedTable;
import io.github.susongyan.bobachop.ast.ExistsExpr;
import io.github.susongyan.bobachop.ast.InExpr;
import io.github.susongyan.bobachop.ast.Join;
import io.github.susongyan.bobachop.ast.JoinType;
import io.github.susongyan.bobachop.ast.Limit;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.OrderByItem;
import io.github.susongyan.bobachop.ast.OrderDirection;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.SelectItem;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.SetOperator;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.SubqueryExpr;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.ast.TableRef;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;

/**
 * SelectParser 黄金用例（T7）：语句结构、JOIN 族、LIMIT 三形态、UNION、派生表、
 * 子查询桥接（T6 占位接活）、语句级拒绝分类。
 */
final class SelectParserTest {

    @Test
    void 无FROM投影与语句跨度() {
        final String sql = "SELECT 1";
        final Statement statement = BobaChop.parse(sql);
        final SelectStatement select = (SelectStatement) statement;
        assertEquals(1, select.items().size());
        assertNull(select.from());
        assertEquals(0, statement.start());
        assertEquals(sql.length() - 1, statement.stop());
    }

    @Test
    void 投影别名与表别名() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT id, user_id AS uid FROM t_order o");
        assertEquals(2, select.items().size());
        assertEquals("uid", select.items().get(1).alias());
        assertNull(select.items().get(0).alias());
        final TableName table = (TableName) select.from();
        assertEquals("t_order", table.name());
        assertEquals("o", table.alias());
        assertNull(table.qualifier());
    }

    @Test
    void 限定表名与DISTINCT() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT DISTINCT id FROM db.t_order");
        assertTrue(select.distinct());
        assertEquals("db", ((TableName) select.from()).qualifier());
    }

    @Test
    void WHERE表达式接入Pratt产物() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT id FROM t_order WHERE amount > 100 AND status = 'PAID'");
        final BinaryExpr where = (BinaryExpr) select.where();
        assertEquals("AND", where.operator());
        assertEquals(">", ((BinaryExpr) where.left()).operator());
    }

    @Test
    void INNER_JOIN_ON() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT o.id FROM t_order o INNER JOIN t_item i ON o.id = i.order_id");
        final Join join = (Join) select.from();
        assertEquals(JoinType.INNER, join.type());
        assertNotNull(join.condition());
        assertNull(join.usingColumns());
    }

    @Test
    void LEFT_OUTER_JOIN与逗号连接与USING() {
        final SelectStatement left = (SelectStatement) BobaChop.parse(
                "SELECT o.id FROM t_order o LEFT OUTER JOIN t_user u ON o.user_id = u.id");
        assertEquals(JoinType.LEFT, ((Join) left.from()).type());

        final SelectStatement comma = (SelectStatement) BobaChop.parse(
                "SELECT o.id FROM t_order o, t_user u WHERE o.user_id = u.id");
        final Join join = (Join) comma.from();
        assertEquals(JoinType.COMMA, join.type());
        assertNull(join.condition());
        assertNull(join.usingColumns());

        final SelectStatement using = (SelectStatement) BobaChop.parse(
                "SELECT o.id FROM t_order o JOIN t_user u USING (user_id)");
        final List<String> columns = ((Join) using.from()).usingColumns();
        assertEquals(1, columns.size());
        assertEquals("user_id", columns.get(0));
    }

    @Test
    void STRAIGHT_JOIN归入INNER() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT o.id FROM t_order o STRAIGHT_JOIN t_user u ON o.user_id = u.id");
        assertEquals(JoinType.INNER, ((Join) select.from()).type());
    }

    @Test
    void CROSS_JOIN无条件_带ON则白名单外() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT o.id FROM t_order o CROSS JOIN t_user u");
        assertNull(((Join) select.from()).condition());
        assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("SELECT o.id FROM t_order o CROSS JOIN t_user u ON o.id = u.id"));
    }

    @Test
    void 三表JOIN左结合() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT a.id FROM t1 a JOIN t2 b ON a.x = b.x JOIN t3 c ON b.y = c.y");
        final Join outer = (Join) select.from();
        assertEquals("t3", ((TableName) outer.right()).name());
        assertEquals("t1", ((TableName) ((Join) outer.left()).left()).name());
    }

    @Test
    void 派生表与缺别名() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT d.uid FROM (SELECT user_id AS uid FROM t_order) d");
        final DerivedTable derived = (DerivedTable) select.from();
        assertEquals("d", derived.alias());
        assertEquals(1, ((SelectStatement) derived.query()).items().size());
        assertThrows(ParseException.class,
                () -> BobaChop.parse("SELECT * FROM (SELECT user_id FROM t_order)"));
    }

    @Test
    void GROUP_BY与HAVING() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT user_id, COUNT(*) FROM t_order GROUP BY user_id HAVING COUNT(*) > 3");
        assertEquals(1, select.groupBy().size());
        assertNotNull(select.having());
    }

    @Test
    void ORDER_BY三态方向() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT id FROM t_order ORDER BY amount DESC, id");
        final List<OrderByItem> orderBy = select.orderBy();
        assertEquals(2, orderBy.size());
        assertEquals(OrderDirection.DESC, orderBy.get(0).direction());
        assertEquals(OrderDirection.UNSPECIFIED, orderBy.get(1).direction());
    }

    @Test
    void LIMIT三形态() {
        final Limit plain = ((SelectStatement) BobaChop.parse("SELECT id FROM t LIMIT 10")).limit();
        assertNull(plain.offset());
        assertEquals("10", ((Literal) plain.rowCount()).text());

        // MySQL：LIMIT o, n —— 首位是偏移
        final Limit comma = ((SelectStatement) BobaChop.parse("SELECT id FROM t LIMIT 20, 10")).limit();
        assertEquals("20", ((Literal) comma.offset()).text());
        assertEquals("10", ((Literal) comma.rowCount()).text());

        final Limit offset = ((SelectStatement) BobaChop.parse("SELECT id FROM t LIMIT 10 OFFSET 20")).limit();
        assertEquals("20", ((Literal) offset.offset()).text());
        assertEquals("10", ((Literal) offset.rowCount()).text());
    }

    @Test
    void LIMIT参数标记与访问序() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT id FROM t WHERE user_id = ? LIMIT ?, ?");
        assertEquals(0, ((ParameterMarker) ((BinaryExpr) select.where()).right()).index());
        final Limit limit = select.limit();
        assertEquals(1, ((ParameterMarker) limit.offset()).index());
        assertEquals(2, ((ParameterMarker) limit.rowCount()).index());
    }

    @Test
    void UNION链() {
        final CompoundSelect union = (CompoundSelect) BobaChop.parse(
                "SELECT id FROM a UNION SELECT id FROM b UNION ALL SELECT id FROM c");
        assertEquals(3, union.queries().size());
        assertEquals(2, union.operators().size());
        assertEquals(SetOperator.UNION, union.operators().get(0));
        assertEquals(SetOperator.UNION_ALL, union.operators().get(1));
    }

    @Test
    void UNION复合级ORDER_BY白名单外() {
        // 复合级尾锚（契约无此字段）
        assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("SELECT id FROM a UNION SELECT id FROM b ORDER BY id"));
        assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("SELECT id FROM a UNION SELECT id FROM b LIMIT 3"));
        // 未括号化操作数自带 ORDER BY/LIMIT（MySQL 1221 域）
        assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("SELECT id FROM a ORDER BY id UNION SELECT id FROM b"));
        assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("SELECT id FROM a LIMIT 1 UNION SELECT id FROM b"));
    }

    @Test
    void 派生表内自带ORDER_BY与LIMIT() {
        final SelectStatement select = (SelectStatement) BobaChop.parse(
                "SELECT d.id FROM (SELECT id FROM t_order ORDER BY id DESC LIMIT 3) d");
        final SelectStatement inner = (SelectStatement) ((DerivedTable) select.from()).query();
        assertNotNull(inner.orderBy());
        assertNotNull(inner.limit());
    }

    @Test
    void 子查询三槽位接活() {
        final SelectStatement inSub = (SelectStatement) BobaChop.parse(
                "SELECT id FROM t WHERE uid IN (SELECT id FROM u)");
        final InExpr in = (InExpr) inSub.where();
        assertNull(in.values());
        assertNotNull(in.query());

        final SelectStatement exists = (SelectStatement) BobaChop.parse(
                "SELECT id FROM t WHERE EXISTS (SELECT 1 FROM u WHERE u.tid = t.id)");
        assertInstanceOf(ExistsExpr.class, exists.where());

        final SelectStatement scalar = (SelectStatement) BobaChop.parse(
                "SELECT id FROM t WHERE amount > (SELECT AVG(amount) FROM t)");
        final BinaryExpr gt = (BinaryExpr) scalar.where();
        assertInstanceOf(SubqueryExpr.class, gt.right());
    }

    @Test
    void 尾分号与多语句() {
        assertNotNull(BobaChop.parse("SELECT 1;"));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT 1; SELECT 2"));
    }

    @Test
    void 语句级拒绝分类() {
        // 白名单外：CTE、尾部锁定/窗口构造、括号化表引用、三级限定表名、ROLLUP、LIMIT 表达式
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("WITH c AS (SELECT 1) SELECT * FROM c"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("SELECT id FROM t FOR UPDATE"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("SELECT * FROM (t1 JOIN t2 ON 1 = 1) x"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("SELECT * FROM a.b.c"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("SELECT a FROM t GROUP BY a WITH ROLLUP"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("SELECT a FROM t LIMIT 1 + 1"));
        // 语法错误：非 SELECT 起始、FROM 后非表引用、AS 后缺别名
        assertThrows(ParseException.class, () -> BobaChop.parse("SHOW TABLES"));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT a FROM WHERE"));
        assertThrows(ParseException.class, () -> BobaChop.parse("SELECT a AS FROM t"));
    }

    @Test
    void 表达式结尾语句的stop偏移() {
        // stop 追踪不经 last 字段而按 consumed() 直读：表达式结尾（WHERE/无方向 ORDER BY/无 FROM）恒准
        final String sql = "SELECT id FROM t WHERE a = 1";
        final Statement statement = BobaChop.parse(sql);
        assertEquals(sql.length() - 1, statement.stop());

        final String order = "SELECT id FROM t ORDER BY id";
        assertEquals(order.length() - 1, BobaChop.parse(order).stop());

        final String noFrom = "SELECT 1 + 2 * 3";
        assertEquals(noFrom.length() - 1, BobaChop.parse(noFrom).stop());
    }
}
