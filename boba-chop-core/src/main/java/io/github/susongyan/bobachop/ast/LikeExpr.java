package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * LIKE 谓词：{@code x [NOT] LIKE pattern [ESCAPE esc]}。
 */
public final class LikeExpr extends AbstractNode implements Expr {

    private final Expr target;
    private final boolean not;
    private final Expr pattern;
    private final Expr escape;

    public LikeExpr(final int start, final int stop, final Expr target, final boolean not, final Expr pattern, final Expr escape) {
        super(start, stop);
        this.target = Objects.requireNonNull(target, "target");
        this.not = not;
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.escape = escape;
    }

    public Expr target() {
        return target;
    }

    /** 是否为 NOT LIKE。 */
    public boolean not() {
        return not;
    }

    public Expr pattern() {
        return pattern;
    }

    /** ESCAPE 表达式，未指定为 null。 */
    public Expr escape() {
        return escape;
    }
}
