package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Node;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;

/**
 * SpanWalker 覆盖守卫：classpath 反射枚举 ast 包全部 Node 具体类，断言每类都被
 * 极限语句集遍历到——新增 AST 类型若漏更遍历器分支（子树静默缺遍历）在本门禁暴露。
 */
final class SpanWalkerTest {

    /** 极限语句集：合并后必须触达全部 Node 具体类（含 PG/Oracle 专属形态）。 */
    private static final List<String> MAXIMAL = Arrays.asList(
            "SELECT DISTINCT a.b AS x, *, t2.*, 1+2*3, 's''tr', ?, -u FROM db.t AS t1 "
                    + "JOIN (SELECT COUNT(*) c FROM t2 WHERE EXISTS (SELECT 1) AND a NOT BETWEEN 1 AND 2) d "
                    + "ON t1.id = d.id LEFT JOIN t3 USING (id) WHERE a = (SELECT MAX(id) FROM t4) "
                    + "OR a IN (SELECT x FROM t5) OR b LIKE 'x%' ESCAPE '!' "
                    + "AND c IS NOT NULL GROUP BY a, b HAVING COUNT(*) > 1 ORDER BY a DESC, b LIMIT 5, 10",
            "SELECT CASE a WHEN 1 THEN 'x' ELSE 'y' END FROM t1 UNION ALL SELECT * FROM t2",
            "INSERT INTO db.t (a, b) VALUES (1, 'x'), (?, 2) ON DUPLICATE KEY UPDATE a = VALUES(b), c = 3",
            "INSERT INTO t (a) SELECT a FROM src UNION SELECT b FROM s2",
            "UPDATE db.t AS x SET a = 1 + ? WHERE x.a IS NULL",
            "DELETE FROM t WHERE a = 1",
            "SELECT a FROM t OFFSET 2 ROWS FETCH NEXT 3 ROWS ONLY",
            "SELECT a, seq.NEXTVAL FROM t OFFSET ? ROWS FETCH FIRST 3 ROWS ONLY");

    @Test
    void 全部node具体类均被遍历() throws IOException {
        final Set<Class<?>> declared = nodeClasses();
        assertTrue(declared.size() >= 29, "ast 包 Node 具体类数量异常: " + declared.size());
        final Set<Class<?>> visited = new HashSet<Class<?>>();
        for (final String sql : MAXIMAL) {
            final DialectProfile profile = sql.contains("NEXTVAL") || sql.contains("FETCH FIRST")
                    || sql.contains("FETCH NEXT") ? DialectProfile.ORACLE : DialectProfile.MYSQL;
            final Statement stmt = BobaChop.parse(sql, profile);
            SpanWalker.walk(stmt, (node, parent) -> visited.add(node.getClass()));
        }
        final Set<Class<?>> missed = new HashSet<Class<?>>(declared);
        missed.removeAll(visited);
        assertEquals(new HashSet<Class<?>>(), missed,
                "以下 Node 类型未被 SpanWalker 遍历到——极限语句集或缺遍历分支: " + missed);
    }

    /** 源码树枚举 ast 包（与语料门禁同一仓布局先例 ../corpus）；接口/抽象类过滤。 */
    private static Set<Class<?>> nodeClasses() throws IOException {
        final Set<Class<?>> out = new HashSet<Class<?>>();
        final File dir = Paths.get("..", "boba-chop-core", "src", "main", "java", "io", "github",
                "susongyan", "bobachop", "ast").toFile();
        assertTrue(dir.isDirectory(), "ast 源码目录缺失: " + dir.getAbsolutePath());
        final String[] files = dir.list();
        if (null == files) {
            return out;
        }
        for (final String name : files) {
            if (!name.endsWith(".java") || "package-info.java".equals(name)) {
                continue;
            }
            final Class<?> clazz;
            try {
                clazz = Class.forName("io.github.susongyan.bobachop.ast."
                        + name.substring(0, name.length() - ".java".length()));
            } catch (final ClassNotFoundException e) {
                throw new IllegalStateException("ast 包类加载失败: " + name, e);
            }
            if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())
                    && Node.class.isAssignableFrom(clazz)) {
                out.add(clazz);
            }
        }
        return out;
    }
}
