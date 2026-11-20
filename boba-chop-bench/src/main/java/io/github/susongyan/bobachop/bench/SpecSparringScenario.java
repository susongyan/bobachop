package io.github.susongyan.bobachop.bench;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.bench.spec.SpecGrammarDriver;

/**
 * 同机陪练场景（F5，DESIGN §8 同机 KPI）：手写内核 vs ANTLR4 规格语法（BobaChopSpec.g4，
 * WHITELIST 可执行化）——同 JVM、同形态族、同窗口协议逐形态对打，报 p50/p99 与
 * p99 倍率（陪练/内核）。
 *
 * <p><b>公平性口径</b>：形态族与 {@link SteadyStateScenario} 同族同序；两引擎各自预热
 * （陪练含 ANTLR4 自适应 DFA 预热）后各开同等长度时间窗逐次采样；陪练侧逐次新建
 * lexer/parser（与内核逐次调用同口径，不作对象池化——比值只会更严）。陪练只当陪练
 * 不当裁判（D7）：本场景只报数字，接受面差异裁决权在 LEDGER 台账。</p>
 */
final class SpecSparringScenario implements Scenario {

    private static final int MAX_SAMPLES = 2_000_000;

    private final long windowNs;
    private final int lightWarmup;
    private final int heavyWarmup;
    private long sink;

    SpecSparringScenario(final int windowMs, final int lightWarmup, final int heavyWarmup) {
        this.windowNs = windowMs * 1_000_000L;
        this.lightWarmup = lightWarmup;
        this.heavyWarmup = heavyWarmup;
    }

    @Override
    public String name() {
        return "spec-sparring";
    }

    @Override
    public List<Metric> run() {
        final List<Metric> metrics = new ArrayList<Metric>();
        measure(metrics, "简单查询", Shapes.SIMPLE_SELECT, lightWarmup);
        measure(metrics, "JOIN+ORDER+LIMIT", Shapes.JOIN_SELECT, lightWarmup);
        measure(metrics, "派生表+子查询", Shapes.COMPOSITE_SELECT, lightWarmup);
        measure(metrics, "UPDATE", Shapes.UPDATE_WHERE, lightWarmup);
        measure(metrics, "IN(100)", Shapes.inListSelect(100), lightWarmup);
        measure(metrics, "IN(1000)", Shapes.inListSelect(1000), heavyWarmup);
        measure(metrics, "批量INSERT(100行)", Shapes.batchInsert(100), heavyWarmup);
        measure(metrics, "批量INSERT(1000行)", Shapes.batchInsert(1000), Math.max(50, heavyWarmup / 4));
        metrics.add(new Metric("sink", sink % 10, "count", null));
        return metrics;
    }

    private void measure(final List<Metric> metrics, final String label, final String sql, final int warmup) {
        final long[] kernel = sampleKernel(sql, warmup);
        final long[] sparring = sampleSpec(sql, warmup);
        metrics.add(new Metric(label + " 内核 p50", Stats.percentile(kernel, 0.50), "ns", null));
        metrics.add(new Metric(label + " 内核 p99", Stats.percentile(kernel, 0.99), "ns", null));
        metrics.add(new Metric(label + " 陪练 p50", Stats.percentile(sparring, 0.50), "ns", null));
        metrics.add(new Metric(label + " 陪练 p99", Stats.percentile(sparring, 0.99), "ns", null));
        // percentile 返回 long——先展宽再除，否则整数除法截断比值
        metrics.add(new Metric(label + " p99倍率(陪练/内核)", ratio1(Stats.percentile(sparring, 0.99),
                Stats.percentile(kernel, 0.99)), "x", null));
    }

    /** 倍率 = 分子/分母（double 除法，保留一位小数防渲染长尾）。 */
    private static double ratio1(final long numerator, final long denominator) {
        return Math.round(numerator * 10.0 / denominator) / 10.0;
    }

    private long[] sampleKernel(final String sql, final int warmup) {
        for (int i = 0; i < warmup; i++) {
            sink += BobaChop.parse(sql).stop();
        }
        final long[] samples = new long[MAX_SAMPLES];
        int n = 0;
        final long deadline = System.nanoTime() + windowNs;
        while (System.nanoTime() < deadline) {
            final long t0 = System.nanoTime();
            final io.github.susongyan.bobachop.ast.Statement statement = BobaChop.parse(sql);
            samples[n++] = System.nanoTime() - t0;
            sink += statement.stop();
        }
        return copyOf(samples, n);
    }

    private long[] sampleSpec(final String sql, final int warmup) {
        for (int i = 0; i < warmup; i++) {
            sink += SpecGrammarDriver.accepts(sql) ? 1 : 0;
        }
        final long[] samples = new long[MAX_SAMPLES];
        int n = 0;
        final long deadline = System.nanoTime() + windowNs;
        while (System.nanoTime() < deadline) {
            final long t0 = System.nanoTime();
            final boolean accepted = SpecGrammarDriver.accepts(sql);
            samples[n++] = System.nanoTime() - t0;
            if (!accepted) {
                throw new IllegalStateException("陪练解析失败（形态应为白名单内）: " + sql);
            }
        }
        return copyOf(samples, n);
    }

    private static long[] copyOf(final long[] samples, final int n) {
        final long[] used = new long[n];
        System.arraycopy(samples, 0, used, 0, n);
        return used;
    }
}
