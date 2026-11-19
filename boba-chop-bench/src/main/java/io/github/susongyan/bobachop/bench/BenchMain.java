package io.github.susongyan.bobachop.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.susongyan.bobachop.api.BobaChop;

/**
 * 基准入口（T9，DESIGN §8/§12）。用法：
 * <pre>
 *   java ...BenchMain [--quick] [--out &lt;dir&gt;]   # 全场景：冷启动(子JVM) + 首走 + 稳态 + 线性度
 *   java ...BenchMain --cold-child                # 内部协议：全新 JVM 首调计时
 * </pre>
 *
 * <p><b>方法与口径</b>：无 JMH 手写基准（D6 零依赖）。逐次计时含两次 nanoTime 开销
 * （Linux 约 20~40ns，µs 级样本误差 &lt;1%）；sink 累计防死码消除；ANTLR4 对照取 §1.1
 * 实测基线（不同硬件，仅量级对照，非同机比值）——同机 live 对拍待 ANTLR4 参考执行器接入。
 * KPI 判定只写入报告不作构建门禁（性能数字机器相关）。</p>
 */
public final class BenchMain {

    public static void main(final String[] args) throws Exception {
        if (0 < args.length && "--cold-child".equals(args[0])) {
            runColdChild();
            return;
        }
        boolean quick = false;
        Path outDir = Paths.get("target");
        for (int i = 0; i < args.length; i++) {
            if ("--quick".equals(args[i])) {
                quick = true;
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                outDir = Paths.get(args[++i]);
            }
        }
        final Map<String, List<Metric>> results = new LinkedHashMap<String, List<Metric>>();
        if (quick) {
            results.put("first-walk", new FirstWalkScenario(2_000, 300).run());
            results.put("steady-state", new SteadyStateScenario(300, 3_000, 300).run());
            results.put("linearity", new LinearityScenario(new int[]{50, 100, 200}, 20, 5).run());
        } else {
            results.put("cold-start", new ColdStartScenario(3).run());
            results.put("first-walk", new FirstWalkScenario(30_000, 20_000).run());
            results.put("steady-state", new SteadyStateScenario(2_000, 20_000, 2_000).run());
            results.put("linearity", new LinearityScenario(new int[]{100, 300, 1000}, 200, 30).run());
        }
        final String markdown = render(results);
        final String csv = renderCsv(results);
        Files.createDirectories(outDir);
        Files.write(outDir.resolve("bench-report.md"), markdown.getBytes(StandardCharsets.UTF_8));
        Files.write(outDir.resolve("bench-report.csv"), csv.getBytes(StandardCharsets.UTF_8));
        System.out.println(markdown);
        System.out.println("报告已写出: " + outDir.resolve("bench-report.md")
                + " / " + outDir.resolve("bench-report.csv"));
    }

    /** 子 JVM 协议：首调一次解析并上报（类加载全计入）。 */
    private static void runColdChild() {
        final long t0 = System.nanoTime();
        final long sink = BobaChop.parse(Shapes.SIMPLE_SELECT).stop();
        final long elapsed = System.nanoTime() - t0;
        System.out.println("cold-first-ns=" + elapsed);
        System.out.println("sink=" + sink);
    }

    private static String render(final Map<String, List<Metric>> results) {
        final StringBuilder md = new StringBuilder(4096);
        md.append("# BobaChop Bench 报告\n\n");
        md.append("- 日期: ").append(java.time.LocalDate.now()).append('\n');
        md.append("- JVM: ").append(System.getProperty("java.vm.name"))
                .append(' ').append(System.getProperty("java.version")).append('\n');
        md.append("- OS: ").append(System.getProperty("os.name"))
                .append(" / cores: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        md.append("- 口径: 手写基准（无 JMH）；逐次计时含 nanoTime 开销 ~20-40ns；sink 防死码消除\n\n");

        md.append("## 对照基线（§1.1 ANTLR4 实测；不同硬件，仅量级对照）\n\n");
        md.append("| 场景 | ANTLR4 基线 | 本内核 |\n|---|---|---|\n");
        md.append("| 冷启动(进程首调, MySQL 方言) | 621 ms | ")
                .append(find(results, "cold-start", "冷启动中位").render()).append(" |\n");
        md.append("| 简单查询稳态 | ~6 µs | ")
                .append(find(results, "steady-state", "简单查询 p50").render()).append(" |\n");
        md.append("| IN(1000) | ~1.2 ms | ")
                .append(find(results, "steady-state", "IN(1000) p50").render()).append(" |\n");
        md.append("| 批量 INSERT 1000 行 | ~7.8 ms (≈8µs/行) | ")
                .append(find(results, "steady-state", "批量INSERT(1000行) p50").render()).append(" |\n");
        md.append("| 首走新形态 第1次 | 6 µs ~ 80 ms | ")
                .append(find(results, "first-walk", "首走第1次").render()).append(" |\n");
        md.append("| 首走新形态 第2次 | 150 µs ~ 660 µs（持续 3~20x 劣化） | ")
                .append(find(results, "first-walk", "首走第2次").render()).append(" |\n\n");

        md.append("## 指标明细\n");
        for (final Map.Entry<String, List<Metric>> entry : results.entrySet()) {
            md.append("\n### ").append(entry.getKey()).append("\n\n| 指标 | 值 | 备注 |\n|---|---|---|\n");
            for (final Metric metric : entry.getValue()) {
                md.append("| ").append(metric.label).append(" | ").append(metric.render())
                        .append(" | ").append(null == metric.note ? "" : metric.note).append(" |\n");
            }
        }

        md.append("\n## KPI 判定（§8；不作构建门禁）\n\n");
        final Metric convergence = findPrefix(results, "first-walk", "收敛比 第");
        md.append("- 首走收敛（").append(convergence.label).append("）< 2x —— §3.1 方差 KPI 的收敛口径: ")
                .append(String.format("%.2f", convergence.value))
                .append("x → ").append(convergence.value < 2 ? "达标" : "未达标").append('\n');
        if (results.containsKey("linearity")) {
            final Metric r2 = find(results, "linearity", "R²");
            md.append("- 长 SQL 线性度 R² ≥ 0.99: ").append(String.format("%.4f", r2.value))
                    .append(" → ").append(r2.value >= 0.99 ? "达标" : "未达标").append('\n');
        }
        md.append("- 稳态 p99 较 ANTLR4 ≥5x：待同机 live 对拍（参考执行器接入后补测；§1.1 基线非同机）\n");
        return md.toString();
    }

    private static Metric find(final Map<String, List<Metric>> results, final String scenario, final String label) {
        for (final Metric metric : results.get(scenario)) {
            if (metric.label.equals(label)) {
                return metric;
            }
        }
        throw new IllegalStateException("指标缺失: " + scenario + " / " + label);
    }

    private static Metric findPrefix(final Map<String, List<Metric>> results, final String scenario,
                                     final String labelPrefix) {
        for (final Metric metric : results.get(scenario)) {
            if (metric.label.startsWith(labelPrefix)) {
                return metric;
            }
        }
        throw new IllegalStateException("指标缺失: " + scenario + " / " + labelPrefix + "*");
    }

    private static String renderCsv(final Map<String, List<Metric>> results) {
        final StringBuilder csv = new StringBuilder(2048);
        csv.append("scenario,label,value,unit,note\n");
        for (final Map.Entry<String, List<Metric>> entry : results.entrySet()) {
            for (final Metric metric : entry.getValue()) {
                csv.append(entry.getKey()).append(",\"").append(metric.label).append("\",")
                        .append(String.format("%.0f", metric.value)).append(',').append(metric.unit)
                        .append(",\"").append(null == metric.note ? "" : metric.note).append("\"\n");
            }
        }
        return csv.toString();
    }

    private BenchMain() {
    }
}
