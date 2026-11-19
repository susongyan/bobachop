package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * UPDATE 语句（v1 单表）：{@code UPDATE t [alias] SET a = x, ... [WHERE ...]}。
 */
public final class UpdateStatement extends AbstractNode implements Statement {

    private final TableName table;
    private final List<Assignment> assignments;
    private final Expr where;

    public UpdateStatement(final int start, final int stop, final TableName table, final List<Assignment> assignments, final Expr where) {
        super(start, stop);
        this.table = Objects.requireNonNull(table, "table");
        this.assignments = Collections.unmodifiableList(new ArrayList<Assignment>(Objects.requireNonNull(assignments, "assignments")));
        this.where = where;
    }

    public TableName table() {
        return table;
    }

    /** SET 赋值清单（至少一项）。 */
    public List<Assignment> assignments() {
        return assignments;
    }

    /** WHERE 表达式，可空。 */
    public Expr where() {
        return where;
    }
}
