package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * BETWEEN 谓词：{@code x [NOT] BETWEEN low AND high}。
 *
 * <p>内部 {@code AND} 归属谓词，不产生歧义节点（词法/语法特判点之一，DESIGN §7.3）。</p>
 */
public final class BetweenExpr extends AbstractNode implements Expr {

    private final Expr target;
    private final boolean not;
    private final Expr low;
    private final Expr high;

    public BetweenExpr(final int start, final int stop, final Expr target, final boolean not, final Expr low, final Expr high) {
        super(start, stop);
        this.target = Objects.requireNonNull(target, "target");
        this.not = not;
        this.low = Objects.requireNonNull(low, "low");
        this.high = Objects.requireNonNull(high, "high");
    }

    public Expr target() {
        return target;
    }

    /** 是否为 NOT BETWEEN。 */
    public boolean not() {
        return not;
    }

    public Expr low() {
        return low;
    }

    public Expr high() {
        return high;
    }
}
