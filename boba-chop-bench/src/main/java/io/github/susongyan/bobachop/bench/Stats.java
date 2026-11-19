package io.github.susongyan.bobachop.bench;

import java.util.Arrays;

/**
 * 基准统计工具：分位数、线性拟合（最小二乘 + R²）。
 *
 * <p>手写实现替代 JMH/JCommons（D6 零依赖）；样本量级（百万内）下排序分位数足够精确，
 * 方法学细节（计时开销、JIT 预热）见 DESIGN §12 与 {@code BenchMain} 头注。</p>
 */
final class Stats {

    private Stats() {
    }

    /** 就地排序后取分位数（p∈[0,1]，0=最小、0.5=中位、1=最大）。 */
    static long percentile(final long[] samples, final double p) {
        if (0 == samples.length) {
            throw new IllegalArgumentException("空样本");
        }
        Arrays.sort(samples);
        final double position = p * (samples.length - 1);
        final int low = (int) Math.floor(position);
        final int high = (int) Math.ceil(position);
        if (low == high) {
            return samples[low];
        }
        final double frac = position - low;
        return Math.round(samples[low] + frac * (samples[high] - samples[low]));
    }

    static long median(final long[] samples) {
        return percentile(samples, 0.5);
    }

    static double mean(final long[] samples) {
        double sum = 0;
        for (final long sample : samples) {
            sum += sample;
        }
        return sum / samples.length;
    }

    /** 线性拟合 y = slope·x + intercept；r2 为皮尔逊相关系数平方。 */
    static Fit fit(final long[] xs, final long[] ys) {
        if (xs.length != ys.length || xs.length < 2) {
            throw new IllegalArgumentException("拟合至少两点且 x/y 等长");
        }
        final double meanX = mean(xs);
        final double meanY = mean(ys);
        double sxy = 0;
        double sxx = 0;
        double syy = 0;
        for (int i = 0; i < xs.length; i++) {
            final double dx = xs[i] - meanX;
            final double dy = ys[i] - meanY;
            sxy += dx * dy;
            sxx += dx * dx;
            syy += dy * dy;
        }
        final double slope = sxy / sxx;
        final double intercept = meanY - slope * meanX;
        final double r2 = sxy * sxy / (sxx * syy);
        return new Fit(slope, intercept, r2);
    }

    /** 拟合结果：slope/intercept 与 y 单位一致，R² 无量纲。 */
    static final class Fit {

        final double slope;
        final double intercept;
        final double r2;

        Fit(final double slope, final double intercept, final double r2) {
            this.slope = slope;
            this.intercept = intercept;
            this.r2 = r2;
        }
    }
}
