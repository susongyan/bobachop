package io.github.susongyan.bobachop.ast;

/**
 * 星号叶子：投影位（{@code SELECT *} / {@code SELECT t.*}）与聚合实参位（{@code COUNT(*)}）。
 *
 * <p>两处身份由所在容器区分（SelectItem 的 expr / FunctionCall 的 arguments），节点本身不带语义。</p>
 */
public final class Star extends AbstractNode implements Expr {

    private final String qualifier;

    public Star(final int start, final int stop, final String qualifier) {
        super(start, stop);
        this.qualifier = qualifier;
    }

    /** 限定前缀（如 {@code t}），通配星号（无前缀）为 null。 */
    public String qualifier() {
        return qualifier;
    }
}
