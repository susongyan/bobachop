package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 集合操作复合查询：{@code q1 UNION [ALL] q2 [UNION [ALL] q3 ...]}。
 *
 * <p>{@link #operators()} 大小 = {@link #queries()} 大小 - 1，按位置对应；
 * 带括号的嵌套复合查询以单个元素出现。</p>
 */
public final class CompoundSelect extends AbstractNode implements Statement, SelectQuery {

    private final List<SelectQuery> queries;
    private final List<SetOperator> operators;

    public CompoundSelect(final int start, final int stop, final List<SelectQuery> queries, final List<SetOperator> operators) {
        super(start, stop);
        this.queries = Collections.unmodifiableList(new ArrayList<SelectQuery>(Objects.requireNonNull(queries, "queries")));
        this.operators = Collections.unmodifiableList(new ArrayList<SetOperator>(Objects.requireNonNull(operators, "operators")));
    }

    /** 被复合的查询体列表。 */
    public List<SelectQuery> queries() {
        return queries;
    }

    /** 集合运算符列表（queries[i] operators[i] queries[i+1]）。 */
    public List<SetOperator> operators() {
        return operators;
    }
}
