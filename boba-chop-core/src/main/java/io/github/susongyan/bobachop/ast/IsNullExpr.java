package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * IS NULL / IS NOT NULL 谓词。
 */
public final class IsNullExpr extends AbstractNode implements Expr {

    private final Expr target;
    private final boolean not;

    public IsNullExpr(final int start, final int stop, final Expr target, final boolean not) {
        super(start, stop);
        this.target = Objects.requireNonNull(target, "target");
        this.not = not;
    }

    public Expr target() {
        return target;
    }

    /** 是否为 IS NOT NULL。 */
    public boolean not() {
        return not;
    }
}
