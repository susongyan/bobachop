package io.github.susongyan.bobachop.bench;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.api.BobaChop;

/**
 * 长_SQL_线性度场景（DESIGN §12）：批量 INSERT 在给定行数档位上各取 {@code reps} 次
 * 计时的中位数，对"行数→耗时"做最小二乘拟合，报告每行边际成本与 R²。
 *
 * <p>KPI：R² ≥ 0.99（§8）。ANTLR4 基线约 8µs/行（§1.1，不同硬件量级对照）。</p>
 */
final class LinearityScenario implements Scenario {

    private final int[] rowsPerStep;
    private final int warmup;
    private final int reps;

    LinearityScenario(final int[] rowsPerStep, final int warmup, final int reps) {
        this.rowsPerStep = rowsPerStep.clone();
        this.warmup = warmup;
        this.reps = reps;
    }

    @Override
    public String name() {
        return "linearity";
    }

    @Override
    public List<Metric> run() {
        final long[] xs = new long[rowsPerStep.length];
        final long[] ys = new long[rowsPerStep.length];
        final List<Metric> metrics = new ArrayList<Metric>();
        long sink = 0;
        for (int i = 0; i < rowsPerStep.length; i++) {
            final int rows = rowsPerStep[i];
            final String sql = Shapes.batchInsert(rows);
            for (int w = 0; w < warmup; w++) {
                sink += BobaChop.parse(sql).stop();
            }
            final long[] samples = new long[reps];
            for (int r = 0; r < reps; r++) {
                final long t0 = System.nanoTime();
                sink += BobaChop.parse(sql).stop();
                samples[r] = System.nanoTime() - t0;
            }
            xs[i] = rows;
            ys[i] = Stats.median(samples);
            metrics.add(new Metric("批量INSERT " + rows + " 行中位", ys[i], "ns", null));
        }
        final Stats.Fit fit = Stats.fit(xs, ys);
        metrics.add(new Metric("每行边际成本", fit.slope / 1000.0, "µs/row", null));
        metrics.add(new Metric("拟合截距", fit.intercept / 1000.0, "µs", null));
        metrics.add(new Metric("R²", fit.r2, "", fit.r2 >= 0.99 ? "KPI≥0.99 达标" : "KPI≥0.99 未达标"));
        metrics.add(new Metric("sink(防消除)", sink % 10, "count", null));
        return metrics;
    }
}
