package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JOIN 结构：{@code left [type] JOIN right [ON condition | USING (cols)]}。
 *
 * <p>连接条件 ON 与 USING 恰一非空（CROSS/COMMA 两者皆空）；右结合的嵌套 JOIN 中，
 * 本节点整体可作为外层 Join 的操作数。</p>
 */
public final class Join extends AbstractNode implements TableRef {

    private final TableRef left;
    private final JoinType type;
    private final TableRef right;
    private final Expr condition;
    private final List<String> usingColumns;

    public Join(final int start, final int stop, final TableRef left, final JoinType type, final TableRef right,
                final Expr condition, final List<String> usingColumns) {
        super(start, stop);
        this.left = Objects.requireNonNull(left, "left");
        this.type = Objects.requireNonNull(type, "type");
        this.right = Objects.requireNonNull(right, "right");
        this.condition = condition;
        this.usingColumns = null == usingColumns ? null : Collections.unmodifiableList(new ArrayList<String>(usingColumns));
    }

    public TableRef left() {
        return left;
    }

    public JoinType type() {
        return type;
    }

    public TableRef right() {
        return right;
    }

    /** ON 条件，USING 形态为 null。 */
    public Expr condition() {
        return condition;
    }

    /** USING 列名清单（源文本），ON 形态为 null。 */
    public List<String> usingColumns() {
        return usingColumns;
    }
}
