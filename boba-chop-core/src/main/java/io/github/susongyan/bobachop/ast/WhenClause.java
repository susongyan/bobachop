package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * CASE 的单个 WHEN 分支。
 */
public final class WhenClause extends AbstractNode {

    private final Expr condition;
    private final Expr result;

    public WhenClause(final int start, final int stop, final Expr condition, final Expr result) {
        super(start, stop);
        this.condition = Objects.requireNonNull(condition, "condition");
        this.result = Objects.requireNonNull(result, "result");
    }

    public Expr condition() {
        return condition;
    }

    public Expr result() {
        return result;
    }
}
