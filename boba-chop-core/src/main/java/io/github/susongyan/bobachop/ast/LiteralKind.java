package io.github.susongyan.bobachop.ast;

/**
 * 字面量类别（追加式：新类别只增不改）。
 */
public enum LiteralKind {
    /** {@code 'abc'}，含方言转义原文。 */
    STRING,
    /** 整数/小数/科学计数，原文保留。 */
    NUMBER,
    /** {@code 0x1F}。 */
    HEX,
    /** {@code 0b01}（MySQL）。 */
    BIT,
    /** {@code NULL}。 */
    NULL,
    /** {@code TRUE}。 */
    TRUE,
    /** {@code FALSE}。 */
    FALSE,
    /** {@code DATE'...'} / {@code TIME'...'} / {@code TIMESTAMP'...'} 带前缀时间字面量。 */
    TEMPORAL
}
