package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 一元运算：前缀（{@code -x}、{@code NOT x}、{@code !x}）与后缀（PostgreSQL 的 {@code x::type}）。
 *
 * <p>{@code NOT EXISTS (...)} 由本节点包裹 {@link ExistsExpr} 表达，不设专门节点。</p>
 */
public final class UnaryExpr extends AbstractNode implements Expr {

    private final String operator;
    private final Expr operand;
    private final boolean postfix;

    public UnaryExpr(final int start, final int stop, final String operator, final Expr operand, final boolean postfix) {
        super(start, stop);
        this.operator = Objects.requireNonNull(operator, "operator");
        this.operand = Objects.requireNonNull(operand, "operand");
        this.postfix = postfix;
    }

    /** 运算符源文本（如 {@code -}、{@code NOT}）。 */
    public String operator() {
        return operator;
    }

    public Expr operand() {
        return operand;
    }

    /** true = 后缀运算（运算符在操作数之后）。 */
    public boolean postfix() {
        return postfix;
    }
}
