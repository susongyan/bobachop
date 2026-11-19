package io.github.susongyan.bobachop.ast;

/**
 * 集合运算符（追加式：INTERSECT/EXCEPT 等随方言里程碑追加）。
 */
public enum SetOperator {
    /** UNION。 */
    UNION,
    /** UNION ALL。 */
    UNION_ALL
}
