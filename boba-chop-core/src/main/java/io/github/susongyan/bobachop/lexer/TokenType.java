package io.github.susongyan.bobachop.lexer;

/**
 * Token 类别（词法层，非 AST 语义层）。
 *
 * <p>NULL/TRUE/FALSE 等是 KEYWORD（Kw 携带），由语法器映射为 AST 字面量节点；
 * HEX/BIT 仅指 {@code 0x}/{@code 0b} 数字形态，{@code X'4D'}/{@code B'01'} 归 STRING。</p>
 */
public enum TokenType {

    /** 未加引号的词：关键字（Kw 命中）或标识符。 */
    KEYWORD,
    /** 未加引号的词：非关键字标识符。 */
    IDENTIFIER,
    /** 包裹标识符（反引号或 ANSI 双引号，方言开关决定；永不查关键字表）。 */
    QUOTED_IDENTIFIER,
    /** 字符串字面量（含引号原文；含引导前缀形态——MySQL N''/X''/B''、PG E''/B''/X''，及 MySQL 双引号字符串）。 */
    STRING,
    /** 十进制/科学计数数字（原文保留）。 */
    NUMBER,
    /** 十六进制数字 {@code 0x1F}。 */
    HEX,
    /** 二进制数字 {@code 0b0101}。 */
    BIT,
    /** 运算符（最长匹配产物，如 {@code <=>}、{@code ->>}；文本即判据）。 */
    OPERATOR,
    /** {@code (}。 */
    LPAREN,
    /** {@code )}。 */
    RPAREN,
    /** {@code ,}。 */
    COMMA,
    /** {@code .}（后随非数字；后随数字则并入 NUMBER）。 */
    DOT,
    /** {@code ;}。 */
    SEMICOLON,
    /** JDBC 参数标记 {@code ?}。 */
    PARAM,
    /** 结束哨兵：start=stop=源长度（零宽，闭区间约定的显式例外）。 */
    EOF
}
