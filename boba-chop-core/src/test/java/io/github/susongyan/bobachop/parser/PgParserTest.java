package io.github.susongyan.bobachop.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.InsertStatement;
import io.github.susongyan.bobachop.ast.Join;
import io.github.susongyan.bobachop.ast.JoinType;
import io.github.susongyan.bobachop.ast.Limit;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.ast.ValuesSource;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;

/**
 * PostgreSQL 方言黄金用例（T10）：分页族（LIMIT/OFFSET/FETCH FIRST）、ANSI 双引号标识符、
 * E'' 字符串、DML 骨架复用、白名单外语法的拒绝分类（Unsupported vs Parse）。
 */
final class PgParserTest {

    private static Statement pg(final String sql) {
        return BobaChop.parse(sql, DialectProfile.POSTGRESQL);
    }

    private static Limit limitOf(final String sql) {
        return ((SelectStatement) pg(sql)).limit();
    }

    @Test
    void limit单形态与参数标记() {
        final Limit limit = limitOf("SELECT id FROM t ORDER BY id LIMIT 10");
        assertNull(limit.offset());
        assertEquals("10", ((Literal) limit.rowCount()).text());
        final Limit param = limitOf("SELECT id FROM t LIMIT ?");
        assertInstanceOf(ParameterMarker.class, param.rowCount());
    }

    @Test
    void limitOffset形态与rows后缀() {
        final Limit limit = limitOf("SELECT id FROM t LIMIT 5 OFFSET 20");
        assertEquals("20", ((Literal) limit.offset()).text());
        assertEquals("5", ((Literal) limit.rowCount()).text());
        // OFFSET 计数的 ROW/ROWS 语法糖
        final Limit suffixed = limitOf("SELECT id FROM t LIMIT 5 OFFSET 20 ROWS");
        assertEquals("20", ((Literal) suffixed.offset()).text());
    }

    @Test
    void limit逗号形态为PG语法错误() {
        assertThrows(ParseException.class, () -> pg("SELECT id FROM t LIMIT 20, 5"));
    }

    @Test
    void limitAll与裸Offset白名单外() {
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT id FROM t LIMIT ALL"));
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT id FROM t OFFSET 10"));
    }

    @Test
    void fetchFirst族() {
        final Limit fetch = limitOf("SELECT id FROM t FETCH FIRST 10 ROWS ONLY");
        assertNull(fetch.offset());
        assertEquals("10", ((Literal) fetch.rowCount()).text());
        // NEXT 同义 + ROW 单数
        final Limit next = limitOf("SELECT id FROM t FETCH NEXT 1 ROW ONLY");
        assertEquals("1", ((Literal) next.rowCount()).text());
        // OFFSET m FETCH FIRST n ROWS ONLY（语义槽位对齐 MySQL LIMIT n OFFSET m）
        final Limit combined = limitOf("SELECT id FROM t OFFSET 30 ROWS FETCH FIRST 10 ROWS ONLY");
        assertEquals("30", ((Literal) combined.offset()).text());
        assertEquals("10", ((Literal) combined.rowCount()).text());
        // LIMIT 与 FETCH 并用拒绝（含经 OFFSET 中转的形态）
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT id FROM t LIMIT 5 FETCH FIRST 5 ROWS ONLY"));
        assertThrows(UnsupportedSyntaxException.class,
                () -> pg("SELECT id FROM t LIMIT 5 OFFSET 2 FETCH FIRST 5 ROWS ONLY"));
        // 省略计数（隐含 1，无字面锚点）拒绝
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT id FROM t FETCH FIRST ROW ONLY"));
        // 缺 ONLY 为语法错误
        assertThrows(ParseException.class, () -> pg("SELECT id FROM t FETCH FIRST 5 ROWS"));
    }

    @Test
    void ansi双引号标识符进表位列位与别名() {
        final SelectStatement select = (SelectStatement) pg(
                "SELECT \"user id\", t.\"name\" AS \"昵称\" FROM public.\"user table\" \"ut\"");
        final TableName table = (TableName) select.from();
        assertEquals("public", table.qualifier());
        assertEquals("\"user table\"", table.name());
        assertEquals("\"ut\"", table.alias());
        assertEquals(2, select.items().size());
        assertEquals("\"user id\"", ((ColumnRef) select.items().get(0).expr()).name());
        assertEquals("\"昵称\"", select.items().get(1).alias());
    }

    @Test
    void e前缀字符串与标准字符串() {
        final SelectStatement select = (SelectStatement) pg(
                "SELECT id FROM t WHERE name = E'a\\'b' AND note = 'x\\y'");
        assertNotNull(select.where());
    }

    @Test
    void 时间字面量与受限函数名沿主段行为() {
        assertNotNull(((SelectStatement) pg("SELECT id FROM t WHERE d = DATE '2026-01-01'")).where());
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT CAST(id AS CHAR) FROM t"));
    }

    @Test
    void 集合运算与复合尾部白名单外() {
        // UNION 本身在白名单内；EXCEPT/INTERSECT 与复合级尾部为白名单外
        assertNotNull(pg("SELECT 1 UNION SELECT 2"));
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT 1 EXCEPT SELECT 2"));
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT 1 INTERSECT SELECT 2"));
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT 1 UNION SELECT 2 ORDER BY 1"));
        // 复合级分页尾锚（PG 形态）同属白名单外
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT 1 UNION SELECT 2 LIMIT 1"));
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT 1 UNION SELECT 2 OFFSET 1 FETCH FIRST 1 ROW ONLY"));
    }

    @Test
    void pg专有语法前置归类() {
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT * FROM t1 FULL JOIN t2 ON t1.id = t2.id"));
        assertThrows(UnsupportedSyntaxException.class, () -> pg("SELECT * FROM t1, LATERAL (SELECT 1) x"));
        assertThrows(UnsupportedSyntaxException.class, () -> pg("INSERT INTO t (id) VALUES (1) RETURNING id"));
        assertThrows(UnsupportedSyntaxException.class, () -> pg("UPDATE t SET id = 1 RETURNING *"));
        assertThrows(UnsupportedSyntaxException.class,
                () -> pg("INSERT INTO t (id) VALUES (1) ON CONFLICT (id) DO NOTHING"));
        // FULL/RETURNING 非 MySQL 关键字：默认入口不受影响（回归锚）
        assertEquals(2, ((SelectStatement) BobaChop.parse("SELECT full, returning FROM t")).items().size());
    }

    @Test
    void dml骨架复用与参数访问序() {
        final InsertStatement insert = (InsertStatement) pg(
                "INSERT INTO \"order\" (id, amount) VALUES (?, ?), (?, ?)");
        assertEquals("\"order\"", insert.table().name());
        assertEquals(2, insert.columns().size());
        assertEquals(2, ((ValuesSource) insert.source()).rows().size());
        final SelectStatement select = (SelectStatement) pg("SELECT * FROM t WHERE id = ? AND qty > ?");
        // 顶层 AND 之下的两个比较分支右值各为参数标记（访问序 = 文本序）
        final BinaryExpr and = (BinaryExpr) select.where();
        assertInstanceOf(ParameterMarker.class, ((BinaryExpr) and.left()).right());
        assertInstanceOf(ParameterMarker.class, ((BinaryExpr) and.right()).right());
    }

    @Test
    void pg非保留字可作标识符() {
        // JOIN/LEFT/VALUES/ROW 在 PG 为非保留字：可作未包裹标识符
        assertEquals("row", ((TableName) ((SelectStatement) pg("SELECT * FROM row")).from()).name());
        assertEquals(1, ((SelectStatement) pg("SELECT next FROM t")).items().size());
    }

    @Test
    void 裸join关键字不作表别名_t11修复回归() {
        // T10 缺陷：JOIN/LEFT 在 PG 为非保留字，表别名位被吞——裸 JOIN 失败、LEFT JOIN 静默错析为别名
        final SelectStatement inner = (SelectStatement) pg("SELECT * FROM t1 JOIN t2 ON t1.id = t2.id");
        assertNull(((TableName) ((Join) inner.from()).left()).alias());
        final Join leftJoin = (Join) ((SelectStatement) pg("SELECT * FROM t1 LEFT JOIN t2 ON t1.id = t2.id")).from();
        assertEquals(JoinType.LEFT, leftJoin.type());
        assertNull(((TableName) leftJoin.left()).alias());
        // 连接词后随非连接延续时仍可作裸别名（PG 本就允许）
        assertEquals("left", ((TableName) ((SelectStatement) pg("SELECT * FROM t1 left")).from()).alias());
        assertEquals("join", ((TableName) ((SelectStatement) pg("SELECT * FROM t1 join")).from()).alias());
        // 派生表位同规：连接词紧跟即"缺别名"语法错误
        assertThrows(ParseException.class, () -> pg("SELECT * FROM (SELECT 1 FROM x) LEFT JOIN t2 ON t2.id = 1"));
    }
}
