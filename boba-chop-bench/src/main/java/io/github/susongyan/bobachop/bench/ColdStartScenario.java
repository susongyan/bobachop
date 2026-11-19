package io.github.susongyan.bobachop.bench;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 冷启动场景（DESIGN §12：进程首调）：父进程连启 {@code repeats} 个全新子 JVM，
 * 子进程对第一条 SQL 计时（词法/语法/AST 类加载全计入），取中位。
 *
 * <p>ANTLR4 基线：MySQL 方言 621ms（§1.1，ATN 反序列化 + 生成类加载；不同硬件量级对照）。
 * 子进程协议：stdout 首行 {@code cold-first-ns=<ns>}，次行 {@code sink=<n>}（防死码消除）。</p>
 */
final class ColdStartScenario implements Scenario {

    private final int repeats;

    ColdStartScenario(final int repeats) {
        this.repeats = repeats;
    }

    @Override
    public String name() {
        return "cold-start";
    }

    @Override
    public List<Metric> run() {
        final long[] samples = new long[repeats];
        final List<Metric> metrics = new ArrayList<Metric>();
        try {
            for (int i = 0; i < repeats; i++) {
                samples[i] = spawnChildAndMeasure();
                metrics.add(new Metric("冷启第" + (i + 1) + "次(全新JVM)", samples[i], "ns", null));
            }
        } catch (final Exception e) {
            throw new IllegalStateException("冷启动子进程失败", e);
        }
        metrics.add(new Metric("冷启动中位", Stats.median(samples), "ns", null));
        return metrics;
    }

    private long spawnChildAndMeasure() throws Exception {
        final String java = System.getProperty("java.home") + "/bin/java";
        final String classpath = System.getProperty("java.class.path");
        final ProcessBuilder builder = new ProcessBuilder(java, "-cp", classpath,
                BenchMain.class.getName(), "--cold-child");
        builder.redirectErrorStream(false);
        final Process process = builder.start();
        long firstNs = -1;
        BufferedReader stdout = null;
        try {
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while (null != (line = stdout.readLine())) {
                if (line.startsWith("cold-first-ns=")) {
                    firstNs = Long.parseLong(line.substring("cold-first-ns=".length()).trim());
                }
            }
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("冷启动子进程超时");
            }
        } finally {
            if (null != stdout) {
                stdout.close();
            }
            process.destroy();
        }
        if (firstNs < 0) {
            throw new IllegalStateException("子进程未上报 cold-first-ns");
        }
        return firstNs;
    }
}
