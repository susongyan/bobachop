package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * INSERT ... SELECT 形态的数据源。
 */
public final class SelectSource extends AbstractNode implements InsertSource {

    private final SelectQuery query;

    public SelectSource(final int start, final int stop, final SelectQuery query) {
        super(start, stop);
        this.query = Objects.requireNonNull(query, "query");
    }

    public SelectQuery query() {
        return query;
    }
}
