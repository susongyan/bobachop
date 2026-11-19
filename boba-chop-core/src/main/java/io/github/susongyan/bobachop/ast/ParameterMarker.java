package io.github.susongyan.bobachop.ast;

/**
 * 参数标记（{@code ?}；PostgreSQL 的 {@code $1}、Oracle 的 {@code :1} 由方言层归入本节点）。
 *
 * <p>{@link #index()} 按<b>访问序</b>分配，白名单构造内访问序 = 文本序（DESIGN §7.3）。</p>
 */
public final class ParameterMarker extends AbstractNode implements Expr {

    private final int index;

    public ParameterMarker(final int start, final int stop, final int index) {
        super(start, stop);
        this.index = index;
    }

    /** 参数下标（0 起，访问序）。 */
    public int index() {
        return index;
    }
}
