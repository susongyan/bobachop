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
import io.github.susongyan.bobachop.ast.DerivedTable;
import io.github.susongyan.bobachop.ast.InsertStatement;
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
 * Oracle 方言黄金用例（T11）：标准分页族（OFFSET ... FETCH）、表别名 AS 拒绝（列位 AS 允许）、
 * FROM DUAL/序列 NEXTVAL、伪列与层级查询前置归类（ROWNUM/LEVEL/PRIOR/MINUS/START/CONNECT/
 * NULLS）、MERGE/多表 INSERT 拒绝、DML 骨架复用。
 */
final class OracleParserTest {

    private static Statement ora(final String sql) {
        return BobaChop.parse(sql, DialectProfile.ORACLE);
    }

    private static Limit limitOf(final String sql) {
        return ((SelectStatement) ora(sql)).limit();
    }

    @Test
    void offsetFetch分页族() {
        final Limit combined = limitOf("SELECT id FROM t ORDER BY id OFFSET 30 ROWS FETCH FIRST 10 ROWS ONLY");
        assertEquals("30", ((Literal) combined.offset()).text());
        assertEquals("10", ((Literal) combined.rowCount()).text());
        // NEXT 同义 + ROW 单数 + 无 OFFSET 前导
        final Limit next = limitOf("SELECT id FROM t FETCH NEXT 1 ROW ONLY");
        assertNull(next.offset());
        assertEquals("1", ((Literal) next.rowCount()).text());
        // 参数标记两槽位
        final Limit params = limitOf("SELECT id FROM t OFFSET ? ROWS FETCH FIRST ? ROWS ONLY");
        assertInstanceOf(ParameterMarker.class, params.offset());
        assertInstanceOf(ParameterMarker.class, params.rowCount());
        // LIMIT 非本方言词：出现即语法错误（非白名单外语法的定性）
        assertThrows(ParseException.class, () -> ora("SELECT id FROM t LIMIT 10"));
        assertThrows(ParseException.class, () -> ora("SELECT id FROM t LIMIT 10, 5"));
    }

    @Test
    void 伪列与层级查询前置归类() {
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT id FROM t WHERE ROWNUM <= 10"));
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT LEVEL FROM t"));
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT id FROM t WHERE PRIOR id = 1"));
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT id FROM t WHERE d = SYSDATE"));
        // 层级查询尾部（START WITH / CONNECT BY）
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT id FROM t START WITH id = 1"));
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT id FROM t CONNECT BY id = parent_id"));
        // MINUS 集合运算（复合级同拒）
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT 1 MINUS SELECT 2"));
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT 1 UNION SELECT 2 MINUS SELECT 3"));
    }

    @Test
    void nulls排序语义白名单外() {
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT id FROM t ORDER BY id NULLS FIRST"));
        assertThrows(UnsupportedSyntaxException.class, () -> ora("SELECT id FROM t ORDER BY id DESC NULLS LAST"));
        // FULL OUTER JOIN 为合法 Oracle 语法但 JoinType 无 FULL 槽位（连接位前置归类）
        assertThrows(UnsupportedSyntaxException.class,
                () -> ora("SELECT * FROM t1 FULL OUTER JOIN t2 ON t1.id = t2.id"));
    }

    @Test
    void merge与多表insert白名单外() {
        assertThrows(UnsupportedSyntaxException.class,
                () -> ora("MERGE INTO t USING s ON (t.id = s.id) WHEN MATCHED THEN UPDATE SET t.a = s.a"));
        assertThrows(UnsupportedSyntaxException.class,
                () -> ora("INSERT ALL INTO t (id) VALUES (1) SELECT 1 FROM dual"));
        assertThrows(UnsupportedSyntaxException.class, () -> ora("INSERT FIRST INTO t (id) VALUES (1)"));
        // 多表 UPDATE：JOIN 不被吞作裸别名，前置归类保持（PG 同受益）
        assertThrows(UnsupportedSyntaxException.class, () -> ora("UPDATE t1 JOIN t2 ON t1.id = t2.id SET t1.a = 1"));
    }

    @Test
    void 表别名as拒绝与裸别名() {
        // 表位/派生表位：AS 引导即语法错误（Oracle 形态），裸别名合法
        assertThrows(ParseException.class, () -> ora("SELECT * FROM t AS a"));
        final SelectStatement bare = (SelectStatement) ora("SELECT * FROM t a");
        assertEquals("a", ((TableName) bare.from()).alias());
        assertThrows(ParseException.class, () -> ora("SELECT * FROM (SELECT 1 FROM dual) AS d"));
        assertEquals("d", ((DerivedTable) ((SelectStatement) ora("SELECT * FROM (SELECT 1 FROM dual) d")).from()).alias());
        // 列位 AS 各方言皆允许
        assertEquals("x", ((SelectStatement) ora("SELECT id AS x FROM t")).items().get(0).alias());
        // UPDATE/DELETE 表位同规
        assertThrows(ParseException.class, () -> ora("UPDATE t AS a SET a.id = 1"));
        assertNotNull(ora("UPDATE t a SET a.id = 1"));
        assertThrows(ParseException.class, () -> ora("DELETE FROM t AS a WHERE a.id = 1"));
        assertNotNull(ora("DELETE FROM t a WHERE a.id = 1"));
        // MySQL 回归锚：表位 AS 在 MySQL/PG 仍合法
        assertNotNull(BobaChop.parse("SELECT * FROM t AS a"));
        assertNotNull(BobaChop.parse("SELECT * FROM t AS a", DialectProfile.POSTGRESQL));
    }

    @Test
    void fromDual与序列nextval() {
        // DUAL 在 Oracle 非关键字：普通标识符表名（MySQL 侧 T11 起 FROM DUAL 亦可用）
        assertEquals("dual", ((TableName) ((SelectStatement) ora("SELECT 1 FROM dual")).from()).name());
        assertNotNull(BobaChop.parse("SELECT 1 FROM DUAL"));
        // 序列引用：限定名链天然承载（NEXTVAL/CURRVAL 均为标识符）
        final SelectStatement seq = (SelectStatement) ora("SELECT seq_order.NEXTVAL FROM dual");
        final ColumnRef column = (ColumnRef) seq.items().get(0).expr();
        assertEquals("seq_order", column.qualifier());
        assertEquals("NEXTVAL", column.name());
    }

    @Test
    void ansi双引号标识符进表位列位与别名() {
        final SelectStatement select = (SelectStatement) ora(
                "SELECT \"user id\", t.\"name\" AS \"昵称\" FROM hr.\"emp table\" \"et\"");
        final TableName table = (TableName) select.from();
        assertEquals("hr", table.qualifier());
        assertEquals("\"emp table\"", table.name());
        assertEquals("\"et\"", table.alias());
        assertEquals("\"user id\"", ((ColumnRef) select.items().get(0).expr()).name());
        assertEquals("\"昵称\"", select.items().get(1).alias());
    }

    @Test
    void 时间字面量与date保留字收紧() {
        assertNotNull(((SelectStatement) ora("SELECT id FROM t WHERE d = DATE '2026-01-01'")).where());
        assertNotNull(((SelectStatement) ora("SELECT id FROM t WHERE ts = TIMESTAMP '2026-01-01 00:00:00'")).where());
        // DATE 在 Oracle 为保留字：无字面量后继不可作裸列名（PG/MySQL 非保留，行为不变）
        assertThrows(ParseException.class, () -> ora("SELECT date FROM t"));
        assertNotNull(BobaChop.parse("SELECT date FROM t"));
    }

    @Test
    void 串接运算符与oracle非保留字标识符() {
        final SelectStatement concat = (SelectStatement) ora("SELECT a || b FROM t");
        assertEquals("||", ((BinaryExpr) concat.items().get(0).expr()).operator());
        // MERGE/JOIN 非保留：可作未包裹标识符（Oracle 本就允许）
        assertEquals(1, ((SelectStatement) ora("SELECT merge FROM t")).items().size());
        assertEquals("join", ((TableName) ((SelectStatement) ora("SELECT * FROM join")).from()).name());
        // MINUS 保留：裸用即语法错误
        assertThrows(ParseException.class, () -> ora("SELECT minus FROM t"));
    }

    @Test
    void dml骨架复用与参数访问序() {
        final InsertStatement insert = (InsertStatement) ora(
                "INSERT INTO \"order\" (id, amount) VALUES (?, ?), (?, ?)");
        assertEquals("\"order\"", insert.table().name());
        assertEquals(2, insert.columns().size());
        assertEquals(2, ((ValuesSource) insert.source()).rows().size());
        final SelectStatement select = (SelectStatement) ora("SELECT * FROM t WHERE id = ? AND qty > ?");
        final BinaryExpr and = (BinaryExpr) select.where();
        assertInstanceOf(ParameterMarker.class, ((BinaryExpr) and.left()).right());
        assertInstanceOf(ParameterMarker.class, ((BinaryExpr) and.right()).right());
        assertTrue(ora("UPDATE \"order\" SET amount = ? WHERE id = ?") instanceof Statement);
        assertTrue(ora("DELETE FROM \"order\" WHERE id = ?") instanceof Statement);
    }
}
