package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 二元运算：算术/比较/逻辑/位运算统一节点。
 *
 * <p>运算符存<b>源文本</b>（保大小写，如 {@code and} / {@code AND} / {@code =} / {@code <=>}），
 * 不做语义归一化——跨方言差异（如 {@code ||} 在 PG/Oracle 为 concat、MySQL 默认为逻辑 OR）
 * 停留在文本与下游语义层，AST 不分方言建型（DESIGN §7.4）。</p>
 */
public final class BinaryExpr extends AbstractNode implements Expr {

    private final String operator;
    private final Expr left;
    private final Expr right;

    public BinaryExpr(final int start, final int stop, final String operator, final Expr left, final Expr right) {
        super(start, stop);
        this.operator = Objects.requireNonNull(operator, "operator");
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    /** 运算符源文本（含关键字类运算符）。 */
    public String operator() {
        return operator;
    }

    public Expr left() {
        return left;
    }

    public Expr right() {
        return right;
    }
}
