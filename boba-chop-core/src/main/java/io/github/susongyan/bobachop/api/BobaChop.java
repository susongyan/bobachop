package io.github.susongyan.bobachop.api;

import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.parser.Parser;

/**
 * BobaChop 对外唯一入口：SQL 文本 → 冻结 AST。
 *
 * <p><b>调用约定</b>：</p>
 * <ul>
 *   <li>每调用一次做一次完整解析：无会话状态、无缓存、线程安全。</li>
 *   <li>白名单外构造抛 {@link io.github.susongyan.bobachop.exception.UnsupportedSyntaxException}
 *       （携带构造名与位置），输入不合法抛
 *       {@link io.github.susongyan.bobachop.exception.ParseException} /
 *       {@link io.github.susongyan.bobachop.exception.LexException}——调用方按类型分流降级决策。</li>
 *   <li>词法错误（{@code LexException}）按白名单外语义上抛，与语法拒绝同等对待。</li>
 * </ul>
 *
 * <p><b>当前支持面</b>（v1 白名单随 WHITELIST.md 演进；方言 = MySQL / PostgreSQL）：</p>
 * <ul>
 *   <li>SELECT 主干——投影/别名/DISTINCT、JOIN（INNER/LEFT/RIGHT/CROSS/STRAIGHT_JOIN(MySQL)/逗号，
 *   ON/USING）、派生表、WHERE/GROUP BY/HAVING/ORDER BY、分页（MySQL LIMIT 三形态；PG
 *   LIMIT/OFFSET 与 FETCH FIRST n ROWS ONLY 族）、UNION [ALL]、表达式子查询</li>
 *   <li>INSERT——[INTO]（列清单）/VALUES 多行/insert-SELECT（含 UNION 源）/ON DUPLICATE KEY UPDATE
 *   （含 MySQL 专有 VALUES(col)）/RETURNING（PG 方言锚点，投影项同形）</li>
 *   <li>UPDATE 单表（别名/SET 赋值/WHERE/RETURNING(PG)）、DELETE 单表（别名/WHERE/RETURNING(PG)）</li>
 *   <li>PG 词法——ANSI 双引号标识符、E''/B''/X'' 字符串、嵌套块注释、-- 恒注释；
 *   PG 白名单外——ON CONFLICT/FULL JOIN/LATERAL/ILIKE（显式归类或语法拒绝）</li>
 * </ul>
 */
public final class BobaChop {

    private BobaChop() {
    }

    /**
     * 解析单条 SQL（MySQL 方言，等价 {@code parse(sql, DialectProfile.MYSQL)}）。
     *
     * @param sql SQL 文本，不允许多语句（尾分号可省可留）
     * @return 语句 AST（全节点携带源文本偏移）
     * @throws io.github.susongyan.bobachop.exception.UnsupportedSyntaxException 白名单外构造
     * @throws io.github.susongyan.bobachop.exception.ParseException 语法错误
     * @throws io.github.susongyan.bobachop.exception.LexException 词法错误
     */
    public static Statement parse(final String sql) {
        return parse(sql, DialectProfile.MYSQL);
    }

    /**
     * 解析单条 SQL（指定方言 profile；D8 单内核多方言）。
     *
     * @param sql SQL 文本，不允许多语句（尾分号可省可留）
     * @param profile 方言 profile（{@link DialectProfile#MYSQL} / {@link DialectProfile#POSTGRESQL}）
     * @return 语句 AST（全节点携带源文本偏移）
     * @throws io.github.susongyan.bobachop.exception.UnsupportedSyntaxException 白名单外构造
     * @throws io.github.susongyan.bobachop.exception.ParseException 语法错误
     * @throws io.github.susongyan.bobachop.exception.LexException 词法错误
     */
    public static Statement parse(final String sql, final DialectProfile profile) {
        return new Parser(sql, profile).parse();
    }
}
