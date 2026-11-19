package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * EXISTS 谓词。{@code NOT EXISTS} 由 {@link UnaryExpr}{@code (NOT)} 包裹表达。
 */
public final class ExistsExpr extends AbstractNode implements Expr {

    private final SelectQuery query;

    public ExistsExpr(final int start, final int stop, final SelectQuery query) {
        super(start, stop);
        this.query = Objects.requireNonNull(query, "query");
    }

    public SelectQuery query() {
        return query;
    }
}
