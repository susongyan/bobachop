package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 标量子查询表达式：{@code (SELECT ...)} 出现在表达式位置。
 */
public final class SubqueryExpr extends AbstractNode implements Expr {

    private final SelectQuery query;

    public SubqueryExpr(final int start, final int stop, final SelectQuery query) {
        super(start, stop);
        this.query = Objects.requireNonNull(query, "query");
    }

    public SelectQuery query() {
        return query;
    }
}
