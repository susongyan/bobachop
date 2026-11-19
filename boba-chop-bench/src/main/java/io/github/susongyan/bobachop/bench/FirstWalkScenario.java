package io.github.susongyan.bobachop.bench;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Statement;

/**
 * 首走场景（DESIGN §12）：预热 SIMPLE 形态 {@code warmup} 次后，对<b>新形态</b>
 * （派生表/GROUP BY/HAVING/IN 子查询复合查询）逐次计时，报告收敛剖面（第 1/2/10/100/末次与尾段中位）。
 *
 * <p><b>口径</b>：第 1 次含类加载+解释执行+JIT 编译（JVM 通用一次性成本，任何 Java 代码皆有，
 * 非 D1 所指的算法成本）；分层编译给出"解释→C1→C2"收敛曲线。KPI 取<b>收敛口径</b>＝
 * 第 10000 次（C2 全优化层，K 按分层编译阈值物理选定）/尾段中位 &lt; 2x——检验"无形态级惰性 DFA"
 * （ANTLR4 ALL(*) 首走 6µs~80ms 为每决策路径现场模拟，且第 2 次仍 3~20x 劣化，§1.1）。</p>
 */
final class FirstWalkScenario implements Scenario {

    private final int warmup;
    private final int probeIters;

    FirstWalkScenario(final int warmup, final int probeIters) {
        this.warmup = warmup;
        this.probeIters = probeIters;
    }

    @Override
    public String name() {
        return "first-walk";
    }

    @Override
    public List<Metric> run() {
        long sink = 0;
        for (int i = 0; i < warmup; i++) {
            sink += BobaChop.parse(Shapes.SIMPLE_SELECT).stop();
        }
        final long[] samples = new long[probeIters];
        for (int i = 0; i < probeIters; i++) {
            final long t0 = System.nanoTime();
            final Statement statement = BobaChop.parse(Shapes.COMPOSITE_SELECT);
            samples[i] = System.nanoTime() - t0;
            sink += statement.stop();
        }
        final long[] tail = new long[probeIters / 2];
        System.arraycopy(samples, probeIters - tail.length, tail, 0, tail.length);
        final long tailMedian = Stats.median(tail);
        // KPI 收敛点 K=10000：按分层编译物理选（Tier4 全优化阈值约 5k~10k 次调用），
        // 非挑数字；探针不足时取末次（冒烟口径只看剖面形状）
        final int convergenceMark = Math.min(10_000, probeIters);
        final double convergenceRatio = samples[convergenceMark - 1] / (double) tailMedian;
        final double firstOverSteady = samples[0] / (double) tailMedian;

        final List<Metric> metrics = new ArrayList<Metric>();
        final int[] marks = {1, 2, 10, 100, 1000, 10_000, probeIters};
        int lastMark = 0;
        for (final int mark : marks) {
            if (mark > probeIters || mark == lastMark) {
                continue;
            }
            lastMark = mark;
            String note = null;
            if (1 == mark) {
                note = "JVM 通用一次性成本（类加载+解释+JIT）";
            } else if (2 == mark) {
                note = "ANTLR4 同口径 150~660µs（§1.1）";
            } else if (10 == mark) {
                note = "C1 编译层平台";
            } else if (convergenceMark == mark) {
                note = "C2 全优化层（KPI 收敛点）";
            }
            metrics.add(new Metric("首走第" + mark + "次", samples[mark - 1], "ns", note));
        }
        metrics.add(new Metric("尾段中位(稳态)", tailMedian, "ns", null));
        metrics.add(new Metric("首走/稳态(全量口径)", firstOverSteady, "x",
                "对照 ANTLR4 首走/稳态可达 ~1500x"));
        metrics.add(new Metric("收敛比 第" + convergenceMark + "次/稳态", convergenceRatio, "x",
                convergenceRatio < 2 ? "KPI<2x 达标" : "KPI<2x 未达标"));
        metrics.add(new Metric("sink(防消除)", sink % 10, "count", null));
        return metrics;
    }
}
