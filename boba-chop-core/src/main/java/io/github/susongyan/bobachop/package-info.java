/**
 * BobaChop 高性能 SQL 解析内核。
 *
 * <p>子包规划：{@code ast}（AST 契约，发布即冻结）、{@code lexer}、{@code parser}、
 * {@code dialect}（DialectProfile）、{@code api}（入口 {@code BobaChop}）、{@code exception}。</p>
 *
 * <p>规格单一事实源：仓库根 DESIGN.md。白名单外构造一律抛
 * {@code UnsupportedSyntaxException}，绝不产出错误树。</p>
 */
package io.github.susongyan.bobachop;
