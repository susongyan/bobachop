package io.github.susongyan.bobachop.parser;

import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.lexer.Token;

/**
 * 嵌套深度护栏（对拍体系 v2 F3 预声明规格条目，DESIGN 版本记录见冻结条）：表达式递归
 * （括号/一元/NOT/CASE/函数实参/IN 清单/BETWEEN 实参，皆经 ExprParser.parseExpr 入口）
 * 与查询递归（子查询/派生表/insert-select 源，皆经 SelectParser.parseQueryBody 入口）
 * 共享同一深度预算——两类递归消耗同一份调用栈，上限必须合计计量。
 *
 * <p><b>只限栈递归，不限链长</b>：迭代路径（长 AND/OR 链、长 IN 清单）不递归入栈，
 * 不受本护栏约束，合法产出深树——递归消费者（渲染器/遍历器）的深树预算是独立观察项
 * （台账），不与本护栏混同。上限 256：真实 SQL 嵌套极端形态 ~30 级，256 已宽裕；
 * 而派生表链每级 ~6 帧、每帧 ~150B，256 级 ≈ 230KB，即便 256KB 小栈线程（-Xss 下限
 * 场景）也不会先行 StackOverflow——上限取值按"最坏每级帧成本 × 最小可部署栈预算"
 * 推导，非拍脑袋。超出以 ParseException 定性，保证任意输入落在三支异常封闭性内
 * （F3），BobaPour 由此获得结构性有界的栈成本。</p>
 */
final class DepthGuard {

    /** 嵌套深度上限（括号/一元/CASE/子查询/派生表合计，同时深度=栈占用）。 */
    static final int MAX_NESTING = 256;

    private int depth;

    /** 进入一层嵌套；超限抛 ParseException（首 token 位供错误定位）。 */
    void enter(final Token at) {
        if (++depth > MAX_NESTING) {
            throw new ParseException("嵌套深度超过上限 " + MAX_NESTING
                    + "（括号/一元/CASE/子查询/派生表合计）@" + at.start(), at.start());
        }
    }

    /** 退出一层嵌套（enter 抛出时本层的自增同样被抵消，计数恒平衡）。 */
    void exit() {
        depth--;
    }
}
