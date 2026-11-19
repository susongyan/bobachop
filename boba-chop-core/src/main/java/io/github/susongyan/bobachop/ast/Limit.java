package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 分页子句：方言形态差异（MySQL {@code LIMIT [o,]n} / {@code LIMIT n OFFSET o}、
 * PostgreSQL {@code LIMIT n OFFSET o}、Oracle {@code FETCH FIRST n ROWS ONLY}）
 * 统一归入本节点的语义槽位；偏移与条数至少一项非空。
 */
public final class Limit extends AbstractNode {

    private final Expr offset;
    private final Expr rowCount;

    public Limit(final int start, final int stop, final Expr offset, final Expr rowCount) {
        super(start, stop);
        this.offset = offset;
        this.rowCount = Objects.requireNonNull(rowCount, "rowCount");
    }

    /** 偏移表达式，未书写为 null。 */
    public Expr offset() {
        return offset;
    }

    /** 行数表达式。 */
    public Expr rowCount() {
        return rowCount;
    }
}
