package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;

/**
 * 对拍体系 v2 F2 门禁：规格生成器 + canonical 渲染器 + 往返对拍。
 *
 * <p>每语句三断言（同一规格、两种机制的 N 版本校验）：</p>
 * <ol>
 *   <li>{@code parse(render(ast)) ≡ ast}（AstDiff.compareStructure 跳位置全等——含
 *       ParameterMarker.index 逐标记相等，即"访问序=文本序"的批量验证）；</li>
 *   <li>{@code render(parse(render(ast))) == render(ast)}（打印规范化幂等）；</li>
 *   <li>重解析树过 SpanInvariants 六条（F1 在生成语料上的复用，偏移契约随样本量放大）。</li>
 * </ol>
 *
 * <p>确定性：默认种子固定（CI 不闪、失败可复现：种子+方言+序号）；夜间扫量
 * {@code -Dgen.seed=... -Dgen.count=100000}。失败清单带方言/序号/SQL 现场与复现参数。</p>
 */
final class RoundTripTest {

    private static final long DEFAULT_SEED = 20261120L;
    private static final int DEFAULT_COUNT = 5000;
    private static final int MAX_REPORTED = 8;

    @Test
    void 三方言生成往返对拍全绿() {
        final long seed = Long.getLong("gen.seed", DEFAULT_SEED);
        final int count = Integer.getInteger("gen.count", DEFAULT_COUNT);
        final List<String> failures = new ArrayList<String>();
        final DialectProfile[] profiles = {DialectProfile.MYSQL, DialectProfile.POSTGRESQL, DialectProfile.ORACLE};
        for (final DialectProfile profile : profiles) {
            final AstGenerator generator = new AstGenerator(profile, new Random(seed + profile.name().hashCode()));
            final AstRenderer renderer = new AstRenderer(profile);
            assertTrue(generator.wordPoolSize() >= 5,
                    profile.name() + " 非保留词池过小: " + generator.wordPoolSize());
            for (int i = 0; i < count && failures.size() < MAX_REPORTED; i++) {
                checkOne(profile, generator, renderer, i, failures);
            }
        }
        assertTrue(failures.isEmpty(), "往返对拍失败（复现: -Dgen.seed=" + seed + " -Dgen.count=" + count + "）:\n"
                + String.join("\n", failures));
    }

    private static void checkOne(final DialectProfile profile, final AstGenerator generator,
            final AstRenderer renderer, final int index, final List<String> failures) {
        final Statement generated = generator.statement();
        final String sql = renderer.render(generated);
        final Statement reparsed;
        try {
            reparsed = BobaChop.parse(sql, profile);
        } catch (final RuntimeException e) {
            failures.add("[" + profile.name() + "#" + index + "] 白名单内语句被拒 " + e + "\n  " + sql);
            return;
        }
        final List<DiffDifference> diffs = AstDiff.compareStructure(generated, reparsed);
        if (!diffs.isEmpty()) {
            failures.add("[" + profile.name() + "#" + index + "] 结构不等 " + diffs + "\n  " + sql);
            return;
        }
        final String again = renderer.render(reparsed);
        if (!sql.equals(again)) {
            failures.add("[" + profile.name() + "#" + index + "] 打印不幂等\n  一次: " + sql + "\n  二次: " + again);
            return;
        }
        final List<String> spans = SpanInvariants.check(reparsed, sql, profile);
        if (!spans.isEmpty()) {
            failures.add("[" + profile.name() + "#" + index + "] 偏移不变量违规 " + spans + "\n  " + sql);
        }
    }
}
