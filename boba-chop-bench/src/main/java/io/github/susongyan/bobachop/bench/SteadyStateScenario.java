package io.github.susongyan.bobachop.bench;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.api.BobaChop;

/**
 * 稳态场景（DESIGN §12）：每形态预热后取 {@code windowMs} 毫秒窗口内的逐次样本，
 * 报告吞吐与 p50/p99。
 *
 * <p>形态族含 §1.1 对照三件（简单查询 / IN(1000) / 批量 INSERT 1000 行）加 JOIN、
 * 复合查询、UPDATE。逐次计时含两次 nanoTime 调用开销（Linux 约 20~40ns），
 * 相对 µs 级样本误差 &lt;1%，报告按此口径。</p>
 */
final class SteadyStateScenario implements Scenario {

    private static final int MAX_SAMPLES = 2_000_000;

    private final long windowNs;
    private final int lightWarmup;
    private final int heavyWarmup;

    SteadyStateScenario(final int windowMs, final int lightWarmup, final int heavyWarmup) {
        this.windowNs = windowMs * 1_000_000L;
        this.lightWarmup = lightWarmup;
        this.heavyWarmup = heavyWarmup;
    }

    @Override
    public String name() {
        return "steady-state";
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
        return metrics;
    }

    private void measure(final List<Metric> metrics, final String label, final String sql, final int warmup) {
        long sink = 0;
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
        final long[] used = new long[n];
        System.arraycopy(samples, 0, used, 0, n);
        final double opsPerSec = n / (windowNs / 1_000_000_000.0);
        metrics.add(new Metric(label + " 吞吐", opsPerSec, "ops/s", null));
        metrics.add(new Metric(label + " p50", Stats.percentile(used, 0.50), "ns", null));
        metrics.add(new Metric(label + " p99", Stats.percentile(used, 0.99), "ns", null));
        metrics.add(new Metric(label + " sink", sink % 10, "count", null));
    }
}
