package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * IN 谓词：值清单与子查询二选一。
 *
 * <p>{@link #values()} 与 {@link #query()} 恰一非空（构造方保证，契约方不重复建模）。</p>
 */
public final class InExpr extends AbstractNode implements Expr {

    private final Expr target;
    private final boolean not;
    private final List<Expr> values;
    private final SelectQuery query;

    public InExpr(final int start, final int stop, final Expr target, final boolean not,
                  final List<Expr> values, final SelectQuery query) {
        super(start, stop);
        this.target = Objects.requireNonNull(target, "target");
        this.not = not;
        this.values = null == values ? null : Collections.unmodifiableList(new ArrayList<Expr>(values));
        this.query = query;
    }

    /** IN 的左操作数。 */
    public Expr target() {
        return target;
    }

    /** 是否为 NOT IN。 */
    public boolean not() {
        return not;
    }

    /** 值清单形态（{@code IN (1,2,?)}），子查询形态为 null。 */
    public List<Expr> values() {
        return values;
    }

    /** 子查询形态（{@code IN (SELECT ...)}），值清单形态为 null。 */
    public SelectQuery query() {
        return query;
    }
}
