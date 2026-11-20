package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 分页子句：方言形态差异（MySQL {@code LIMIT [o,]n} / {@code LIMIT n OFFSET o}、
 * PostgreSQL {@code LIMIT n OFFSET o} 与 {@code OFFSET o ROWS FETCH FIRST n ROWS ONLY}、
 * Oracle {@code FETCH FIRST n ROWS ONLY}）统一归入本节点的语义槽位；偏移与条数
 * 至少一项非空。
 *
 * <p><b>形态字段（F3 契约演进，追加可空字段）</b>：offset 与 rowCount 的<b>文本序
 * 随书写形态翻转</b>（逗号形态与标准族 offset 在前，LIMIT 形态 rowCount 在前）——
 * 参数标记的访问序（即绑定序）随之翻转。故 {@link #form()} 记录原书写形态，渲染器
 * 按原形态还原而非规范化为方言默认形态，往返后参数语义槽位不换位（BobaPour 改写
 * 场景下参数值错位属数据损坏级缺陷，F3 变异模糊暴露）。{@code null} 表示未声明
 * （手工构造），渲染按方言默认形态。</p>
 */
public final class Limit extends AbstractNode {

    /** 分页书写形态：决定 offset/rowCount 的渲染文本序。 */
    public enum Form {

        /** {@code LIMIT n [OFFSET o]}——rowCount 在前（MySQL/PG）。 */
        LIMIT,

        /** {@code LIMIT o, n}——offset 在前（MySQL 逗号形态）。 */
        COMMA,

        /** {@code [OFFSET o ROWS] FETCH FIRST n ROWS ONLY}——offset 在前（SQL 标准族）。 */
        FETCH
    }

    private final Expr offset;
    private final Expr rowCount;
    private final Form form;

    public Limit(final int start, final int stop, final Expr offset, final Expr rowCount) {
        this(start, stop, offset, rowCount, null);
    }

    public Limit(final int start, final int stop, final Expr offset, final Expr rowCount, final Form form) {
        super(start, stop);
        this.offset = offset;
        this.rowCount = Objects.requireNonNull(rowCount, "rowCount");
        this.form = form;
    }

    /** 偏移表达式，未书写为 null。 */
    public Expr offset() {
        return offset;
    }

    /** 行数表达式。 */
    public Expr rowCount() {
        return rowCount;
    }

    /** 书写形态，未声明为 null（渲染按方言默认形态）。 */
    public Form form() {
        return form;
    }
}
