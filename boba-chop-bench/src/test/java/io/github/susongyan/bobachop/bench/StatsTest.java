package io.github.susongyan.bobachop.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 统计工具黄金用例：分位数插值与线性拟合 R²。 */
final class StatsTest {

    @Test
    void 分位数与中位() {
        final long[] samples = {10, 20, 30, 40};
        assertEquals(10, Stats.percentile(samples.clone(), 0));
        assertEquals(25, Stats.percentile(samples.clone(), 0.5));
        assertEquals(40, Stats.percentile(samples.clone(), 1));
        assertEquals(4, Stats.median(new long[]{4}));
        assertThrows(IllegalArgumentException.class, () -> Stats.percentile(new long[0], 0.5));
    }

    @Test
    void 完美线性R2为1() {
        final long[] xs = {100, 300, 1000};
        final long[] ys = {100_000, 300_000, 1_000_000};
        final Stats.Fit fit = Stats.fit(xs, ys);
        assertEquals(1000.0, fit.slope, 1e-9);
        assertEquals(0.0, fit.intercept, 1e-6);
        assertEquals(1.0, fit.r2, 1e-12);
    }

    @Test
    void 非线性R2低于1() {
        final long[] xs = {1, 2, 3, 4};
        final long[] ys = {1, 8, 27, 64};
        assertTrue(Stats.fit(xs, ys).r2 < 0.95);
        assertThrows(IllegalArgumentException.class, () -> Stats.fit(new long[]{1}, new long[]{1}));
    }
}
