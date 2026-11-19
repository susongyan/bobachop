package io.github.susongyan.bobachop.diff;

import java.util.Objects;

/**
 * 单条差异：AST 内定位路径 + 双侧观测值。
 *
 * <p>路径以字段名与下标构成（如 {@code where.right.text}、{@code items[1].expr}、
 * {@code groupBy.size}）；语句级接受度不一致（一侧成功一侧失败）使用 {@code (statement)}。
 * 观测值渲染规则见 {@link AstDiff}：字符串加单引号、null 显式为 {@code null}。</p>
 */
public final class DiffDifference {

    private final String path;
    private final String expected;
    private final String actual;

    public DiffDifference(final String path, final String expected, final String actual) {
        this.path = Objects.requireNonNull(path, "path");
        this.expected = Objects.requireNonNull(expected, "expected");
        this.actual = Objects.requireNonNull(actual, "actual");
    }

    /** AST 内定位路径；空串表示根节点本身的缺失/类型差异。 */
    public String path() {
        return path;
    }

    /** 期望侧（参考实现）观测值渲染。 */
    public String expected() {
        return expected;
    }

    /** 实际侧（候选实现）观测值渲染。 */
    public String actual() {
        return actual;
    }

    @Override
    public String toString() {
        return path + ": expected=" + expected + ", actual=" + actual;
    }
}
