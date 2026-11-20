package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.LexException;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;

/**
 * 对拍体系 v2 F3 门禁：变异模糊 + 元性质。
 *
 * <p><b>封闭性（无条件，任意规模）</b>：语料/生成器语句经 {@link Mutator} 变异 + 病态
 * 构造（深嵌套/超长/未闭合），解析结果必须落在三支之一——接受，或
 * LexException/ParseException/UnsupportedSyntaxException 之一；其余任何 Throwable
 * （含 StackOverflowError、越界、断言）= 封闭性被破坏 = BobaPour REJECT 路径会裸崩。</p>
 *
 * <p><b>元性质（限变异语料规模）</b>：被接受的变异体上断言三件——① 确定性：同输入
 * 两次解析全等（AstDiff.compare 含偏移——影子回放/决策缓存的前提）；② 往返：
 * render→重解析 compareStructure 全等 + 打印幂等（F2 保证从生成器空间推广到接受面
 * 空间）；③ SpanInvariants 六条（F1 推广到变异空间）。递归消费者（渲染器/遍历器）
 * 的深树预算是台账观察项，故元性质仅断言于 ≤8KB 的常规规模输入；迭代路径合法产出
 * 的深树（病态构造中的长链）只断言封闭性。</p>
 */
final class MutationFuzzTest {

    private static final long DEFAULT_SEED = 20261120L;
    /** 每条种子的变异体数；失败上报上限。 */
    private static final int DEFAULT_MUTANTS = 10;
    private static final int MAX_REPORTED = 8;
    /** 元性质断言的输入规模上限（递归消费者深树预算台账观察项）。 */
    private static final int META_TEXT_LIMIT = 8192;

    /** 语料 → 方言（与 SpanInvariantsCorpusTest 同一清单）。 */
    private static final String[][] CORPORA = {
        {"mysql", "select-v1.txt"}, {"mysql", "dml-v1.txt"},
        {"pg", "select-v1.txt"}, {"oracle", "select-v1.txt"},
    };

    private static final DialectProfile[] PROFILES = {
        DialectProfile.MYSQL, DialectProfile.POSTGRESQL, DialectProfile.ORACLE,
    };

    @Test
    void 变异封闭性与元性质全绿() throws IOException {
        final long seed = Long.getLong("fuzz.seed", DEFAULT_SEED);
        final int mutantsPerSeed = Integer.getInteger("fuzz.mutants", DEFAULT_MUTANTS);
        final List<String> failures = new ArrayList<String>();
        final Random random = new Random(seed);
        final Mutator mutator = new Mutator(random);
        int total = 0;
        int accepted = 0;
        // 种子 = 全部形态语料（真实形态分布）+ 三方言生成语句（契约全空间采样）
        for (final String[] corpus : CORPORA) {
            final DialectProfile profile = profileOf(corpus[0]);
            final Path path = Paths.get("..", "corpus", corpus[0], corpus[1]);
            assertTrue(Files.exists(path), "语料文件缺失: " + path.toAbsolutePath());
            for (final String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                final String sql = line.trim();
                if (sql.isEmpty() || sql.startsWith("--")) {
                    continue;
                }
                accepted += mutateAndCheck(sql, profile, mutator, random, mutantsPerSeed, failures, total);
                total++;
            }
        }
        for (final DialectProfile profile : PROFILES) {
            final AstGenerator generator = new AstGenerator(profile,
                    new Random(seed + profile.name().hashCode()));
            final AstRenderer renderer = new AstRenderer(profile);
            for (int i = 0; i < 100; i++) {
                accepted += mutateAndCheck(renderer.render(generator.statement()), profile,
                        mutator, random, mutantsPerSeed, failures, total);
                total++;
            }
        }
        assertTrue(failures.isEmpty(), "变异模糊失败 " + failures.size() + "/" + total
                + "（复现: -Dfuzz.seed=" + seed + " -Dfuzz.mutants=" + mutantsPerSeed + "）:\n"
                + String.join("\n", failures));
    }

    @Test
    void 病态构造封闭性() {
        final List<String> failures = new ArrayList<String>();
        final String[][] hostile = {
            {"MySQL", "SELECT " + repeat("(", 50000) + "1" + repeat(")", 50000)},
            {"MySQL", "SELECT " + repeat("- ", 50000) + "1"},
            {"MySQL", "SELECT * FROM " + repeat("(SELECT * FROM ", 20000) + "t x" + repeat(") d", 20000)},
            {"MySQL", "SELECT " + repeat("(SELECT (", 20000) + "1" + repeat("))", 20000)},
            {"MySQL", "SELECT " + repeat("CASE WHEN 1 THEN ", 20000) + "1" + repeat(" ELSE 1 END", 20000)},
            {"MySQL", "SELECT 1 AND " + repeat("1 AND ", 20000) + "1"},
            {"MySQL", "SELECT 1 IN (" + repeat("1, ", 20000) + "1)"},
            {"MySQL", "SELECT 'abc"},
            {"MySQL", "SELECT /* abc"},
            {"PostgreSQL", "SELECT " + repeat("/* ", 50000)},
            {"Oracle", "SELECT " + repeat("(", 50000) + "1" + repeat(")", 50000)},
            {"MySQL", repeat("a", 100000)},
            {"MySQL", ""},
            {"MySQL", ";;"},
            {"MySQL", "; SELECT 1"},
        };
        for (final String[] row : hostile) {
            checkClosure(row[1], profileOf(row[0]), row[0] + " 病态构造", failures);
        }
        assertTrue(failures.isEmpty(), "病态构造封闭性被破坏:\n" + String.join("\n", failures));
    }

    /** 一条种子 → 若干变异体：封闭性逐条，元性质在被接受的常规规模变异体上。返回接受数。 */
    private static int mutateAndCheck(final String seedSql, final DialectProfile profile,
            final Mutator mutator, final Random random, final int mutantsPerSeed,
            final List<String> failures, final int seedIndex) {
        int accepted = 0;
        for (int j = 0; j < mutantsPerSeed && failures.size() < MAX_REPORTED; j++) {
            final String mutant = mutator.mutate(seedSql, profile, 1 + random.nextInt(3));
            accepted += checkMutant(mutant, profile, "#" + seedIndex + "." + j, failures);
        }
        return accepted;
    }

    /** 单条输入：封闭性（无条件）+ 元性质（接受且常规规模）。返回 1 若被接受。 */
    private static int checkMutant(final String sql, final DialectProfile profile,
            final String label, final List<String> failures) {
        final Statement first;
        try {
            first = BobaChop.parse(sql, profile);
        } catch (LexException | ParseException | UnsupportedSyntaxException e) {
            return 0;
        } catch (final Throwable t) {
            failures.add("[" + profile.name() + label + "] 三支外异常 " + t + "\n  " + excerpt(sql));
            return 0;
        }
        if (sql.length() <= META_TEXT_LIMIT) {
            metaProperties(first, sql, profile, label, failures);
        }
        return 1;
    }

    private static void checkClosure(final String sql, final DialectProfile profile,
            final String label, final List<String> failures) {
        try {
            BobaChop.parse(sql, profile);
        } catch (LexException | ParseException | UnsupportedSyntaxException e) {
            return; // 分类拒绝 = 合法出路
        } catch (final Throwable t) {
            failures.add("[" + label + "] 三支外异常 " + t + "\n  " + excerpt(sql));
        }
    }

    private static void metaProperties(final Statement first, final String sql,
            final DialectProfile profile, final String label, final List<String> failures) {
        // ① 确定性：同输入两次解析全等（含偏移）
        final Statement again = BobaChop.parse(sql, profile);
        final List<DiffDifference> drift = AstDiff.compare(first, again);
        if (!drift.isEmpty()) {
            failures.add("[" + profile.name() + label + "] 两次解析不等 " + drift + "\n  " + excerpt(sql));
            return;
        }
        // ②③ 往返 + 打印幂等 + 偏移不变量（F2/F1 元性质推广到变异空间）
        final AstRenderer renderer = new AstRenderer(profile);
        final String rendered = renderer.render(first);
        final Statement reparsed;
        try {
            reparsed = BobaChop.parse(rendered, profile);
        } catch (final Throwable t) {
            failures.add("[" + profile.name() + label + "] 渲染产物不可解析 " + t
                    + "\n  " + excerpt(sql) + "\n  渲染: " + excerpt(rendered));
            return;
        }
        final List<DiffDifference> structural = AstDiff.compareStructure(first, reparsed);
        if (!structural.isEmpty()) {
            failures.add("[" + profile.name() + label + "] 往返结构不等 " + structural
                    + "\n  " + excerpt(sql) + "\n  渲染: " + excerpt(rendered));
            return;
        }
        if (!rendered.equals(renderer.render(reparsed))) {
            failures.add("[" + profile.name() + label + "] 打印不幂等"
                    + "\n  一次: " + excerpt(rendered) + "\n  二次: " + excerpt(renderer.render(reparsed)));
            return;
        }
        final List<String> spans = SpanInvariants.check(first, sql, profile);
        if (!spans.isEmpty()) {
            failures.add("[" + profile.name() + label + "] 偏移不变量违规 " + spans + "\n  " + excerpt(sql));
        }
    }

    private static DialectProfile profileOf(final String tag) {
        return "mysql".equals(tag) || "MySQL".equals(tag) ? DialectProfile.MYSQL
                : "pg".equals(tag) || "PostgreSQL".equals(tag) ? DialectProfile.POSTGRESQL
                : DialectProfile.ORACLE;
    }

    private static String excerpt(final String sql) {
        return sql.length() <= 160 ? sql : sql.substring(0, 160) + "…(" + sql.length() + " 字符)";
    }

    private static String repeat(final String s, final int n) {
        final StringBuilder out = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            out.append(s);
        }
        return out.toString();
    }
}
