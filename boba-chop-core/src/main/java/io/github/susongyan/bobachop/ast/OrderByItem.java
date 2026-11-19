package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 排序项：{@code expr [ASC|DESC]}。方向三态（未指定与 ASC 语义同、语法事实不同——改写层按需区分）。
 */
public final class OrderByItem extends AbstractNode {

    private final Expr expr;
    private final OrderDirection direction;

    public OrderByItem(final int start, final int stop, final Expr expr, final OrderDirection direction) {
        super(start, stop);
        this.expr = Objects.requireNonNull(expr, "expr");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public Expr expr() {
        return expr;
    }

    public OrderDirection direction() {
        return direction;
    }
}
