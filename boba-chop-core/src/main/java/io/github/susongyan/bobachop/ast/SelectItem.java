package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 投影项：{@code expr [AS alias]}；{@code *} / {@code t.*} 的 expr 为 {@link Star}。
 */
public final class SelectItem extends AbstractNode {

    private final Expr expr;
    private final String alias;

    public SelectItem(final int start, final int stop, final Expr expr, final String alias) {
        super(start, stop);
        this.expr = Objects.requireNonNull(expr, "expr");
        this.alias = alias;
    }

    public Expr expr() {
        return expr;
    }

    /** 别名（源文本，不含 AS），未指定为 null。 */
    public String alias() {
        return alias;
    }
}
