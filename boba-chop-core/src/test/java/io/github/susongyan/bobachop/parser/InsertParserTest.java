package io.github.susongyan.bobachop.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Assignment;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.FunctionCall;
import io.github.susongyan.bobachop.ast.InsertStatement;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.SelectSource;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.SubqueryExpr;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.ast.ValuesSource;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;

/**
 * InsertParser 黄金用例（T8）：列清单/多行 VALUES/参数访问序/insert-SELECT/ODKU
 * （含 MySQL 专有 VALUES(col)）/静态列数校验/白名单外形态分类。
 */
final class InsertParserTest {

    @Test
    void 列清单与单行() {
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t_order (id, amount) VALUES (1, 9.9)");
        assertEquals("t_order", insert.table().name());
        assertNull(insert.table().qualifier());
        assertEquals(2, insert.columns().size());
        assertEquals("id", insert.columns().get(0));
        final ValuesSource source = (ValuesSource) insert.source();
        assertEquals(1, source.rows().size());
        assertEquals(2, source.rows().get(0).size());
        assertNull(insert.onDuplicateKey());
    }

    @Test
    void 多行VALUES与无列清单与无INTO() {
        final InsertStatement multi = (InsertStatement) BobaChop.parse(
                "INSERT INTO t (a, b) VALUES (1, 'x'), (2, 'y'), (3, NULL)");
        assertEquals(3, ((ValuesSource) multi.source()).rows().size());

        final InsertStatement bare = (InsertStatement) BobaChop.parse(
                "INSERT t VALUES (1, 'x', TRUE)");
        assertNull(bare.columns());
        assertEquals(3, ((ValuesSource) bare.source()).rows().get(0).size());
    }

    @Test
    void 限定表名() {
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO db.t (a) VALUES (1)");
        assertEquals("db", insert.table().qualifier());
        assertEquals("t", insert.table().name());
    }

    @Test
    void 参数标记访问序() {
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t (a, b) VALUES (?, ?), (?, ?)");
        final List<List<Expr>> rows = ((ValuesSource) insert.source()).rows();
        int index = 0;
        for (final List<Expr> row : rows) {
            for (final Expr expr : row) {
                assertEquals(index++, ((ParameterMarker) expr).index());
            }
        }
        assertEquals(4, index);
    }

    @Test
    void 行值为表达式与子查询() {
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t (a, b) VALUES (1 + 1, (SELECT MAX(id) FROM seq))");
        final List<Expr> row = ((ValuesSource) insert.source()).rows().get(0);
        assertInstanceOf(BinaryExpr.class, row.get(0));
        assertInstanceOf(SubqueryExpr.class, row.get(1));
    }

    @Test
    void insertSelect() {
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t (a, b) SELECT x, y FROM src WHERE x > 0");
        final SelectSource source = (SelectSource) insert.source();
        final SelectStatement query = (SelectStatement) source.query();
        assertEquals(2, query.items().size());
        assertNotNull(query.where());
    }

    @Test
    void 表名为非保留字first不误判多表INSERT_f2修复回归() {
        // ALL/FIRST 检查曾在消费 INTO 后执行：INSERT INTO first 的 first 是表名却被误判
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO first (id) VALUES (1)");
        assertEquals("first", insert.table().name());
    }

    @Test
    void insertSelect的CROSS_JOIN后ODKU尾缀_f2修复回归() {
        // ON DUPLICATE KEY UPDATE 的 ON 属于外层 INSERT，不是 CROSS 的连接条件
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t SELECT a.id FROM a CROSS JOIN b ON DUPLICATE KEY UPDATE cnt = 1");
        assertEquals(1, insert.onDuplicateKey().size());
        // 真正的 CROSS 带 ON 仍前置归类
        assertThrows(UnsupportedSyntaxException.class,
                () -> BobaChop.parse("SELECT * FROM a CROSS JOIN b ON a.id = b.id"));
    }

    @Test
    void insertSelect数据源span不倒挂_f1修复回归() {
        // F1 对拍体系（SpanInvariants R1）抓出的缺陷回归：SelectSource 构造时
        // lastStop() 抢在子解析前求值，stop = SELECT 前一 token 终点 → span 倒挂
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t (a) SELECT x FROM src WHERE x > 0");
        final SelectSource source = (SelectSource) insert.source();
        final SelectStatement query = (SelectStatement) source.query();
        assertEquals(query.start(), source.start(), "数据源 span 起点应为 SELECT 起点");
        assertEquals(query.stop(), source.stop(), "数据源 span 终点应为查询体终点");
    }

    @Test
    void insertSelect自带ORDER_BY与LIMIT合法() {
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t SELECT id FROM src ORDER BY id DESC LIMIT 3");
        final SelectStatement query = (SelectStatement) ((SelectSource) insert.source()).query();
        assertNotNull(query.orderBy());
        assertNotNull(query.limit());
    }

    @Test
    void insertSelect复合源() {
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t (a) SELECT x FROM s1 UNION ALL SELECT y FROM s2");
        final io.github.susongyan.bobachop.ast.CompoundSelect query =
                (io.github.susongyan.bobachop.ast.CompoundSelect) ((SelectSource) insert.source()).query();
        assertEquals(2, query.queries().size());
    }

    @Test
    void ON_DUPLICATE_KEY_UPDATE含VALUES函数() {
        final InsertStatement insert = (InsertStatement) BobaChop.parse(
                "INSERT INTO t (id, c) VALUES (1, 2) ON DUPLICATE KEY UPDATE c = VALUES(c), c = c + 1");
        final List<Assignment> odku = insert.onDuplicateKey();
        assertEquals(2, odku.size());
        final FunctionCall values = (FunctionCall) odku.get(0).value();
        assertEquals("VALUES", values.name());
        assertEquals("c", ((ColumnRef) values.arguments().get(0)).name());
        assertInstanceOf(BinaryExpr.class, odku.get(1).value());
    }

    @Test
    void 静态列数校验() {
        // 列数 vs 值数（静态可判）；insert-select 的列数依赖 schema，不做静态校验
        assertThrows(ParseException.class, () -> BobaChop.parse("INSERT INTO t (a, b) VALUES (1)"));
        assertThrows(ParseException.class, () -> BobaChop.parse("INSERT INTO t (a) VALUES (1, 2)"));
        // 行间值数不一致
        assertThrows(ParseException.class, () -> BobaChop.parse("INSERT INTO t VALUES (1, 2), (3)"));
        // 空 VALUES 行形态 v1 不收
        assertThrows(ParseException.class, () -> BobaChop.parse("INSERT INTO t VALUES ()"));
    }

    @Test
    void 白名单外形态() {
        // 括号化查询数据源：peek(1)==SELECT 即判，无需扫描
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("INSERT INTO t (SELECT 1)"));
        // 语法合法但 v1 白名单外
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("INSERT INTO t VALUE (1)"));
        assertThrows(UnsupportedSyntaxException.class, () -> BobaChop.parse("INSERT INTO t SET a = 1"));
        // 语法错误
        assertThrows(ParseException.class, () -> BobaChop.parse("INSERT INTO t WHERE id = 1"));
        assertThrows(ParseException.class, () -> BobaChop.parse("INSERT INTO (a) VALUES (1)"));
    }

    @Test
    void 语句跨度() {
        final String sql = "INSERT INTO t (a) VALUES (1)";
        final Statement statement = BobaChop.parse(sql);
        assertEquals(0, statement.start());
        assertEquals(sql.length() - 1, statement.stop());
    }
}
