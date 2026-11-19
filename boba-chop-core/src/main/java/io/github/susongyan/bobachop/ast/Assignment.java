package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 赋值：UPDATE SET / ON DUPLICATE KEY UPDATE 的 {@code column = value} 单元。
 */
public final class Assignment extends AbstractNode {

    private final ColumnRef column;
    private final Expr value;

    public Assignment(final int start, final int stop, final ColumnRef column, final Expr value) {
        super(start, stop);
        this.column = Objects.requireNonNull(column, "column");
        this.value = Objects.requireNonNull(value, "value");
    }

    public ColumnRef column() {
        return column;
    }

    public Expr value() {
        return value;
    }
}
