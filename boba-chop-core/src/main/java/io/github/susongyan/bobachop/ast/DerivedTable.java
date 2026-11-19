package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 派生表：{@code (SELECT ...) alias}。
 */
public final class DerivedTable extends AbstractNode implements TableRef {

    private final SelectQuery query;
    private final String alias;

    public DerivedTable(final int start, final int stop, final SelectQuery query, final String alias) {
        super(start, stop);
        this.query = Objects.requireNonNull(query, "query");
        this.alias = alias;
    }

    public SelectQuery query() {
        return query;
    }

    /** 别名，可空（方言可要求必填，由解析器按 profile 裁决）。 */
    public String alias() {
        return alias;
    }
}
