package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.LexException;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;

/**
 * 对拍体系 v2 F4——接受面矩阵：方言词表 × 标识符槽位的机械化全枚举探针。
 *
 * <p>每个方言关键字在四个槽位各探一针：表达式首（{@code SELECT w FROM t}）、裸别名
 * （{@code SELECT 1 w FROM t}）、AS 别名（{@code SELECT 1 AS w FROM t}）、表名
 * （{@code SELECT 1 FROM w}），按三支异常分类学归为 ACCEPT / UNSUPPORTED / PARSE
 * / LEX。全矩阵与仓内快照 {@code acceptance-matrix.tsv} 逐行比对——任何接受面漂移
 * （新词入表、词升级保留、归类翻转）都会翻红一格，即"矩阵即门禁"。</p>
 *
 * <p>契约卫生断言（独立于快照）：保留字在三个纯标识符槽位（裸别名/AS 别名/表名）不得
 * ACCEPT（D9）；表达式首槽 ACCEPT 仅当消费为字面量等非标识符形态（NULL/TRUE/FALSE
 * 字面量与服务器同构——被当列引用才是违约）；非保留字不得 UNSUPPORTED（位置歧义词
 * 允许 PARSE——与服务器同拒）；任何探针不得 LEX（探针文本恒为合法词法）。</p>
 *
 * <p>快照再生成：{@code mvn test -pl boba-chop-diff -Dtest=AcceptanceMatrixTest
 * -Dmatrix.dump=<path>}， diff 后经人工裁决方可入库。</p>
 */
class AcceptanceMatrixTest {

    private static final String[] DIALECT_ORDER = {"MYSQL", "POSTGRESQL", "ORACLE"};
    private static final String[] SLOTS = {"expr", "bare-alias", "as-alias", "table"};
    private static final String SNAPSHOT = "/acceptance-matrix.tsv";
    private static final String HEADER = "# BobaChop 接受面矩阵（F4）：方言词表 × 标识符槽位，"
            + "再生成 -Dmatrix.dump=<path>；任何一格翻转须经裁决台账（LEDGER.md）记录";

    @Test
    void matrixMatchesCommittedSnapshot() throws IOException {
        final String generated = buildMatrix();
        maybeDump(generated);
        final String snapshot = readSnapshot();
        if (!snapshot.equals(generated)) {
            fail("接受面矩阵与仓内快照不一致（漂移须过裁决台账）——首处差异:\n"
                    + firstDifference(snapshot, generated)
                    + "\n再生成: mvn test -Dtest=AcceptanceMatrixTest -Dmatrix.dump=<path>");
        }
    }

    @Test
    void contractSanityReservedNeverAcceptedAsIdentifier() {
        final List<String> violations = new ArrayList<String>();
        for (final String dialectName : DIALECT_ORDER) {
            final DialectProfile profile = dialect(dialectName);
            for (final Kw word : profile.lexerConfig().keywords().allWords()) {
                for (int slot = 0; slot < SLOTS.length; slot++) {
                    final String verdict = probe(profile, word, slot);
                    final boolean reserved = profile.reserved(word);
                    if ("ACCEPT".equals(verdict) && reserved) {
                        // 表达式首允许字面量消费（NULL/TRUE/FALSE 与服务器同构）；列引用即违约
                        if (0 == slot) {
                            final Expr head = ((SelectStatement) BobaChop.parse(probeSql(word, 0), profile))
                                    .items().get(0).expr();
                            if (head instanceof ColumnRef) {
                                violations.add(dialectName + " " + word + " 保留字被当作列引用");
                            }
                        } else {
                            violations.add(dialectName + " " + word + " " + SLOTS[slot] + " 保留字被接受为标识符");
                        }
                    }
                    if ("UNSUPPORTED".equals(verdict) && !reserved) {
                        violations.add(dialectName + " " + word + " " + SLOTS[slot] + " 非保留字却白名单外拒绝");
                    }
                    if ("LEX".equals(verdict)) {
                        violations.add(dialectName + " " + word + " " + SLOTS[slot] + " 词法异常（探针恒为合法词法）");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "契约卫生违约:\n" + String.join("\n", violations));
    }

    // ---------- 矩阵机械 ----------

    static String buildMatrix() {
        final StringBuilder out = new StringBuilder(64 * 1024);
        out.append(HEADER).append('\n');
        for (final String dialectName : DIALECT_ORDER) {
            final DialectProfile profile = dialect(dialectName);
            for (final Kw word : profile.lexerConfig().keywords().allWords()) {
                for (int slot = 0; slot < SLOTS.length; slot++) {
                    out.append(dialectName).append('\t').append(word.name()).append('\t')
                            .append(SLOTS[slot]).append('\t').append(probe(profile, word, slot)).append('\n');
                }
            }
        }
        return out.toString();
    }

    private static DialectProfile dialect(final String name) {
        switch (name) {
            case "MYSQL":
                return DialectProfile.MYSQL;
            case "POSTGRESQL":
                return DialectProfile.POSTGRESQL;
            default:
                return DialectProfile.ORACLE;
        }
    }

    private static String probe(final DialectProfile profile, final Kw word, final int slot) {
        final String sql = probeSql(word, slot);
        try {
            BobaChop.parse(sql, profile);
            return "ACCEPT";
        } catch (final UnsupportedSyntaxException ex) {
            return "UNSUPPORTED";
        } catch (final ParseException ex) {
            return "PARSE";
        } catch (final LexException ex) {
            return "LEX";
        }
    }

    private static String probeSql(final Kw word, final int slot) {
        final String w = word.name().toLowerCase(java.util.Locale.ROOT);
        switch (slot) {
            case 0:
                return "SELECT " + w + " FROM t";
            case 1:
                return "SELECT 1 " + w + " FROM t";
            case 2:
                return "SELECT 1 AS " + w + " FROM t";
            default:
                return "SELECT 1 FROM " + w;
        }
    }

    // ---------- 快照读写 ----------

    private static String readSnapshot() throws IOException {
        final InputStream in = AcceptanceMatrixTest.class.getResourceAsStream(SNAPSHOT);
        if (null == in) {
            fail("快照缺失: " + SNAPSHOT + "（先 -Dmatrix.dump 再生成入库）");
            return "";
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] chunk = new byte[8192];
        for (int read = in.read(chunk); read >= 0; read = in.read(chunk)) {
            buffer.write(chunk, 0, read);
        }
        in.close();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void maybeDump(final String matrix) throws IOException {
        final String dump = System.getProperty("matrix.dump");
        if (null != dump && !dump.isEmpty()) {
            Files.write(Paths.get(dump), matrix.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String firstDifference(final String expected, final String actual) {
        final String[] expectedLines = expected.split("\n", -1);
        final String[] actualLines = actual.split("\n", -1);
        for (int i = 0; i < Math.max(expectedLines.length, actualLines.length); i++) {
            final String left = i < expectedLines.length ? expectedLines[i] : "<EOF>";
            final String right = i < actualLines.length ? actualLines[i] : "<EOF>";
            if (!left.equals(right)) {
                return "快照: " + left + "\n实际: " + right;
            }
        }
        return "（仅行数差异）";
    }
}
