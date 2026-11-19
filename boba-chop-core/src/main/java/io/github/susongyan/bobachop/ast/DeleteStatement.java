package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * DELETE 语句（v1 单表）：{@code DELETE FROM t [alias] [WHERE ...]}。
 */
public final class DeleteStatement extends AbstractNode implements Statement {

    private final TableName table;
    private final Expr where;

    public DeleteStatement(final int start, final int stop, final TableName table, final Expr where) {
        super(start, stop);
        this.table = Objects.requireNonNull(table, "table");
        this.where = where;
    }

    public TableName table() {
        return table;
    }

    /** WHERE 表达式，可空。 */
    public Expr where() {
        return where;
    }
}
