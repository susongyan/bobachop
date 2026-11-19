package io.github.susongyan.bobachop.api;

import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.parser.SelectParser;

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
 * <p><b>当前支持面</b>（MySQL 方言，v1 白名单随 WHITELIST.md 演进）：SELECT 主干——
 * 投影/别名/DISTINCT、JOIN（INNER/LEFT/RIGHT/CROSS/STRAIGHT_JOIN/逗号，ON/USING）、派生表、
 * WHERE/GROUP BY/HAVING/ORDER BY、LIMIT 三形态、UNION [ALL]、表达式子查询。
 * INSERT/UPDATE/DELETE 于 T8 接入。</p>
 */
public final class BobaChop {

    private BobaChop() {
    }

    /**
     * 解析单条 SQL（MySQL 方言；方言 profile 参数于 T10 引入）。
     *
     * @param sql SQL 文本，不允许多语句（尾分号可省可留）
     * @return 语句 AST（全节点携带源文本偏移）
     * @throws io.github.susongyan.bobachop.exception.UnsupportedSyntaxException 白名单外构造
     * @throws io.github.susongyan.bobachop.exception.ParseException 语法错误
     * @throws io.github.susongyan.bobachop.exception.LexException 词法错误
     */
    public static Statement parse(final String sql) {
        return new SelectParser(sql).parse();
    }
}
