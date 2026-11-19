package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单个 SELECT 查询体（无 UNION；复合查询见 {@link CompoundSelect}）。
 *
 * <p>可空字段缺省即"该子句未出现"；{@code DISTINCT} 解析在本节点（解析 ≠ 允许跨分片执行，
 * 后者由 boba-pour 的归并矩阵裁决）。</p>
 */
public final class SelectStatement extends AbstractNode implements Statement, SelectQuery {

    private final boolean distinct;
    private final List<SelectItem> items;
    private final TableRef from;
    private final Expr where;
    private final List<Expr> groupBy;
    private final Expr having;
    private final List<OrderByItem> orderBy;
    private final Limit limit;

    public SelectStatement(final int start, final int stop, final boolean distinct, final List<SelectItem> items,
                           final TableRef from, final Expr where, final List<Expr> groupBy, final Expr having,
                           final List<OrderByItem> orderBy, final Limit limit) {
        super(start, stop);
        this.distinct = distinct;
        this.items = Collections.unmodifiableList(new ArrayList<SelectItem>(Objects.requireNonNull(items, "items")));
        this.from = from;
        this.where = where;
        this.groupBy = null == groupBy ? null : Collections.unmodifiableList(new ArrayList<Expr>(groupBy));
        this.having = having;
        this.orderBy = null == orderBy ? null : Collections.unmodifiableList(new ArrayList<OrderByItem>(orderBy));
        this.limit = limit;
    }

    /** 是否带 DISTINCT。 */
    public boolean distinct() {
        return distinct;
    }

    /** 投影列表（至少一项；{@code *} 以 {@link Star} 出现）。 */
    public List<SelectItem> items() {
        return items;
    }

    /** FROM 表引用，可空（如 {@code SELECT 1}）。 */
    public TableRef from() {
        return from;
    }

    /** WHERE 表达式，可空。 */
    public Expr where() {
        return where;
    }

    /** GROUP BY 表达式列表，可空。 */
    public List<Expr> groupBy() {
        return groupBy;
    }

    /** HAVING 表达式，可空。 */
    public Expr having() {
        return having;
    }

    /** ORDER BY 列表，可空。 */
    public List<OrderByItem> orderBy() {
        return orderBy;
    }

    /** 分页，可空。Oracle {@code FETCH FIRST n ROWS ONLY} 同样归入本节点；
     * {@code ROWNUM} 按语法事实留在 WHERE 的列引用中，不做归一化。 */
    public Limit limit() {
        return limit;
    }
}
