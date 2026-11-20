package io.github.susongyan.bobachop.bench.spec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.LexException;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;

/**
 * 对拍体系 v2 F5——规格语法方向性对拍：白名单内差异率 ≡ 0。
 *
 * <p><b>口径（DESIGN §8）</b>：方向性 = 内核 ACCEPT ⇒ 规格语法 ACCEPT；反向（语法收、
 * 内核拒）为已声明容差（T1 词表/形态并集、T2 词级粒度、T3 词法旋钮——见 g4 头注），
 * 单独锁定方向不得反转。语句级白名单外形态双向拒（负例双向断言）。</p>
 *
 * <p>三组正例：(1) 仓内语料五文件全量——内核绿即语法绿；(2) F4 接受面矩阵重放——
 * 三方言词表全词 × 四标识符槽位（探针模板独立复述自 AcceptanceMatrixTest，快照门禁
 * 延伸到规格语法：矩阵翻转 ⇒ 本测试与 g4 别名清单同步）；(3) RETURNING 别名前瞻等
 * 位置键控补充针。任何一格方向性翻转即"内核收而规格拒"——真差异，非容差。</p>
 */
class SpecGrammarDifferentialTest {

    /** 语料清单：方言 → 语料文件（内核白名单内全量）。 */
    private static final String[][] CORPUS = {
        {"MYSQL", "mysql/select-v1.txt"},
        {"MYSQL", "mysql/dml-v1.txt"},
        {"POSTGRESQL", "pg/select-v1.txt"},
        {"POSTGRESQL", "pg/dml-v1.txt"},
        {"ORACLE", "oracle/select-v1.txt"},
    };

    /** 语句级白名单外负例（方言, SQL）——内核拒（三支任一）且规格语法拒。 */
    private static final String[][] NEGATIVES = {
        // MySQL
        {"MYSQL", "WITH c AS (SELECT 1 FROM t) SELECT * FROM c"},
        {"MYSQL", "SELECT * FROM t FOR UPDATE"},
        {"MYSQL", "SELECT a FROM t GROUP BY a WITH ROLLUP"},
        {"MYSQL", "UPDATE t SET a = 1 ORDER BY a"},
        {"MYSQL", "UPDATE t1, t2 SET t1.a = 1"},
        {"MYSQL", "DELETE FROM t1, t2 WHERE t1.a = 1"},
        {"MYSQL", "INSERT INTO t SET a = 1"},
        {"MYSQL", "INSERT INTO t (SELECT id FROM src)"},
        {"MYSQL", "UPDATE t SET a := 1"},
        {"MYSQL", "SELECT a FROM t WHERE a IS TRUE"},
        {"MYSQL", "SELECT (a, b) FROM t"},
        {"MYSQL", "SELECT 1 UNION SELECT 2 ORDER BY 1"},
        {"MYSQL", "SELECT a FROM t ORDER BY a LIMIT 1 UNION SELECT b FROM u"},
        {"MYSQL", "SELECT 1 FROM a.b.c.d"},
        {"MYSQL", "SELECT * FROM (t1 JOIN t2 ON t1.a = t2.a)"},
        {"MYSQL", "SELECT :1 FROM t"},
        // PostgreSQL
        {"POSTGRESQL", "SELECT * FROM t OFFSET 5"},
        {"POSTGRESQL", "SELECT * FROM t LIMIT ALL"},
        {"POSTGRESQL", "SELECT * FROM t OFFSET 10 ROWS FETCH FIRST ROWS ONLY"},
        {"POSTGRESQL", "SELECT * FROM t OFFSET 10 ROWS FETCH FIRST 5 ROWS WITH TIES"},
        {"POSTGRESQL", "SELECT * FROM t LIMIT 10 OFFSET 5 ROWS FETCH FIRST 3 ROWS ONLY"},
        {"POSTGRESQL", "INSERT INTO t VALUES (1) ON CONFLICT DO NOTHING"},
        // Oracle
        {"ORACLE", "SELECT a FROM t ORDER BY a NULLS FIRST"},
        {"ORACLE", "SELECT * FROM t OFFSET 10 ROWS FETCH FIRST 5 PERCENT ROWS ONLY"},
        {"ORACLE", "UPDATE t SET a = 1 RETURNING id INTO :o"},
        {"ORACLE", "INSERT ALL INTO a VALUES (1) INTO b VALUES (2) SELECT * FROM dual"},
        {"ORACLE", "SELECT q'[x]' FROM t"},
    };

    /** 已声明容差（方言, SQL）——内核拒 + 规格语法收：并集接受面宽于内核的方向性缺口。 */
    private static final String[][] TOLERANCES = {
        // T1 方言词表/形态并集
        {"POSTGRESQL", "SELECT 'a' REGEXP 'b' FROM t"},
        {"POSTGRESQL", "SELECT * FROM t1 STRAIGHT_JOIN t2"},
        {"ORACLE", "SELECT * FROM t LIMIT 5"},
        {"MYSQL", "SELECT e'x' FROM t"},
        // T2 词级粒度（受限函数/别名位让位/表位 AS/连接条件并集）
        {"ORACLE", "SELECT 1 FROM t AS a"},
        {"MYSQL", "SELECT left('ab', 1) FROM t"},
        {"MYSQL", "SELECT values(a) FROM t"},
        {"MYSQL", "SELECT a FROM t1 CROSS JOIN t2 ON t1.x = t2.x"},
        {"POSTGRESQL", "SELECT * FROM t1 NATURAL JOIN t2"},
        {"POSTGRESQL", "SELECT * FROM t1 FULL JOIN t2 ON t1.a = t2.a"},
    };

    /** 位置键控补充针（方言, SQL）——内核 ACCEPT 的别名/前瞻形态，规格语法须同样收。 */
    private static final String[][] KEYED_POSITIVES = {
        {"ORACLE", "DELETE FROM t RETURNING"},
        {"ORACLE", "UPDATE t u SET u.a = 1"},
        {"POSTGRESQL", "UPDATE t SET a = 1 RETURNING id, a * 2 AS double_a"},
    };

    @Test
    void corpusDirectionalZeroDivergence() throws IOException {
        int statements = 0;
        final List<String> divergences = new ArrayList<String>();
        for (final String[] entry : CORPUS) {
            final DialectProfile profile = dialect(entry[0]);
            for (final String sql : readCorpus(entry[1])) {
                statements++;
                if (kernelAccepts(sql, profile) && !SpecGrammarDriver.accepts(sql)) {
                    divergences.add("[" + entry[0] + "] " + entry[1] + ": " + sql);
                }
            }
        }
        assertTrue(statements >= 150, "语料量异常: " + statements);
        assertTrue(divergences.isEmpty(),
                "语料方向性差异（内核收而规格拒）:\n" + String.join("\n", divergences));
    }

    /** F4 接受面矩阵重放：内核 ACCEPT 格 ⇒ 规格语法 ACCEPT（快照门禁延展）。 */
    @Test
    void matrixProbeReplayDirectional() {
        final List<String> divergences = new ArrayList<String>();
        int probes = 0;
        int accepts = 0;
        for (final DialectProfile profile :
                new DialectProfile[]{DialectProfile.MYSQL, DialectProfile.POSTGRESQL, DialectProfile.ORACLE}) {
            for (final Kw word : profile.lexerConfig().keywords().allWords()) {
                for (int slot = 0; slot < 4; slot++) {
                    final String sql = probeSql(word.name().toLowerCase(java.util.Locale.ROOT), slot);
                    probes++;
                    if (kernelAccepts(sql, profile)) {
                        accepts++;
                        if (!SpecGrammarDriver.accepts(sql)) {
                            divergences.add(profile + " " + word + " slot" + slot + ": " + sql);
                        }
                    }
                }
            }
        }
        assertTrue(probes >= 2000, "矩阵探针量异常: " + probes);
        assertTrue(accepts >= 100, "矩阵 ACCEPT 量异常: " + accepts);
        assertTrue(divergences.isEmpty(),
                "矩阵重放方向性差异（内核收而规格拒）:\n" + String.join("\n", divergences));
    }

    @Test
    void statementNegativesBothReject() {
        for (final String[] entry : NEGATIVES) {
            final String sql = entry[1];
            assertFalse(kernelAccepts(sql, dialect(entry[0])), "内核应拒: " + sql);
            assertFalse(SpecGrammarDriver.accepts(sql), "规格语法应拒: " + sql);
        }
    }

    /** 容差单向锁定：内核拒 + 规格语法收——容差方向（语法 ⊇ 内核）不得反转成差异。 */
    @Test
    void declaredTolerancesRemainOneSided() {
        for (final String[] entry : TOLERANCES) {
            final String sql = entry[1];
            assertFalse(kernelAccepts(sql, dialect(entry[0])), "容差前提失效（内核已收）: " + sql);
            assertTrue(SpecGrammarDriver.accepts(sql), "容差应保持语法收: " + sql);
        }
    }

    @Test
    void keyedPositivesDirectional() {
        for (final String[] entry : KEYED_POSITIVES) {
            final String sql = entry[1];
            assertTrue(kernelAccepts(sql, dialect(entry[0])), "内核应收（前提）: " + sql);
            assertTrue(SpecGrammarDriver.accepts(sql), "规格语法应收: " + sql);
        }
    }

    // ---------- 机械 ----------

    private static boolean kernelAccepts(final String sql, final DialectProfile profile) {
        try {
            BobaChop.parse(sql, profile);
            return true;
        } catch (final ParseException | UnsupportedSyntaxException | LexException ex) {
            return false;
        }
    }

    /** 矩阵探针模板（独立复述自 F4 AcceptanceMatrixTest，两处须同步）。 */
    private static String probeSql(final String word, final int slot) {
        switch (slot) {
            case 0:
                return "SELECT " + word + " FROM t";
            case 1:
                return "SELECT 1 " + word + " FROM t";
            case 2:
                return "SELECT 1 AS " + word + " FROM t";
            default:
                return "SELECT 1 FROM " + word;
        }
    }

    private static DialectProfile dialect(final String name) {
        if ("MYSQL".equals(name)) {
            return DialectProfile.MYSQL;
        }
        if ("POSTGRESQL".equals(name)) {
            return DialectProfile.POSTGRESQL;
        }
        return DialectProfile.ORACLE;
    }

    private static List<String> readCorpus(final String relative) throws IOException {
        final Path path = Paths.get("..", "corpus", relative);
        final List<String> out = new ArrayList<String>();
        for (final String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                out.add(trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
            }
        }
        return out;
    }
}
