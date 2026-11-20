package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * DELETE 语句（v1 单表）：{@code DELETE FROM t [alias] [WHERE ...] [RETURNING 清单]}。
 *
 * <p>RETURNING 为 PG 方言锚点（追加可空字段，只增不改），返回列清单与 SELECT 投影项同形。</p>
 */
public final class DeleteStatement extends AbstractNode implements Statement {

    private final TableName table;
    private final Expr where;
    private final List<SelectItem> returning;

    public DeleteStatement(final int start, final int stop, final TableName table, final Expr where) {
        this(start, stop, table, where, null);
    }

    public DeleteStatement(final int start, final int stop, final TableName table, final Expr where,
            final List<SelectItem> returning) {
        super(start, stop);
        this.table = Objects.requireNonNull(table, "table");
        this.where = where;
        this.returning = null == returning ? null : Collections.unmodifiableList(new ArrayList<SelectItem>(returning));
    }

    public TableName table() {
        return table;
    }

    /** WHERE 表达式，可空。 */
    public Expr where() {
        return where;
    }

    /** RETURNING 返回列清单（PG 方言锚点），未出现为 null。 */
    public List<SelectItem> returning() {
        return returning;
    }
}
