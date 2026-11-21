package io.github.susongyan.bobachop.diff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.CompoundSelect;
import io.github.susongyan.bobachop.ast.DeleteStatement;
import io.github.susongyan.bobachop.ast.InsertStatement;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.UpdateStatement;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.LexException;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;

/**
 * 语料剖析器（拒绝率/形态分布观测，非构建门禁——KPI 与拒绝率均机器与语料相关，
 * 同 bench 口径只报数字）。面向集成期生产语料接入：
 *
 * <pre>
 *   java ...CorpusProfiler &lt;语料目录&gt; [--dialect mysql|postgresql|oracle] [--out 报告.md]
 * </pre>
 *
 * <p>输入约定与 corpus/ 相同：目录递归收集 {@code *.txt}/*.sql，每行一条语句，
 * 空行与 {@code --} 注释行跳过、尾分号剥去（多行语句需先行归一化，v1 不处理）。
 * 生产实例语料只进内网 gitignored 的 {@code corpus-internal/}，永不进公开仓。</p>
 *
 * <p>产出：三支异常分类计数与占比（Unsupported=合法方言语法契约无锚、Parse=方言
 * 语法错误、Lex=词法非法）、Top-N 拒绝消息前缀（白名单缺口定位直接输入）、
 * ACCEPT 形态分布（语句类型）与语句长度分布。</p>
 */
public final class CorpusProfiler {

    private static final int TOP_N = 20;
    private static final int MESSAGE_PREFIX = 80;

    private CorpusProfiler() {
    }

    public static void main(final String[] args) throws IOException {
        if (1 > args.length) {
            System.err.println("用法: CorpusProfiler <语料目录> [--dialect mysql|postgresql|oracle] [--out 报告.md]");
            System.exit(2);
        }
        final Path root = Paths.get(args[0]);
        DialectProfile profile = DialectProfile.MYSQL;
        Path out = null;
        for (int i = 1; i < args.length; i++) {
            if ("--dialect".equals(args[i]) && i + 1 < args.length) {
                profile = profileOf(args[++i]);
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                out = Paths.get(args[++i]);
            }
        }
        final String report = profile(root, profile);
        System.out.println(report);
        if (null != out) {
            Files.createDirectories(out.getParent());
            Files.write(out, report.getBytes(StandardCharsets.UTF_8));
            System.out.println("报告已写出: " + out);
        }
    }

    /** 剖析一个语料目录，返回 markdown 报告。 */
    public static String profile(final Path root, final DialectProfile profile) throws IOException {
        final List<String> statements = readStatements(root);
        int accept = 0;
        int unsupported = 0;
        int parse = 0;
        int lex = 0;
        int other = 0;
        final Map<String, Integer> unsupportedMessages = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> parseMessages = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> shapes = new LinkedHashMap<String, Integer>();
        final List<Integer> lengths = new ArrayList<Integer>();
        for (final String sql : statements) {
            lengths.add(sql.length());
            try {
                final Statement stmt = BobaChop.parse(sql, profile);
                accept++;
                bump(shapes, shapeOf(stmt));
            } catch (final UnsupportedSyntaxException e) {
                unsupported++;
                bump(unsupportedMessages, prefix(e.getMessage()));
            } catch (final ParseException e) {
                parse++;
                bump(parseMessages, prefix(e.getMessage()));
            } catch (final LexException e) {
                lex++;
                bump(parseMessages, prefix(e.getMessage()));
            } catch (final RuntimeException e) {
                other++;
                bump(parseMessages, "UNEXPECTED " + e.getClass().getSimpleName());
            }
        }
        return render(root, profile, statements.size(), accept, unsupported, parse, lex, other,
                unsupportedMessages, parseMessages, shapes, lengths);
    }

    private static List<String> readStatements(final Path root) throws IOException {
        final List<Path> files = new ArrayList<Path>();
        if (Files.isRegularFile(root)) {
            files.add(root);
        } else {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> {
                            final String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".txt") || n.endsWith(".sql");
                        })
                        .sorted()
                        .forEach(files::add);
            }
        }
        final List<String> statements = new ArrayList<String>();
        for (final Path file : files) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                if (line.endsWith(";")) {
                    line = line.substring(0, line.length() - 1).trim();
                }
                if (!line.isEmpty()) {
                    statements.add(line);
                }
            }
        }
        return statements;
    }

    private static String shapeOf(final Statement stmt) {
        if (stmt instanceof CompoundSelect) {
            return "SELECT(UNION)";
        }
        if (stmt instanceof SelectStatement) {
            return "SELECT";
        }
        if (stmt instanceof InsertStatement) {
            return "INSERT";
        }
        if (stmt instanceof UpdateStatement) {
            return "UPDATE";
        }
        if (stmt instanceof DeleteStatement) {
            return "DELETE";
        }
        return "其他";
    }

    private static DialectProfile profileOf(final String name) {
        if ("postgresql".equalsIgnoreCase(name) || "pg".equalsIgnoreCase(name)) {
            return DialectProfile.POSTGRESQL;
        }
        if ("oracle".equalsIgnoreCase(name)) {
            return DialectProfile.ORACLE;
        }
        return DialectProfile.MYSQL;
    }

    private static String prefix(final String message) {
        if (null == message) {
            return "(null)";
        }
        final String firstLine = message.split("\n")[0];
        return firstLine.length() > MESSAGE_PREFIX ? firstLine.substring(0, MESSAGE_PREFIX) : firstLine;
    }

    private static void bump(final Map<String, Integer> counts, final String key) {
        final Integer old = counts.get(key);
        counts.put(key, null == old ? 1 : old + 1);
    }

    private static String render(final Path root, final DialectProfile profile, final int total,
                                 final int accept, final int unsupported, final int parse, final int lex,
                                 final int other, final Map<String, Integer> unsupportedMessages,
                                 final Map<String, Integer> parseMessages, final Map<String, Integer> shapes,
                                 final List<Integer> lengths) {
        final StringBuilder md = new StringBuilder(2048);
        md.append("# BobaChop 语料剖析报告\n\n");
        md.append("- 语料: ").append(root).append("  方言: ").append(profile).append('\n');
        md.append("- 语句数: ").append(total).append('\n');
        md.append("- 接受: ").append(accept).append(" (").append(pct(accept, total)).append("%)");
        md.append("  Unsupported: ").append(unsupported).append(" (").append(pct(unsupported, total)).append("%)");
        md.append("  Parse: ").append(parse).append(" (").append(pct(parse, total)).append("%)");
        md.append("  Lex: ").append(lex).append("  意外: ").append(other).append('\n');
        md.append("\n## Top Unsupported 构造（白名单缺口定位输入）\n\n");
        appendTop(md, unsupportedMessages);
        md.append("\n## Top Parse/Lex 失败\n\n");
        appendTop(md, parseMessages);
        md.append("\n## 接受面形态分布\n\n");
        final List<Map.Entry<String, Integer>> shapeEntries = new ArrayList<Map.Entry<String, Integer>>(shapes.entrySet());
        shapeEntries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        for (final Map.Entry<String, Integer> entry : shapeEntries) {
            md.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        md.append("\n## 语句长度（字符）\n\n");
        Collections.sort(lengths);
        md.append("- p50: ").append(percentile(lengths, 0.50));
        md.append("  p95: ").append(percentile(lengths, 0.95));
        md.append("  max: ").append(lengths.isEmpty() ? 0 : lengths.get(lengths.size() - 1)).append('\n');
        return md.toString();
    }

    private static void appendTop(final StringBuilder md, final Map<String, Integer> messages) {
        if (messages.isEmpty()) {
            md.append("- （无）\n");
            return;
        }
        final List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(messages.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        int rank = 1;
        for (final Map.Entry<String, Integer> entry : entries) {
            if (rank > TOP_N) {
                break;
            }
            md.append(rank++).append(". ×").append(entry.getValue()).append(' ').append(entry.getKey()).append('\n');
        }
    }

    private static String pct(final int part, final int total) {
        return 0 == total ? "0.0" : String.format("%.1f", part * 100.0 / total);
    }

    private static int percentile(final List<Integer> sorted, final double quantile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        final int idx = (int) Math.min(sorted.size() - 1, Math.round(quantile * (sorted.size() - 1)));
        return sorted.get(idx);
    }
}
