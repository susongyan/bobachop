package io.github.susongyan.bobachop.ast;

/**
 * 连接类型（追加式：FULL 等随方言里程碑追加）。
 */
public enum JoinType {
    /** INNER JOIN（含 STRAIGHT_JOIN 归入）。 */
    INNER,
    /** LEFT [OUTER] JOIN。 */
    LEFT,
    /** RIGHT [OUTER] JOIN。 */
    RIGHT,
    /** CROSS JOIN。 */
    CROSS,
    /** 逗号连接（{@code FROM a, b}）。 */
    COMMA
}
