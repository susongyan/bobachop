package io.github.susongyan.bobachop.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 对拍编排：同一 SQL 喂给期望侧与实际侧执行器，产出 {@link DiffReport}。
 *
 * <p>比较语义：双方成功 → {@link AstDiff} 深度比较（含全部位置偏移）；
 * 一方成功一方失败 → 路径 {@code (statement)} 的结构性差异；双方失败 → 视为一致
 * （接受度对齐，同拒同收）。执行器抛出的任何 Throwable 按失败观测值兜底，
 * 批量对拍不因单条输入的崩溃（含栈溢出）中断。</p>
 */
public final class DiffHarness {

    private static final int SQL_RENDER_MAX = 200;

    private final ParseExecutor expected;
    private final ParseExecutor actual;

    public DiffHarness(final ParseExecutor expected, final ParseExecutor actual) {
        this.expected = Objects.requireNonNull(expected, "expected");
        this.actual = Objects.requireNonNull(actual, "actual");
    }

    /** 期望侧（参考实现）执行器。 */
    public ParseExecutor expected() {
        return expected;
    }

    /** 实际侧（候选实现）执行器。 */
    public ParseExecutor actual() {
        return actual;
    }

    public DiffReport diff(final String sql) {
        Objects.requireNonNull(sql, "sql");
        final ParseResult exp = safeParse(expected, sql);
        final ParseResult act = safeParse(actual, sql);
        final List<DiffDifference> differences = new ArrayList<DiffDifference>();
        if (exp.success() && act.success()) {
            differences.addAll(AstDiff.compare(exp.statement(), act.statement()));
        } else if (exp.success() != act.success()) {
            differences.add(new DiffDifference("(statement)", outcome(exp), outcome(act)));
        }
        return new DiffReport(sql, differences);
    }

    public List<DiffReport> diffAll(final Iterable<String> sqls) {
        Objects.requireNonNull(sqls, "sqls");
        final List<DiffReport> reports = new ArrayList<DiffReport>();
        for (final String sql : sqls) {
            reports.add(diff(sql));
        }
        return reports;
    }

    /** 文本渲染：汇总行 + 不一致明细（SQL 截断 + 差异路径），供 CI 与控制台输出。 */
    public String render(final List<DiffReport> reports) {
        Objects.requireNonNull(reports, "reports");
        int identicalCount = 0;
        for (final DiffReport report : reports) {
            if (report.identical()) {
                identicalCount++;
            }
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("对拍汇总（expected=").append(expected.name()).append(", actual=").append(actual.name())
                .append("）：").append(reports.size()).append(" 条，一致 ").append(identicalCount)
                .append("，不一致 ").append(reports.size() - identicalCount).append('\n');
        int mismatchIndex = 0;
        for (final DiffReport report : reports) {
            if (report.identical()) {
                continue;
            }
            mismatchIndex++;
            sb.append("--- 不一致 #").append(mismatchIndex).append(" ---\n");
            sb.append("SQL  : ").append(truncate(report.sql())).append('\n');
            final List<DiffDifference> diffs = report.differences();
            for (int i = 0; i < diffs.size(); i++) {
                sb.append("差异 ").append(i + 1).append('/').append(diffs.size()).append(": ").append(diffs.get(i)).append('\n');
            }
        }
        return sb.toString();
    }

    private static String outcome(final ParseResult result) {
        return result.success() ? "解析成功" : "解析失败: " + result.error();
    }

    private static ParseResult safeParse(final ParseExecutor executor, final String sql) {
        try {
            final ParseResult result = executor.parse(sql);
            return null != result ? result : ParseResult.failure("执行器返回 null");
        } catch (final Throwable t) {
            return ParseResult.failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static String truncate(final String sql) {
        return sql.length() <= SQL_RENDER_MAX ? sql : sql.substring(0, SQL_RENDER_MAX) + "...(" + sql.length() + " 字符)";
    }
}
