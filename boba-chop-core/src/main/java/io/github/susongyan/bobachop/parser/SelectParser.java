package io.github.susongyan.bobachop.parser;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.ast.CompoundSelect;
import io.github.susongyan.bobachop.ast.DerivedTable;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.Join;
import io.github.susongyan.bobachop.ast.JoinType;
import io.github.susongyan.bobachop.ast.Limit;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.LiteralKind;
import io.github.susongyan.bobachop.ast.OrderByItem;
import io.github.susongyan.bobachop.ast.OrderDirection;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.SelectItem;
import io.github.susongyan.bobachop.ast.SelectQuery;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.SetOperator;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.ast.TableRef;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenBuffer;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * SELECT 语句语法器主干（DESIGN §7.3：顶层按首 token 分派；每构造一函数，函数即白名单条目）。
 *
 * <p><b>v1 白名单</b>：投影（含别名）/DISTINCT/ALL、FROM（具名表/派生表/JOIN：INNER/LEFT/RIGHT/CROSS/
 * STRAIGHT_JOIN(归入 INNER)/逗号连接，ON/USING）、WHERE/GROUP BY/HAVING/ORDER BY、
 * LIMIT 三形态（{@code LIMIT n} / {@code LIMIT o, n} / {@code LIMIT n OFFSET o}）、
 * UNION [ALL] 链、子查询（经 {@link ExprParser} 桥接）。</p>
 *
 * <p><b>v1 白名单外</b>：UNION 后的复合级 ORDER BY/LIMIT（AST 契约 CompoundSelect 无尾锚，后续以
 * 追加可空字段演进）、括号化表引用/括号化复合查询、WITH ROLLUP、FOR UPDATE/LOCK IN SHARE MODE、
 * 多级限定表名（db.t 为上限）、LIMIT 实参非数字字面量或参数标记。</p>
 */
public final class SelectParser {

    private final TokenBuffer buffer;
    private final ExprParser exprs;

    /** 共享 buffer/exprs 由语句级 {@link Parser} 装配（子查询桥接与 insert-select 复用本类）。 */
    SelectParser(final TokenBuffer buffer, final ExprParser exprs) {
        this.buffer = buffer;
        this.exprs = exprs;
    }

    /** 语句级 SELECT 入口（Parser 分派后到达）：查询体 + 白名单外尾部构造检查。 */
    Statement parseStatement() {
        final SelectQuery query = parseQueryBody();
        final Token trailing = buffer.peek(0);
        // 已知白名单外尾部构造显式归 UnsupportedSyntaxException；其余尾部 token 由 Parser 按语法错误定性
        if (trailing.isKw(Kw.FOR) || trailing.isKw(Kw.LOCK) || trailing.isKw(Kw.WINDOW)
                || trailing.isKw(Kw.NATURAL) || trailing.isKw(Kw.INTO) || trailing.isKw(Kw.PROCEDURE)) {
            throw unsupported("SELECT 尾部构造 '" + trailing.text() + "'", trailing);
        }
        return (Statement) query;
    }

    /** 查询体：单 SELECT 或 UNION [ALL] 链（左结合按位置对应 operators）；亦为子查询桥与 insert-select 的目标。 */
    SelectQuery parseQueryBody() {
        final SelectStatement first = parseSimpleSelect(false);
        if (!buffer.peek(0).isKw(Kw.UNION)) {
            return first;
        }
        // 未括号化的 UNION 操作数不得自带 ORDER BY/LIMIT（MySQL 1221 域；括号化复合 v1 白名单外）
        if (null != first.orderBy() || null != first.limit()) {
            throw unsupported("UNION 操作数自带 ORDER BY/LIMIT", buffer.peek(0));
        }
        final List<SelectQuery> queries = new ArrayList<SelectQuery>();
        final List<SetOperator> operators = new ArrayList<SetOperator>();
        queries.add(first);
        while (buffer.peek(0).isKw(Kw.UNION)) {
            buffer.next();
            final SetOperator operator = buffer.peek(0).isKw(Kw.ALL) ? SetOperator.UNION_ALL : SetOperator.UNION;
            if (SetOperator.UNION_ALL == operator) {
                buffer.next();
            }
            operators.add(operator);
            queries.add(parseSimpleSelect(true));
        }
        // 契约 v1：CompoundSelect 无尾锚，复合级 ORDER BY/LIMIT 白名单外
        if (buffer.peek(0).isKw(Kw.ORDER) || buffer.peek(0).isKw(Kw.LIMIT)) {
            throw unsupported("UNION 复合级 ORDER BY/LIMIT（待契约追加尾锚字段）", buffer.peek(0));
        }
        return new CompoundSelect(first.start(), lastStop(), queries, operators);
    }

    /**
     * 单个查询体。复合操作数（compoundOperand）不消费 ORDER BY/LIMIT——该位置出现即属复合级，
     * 由 {@link #parseSelectQuery()} 统一拒绝（无回溯前提下的归属裁定）。
     */
    private SelectStatement parseSimpleSelect(final boolean compoundOperand) {
        final Token select = expectKw(Kw.SELECT, "SELECT");
        boolean distinct = false;
        if (buffer.peek(0).isKw(Kw.DISTINCT)) {
            distinct = true;
            buffer.next();
        } else if (buffer.peek(0).isKw(Kw.ALL)) {
            buffer.next();
        }
        final List<SelectItem> items = new ArrayList<SelectItem>();
        while (true) {
            final Expr expr = exprs.parseExpr();
            final Token alias = parseAlias();
            items.add(new SelectItem(expr.start(), null != alias ? alias.stop() : expr.stop(), expr,
                    null != alias ? alias.text() : null));
            if (TokenType.COMMA == buffer.peek(0).type()) {
                buffer.next();
                continue;
            }
            break;
        }
        TableRef from = null;
        if (buffer.peek(0).isKw(Kw.FROM)) {
            buffer.next();
            from = parseTableRef();
        }
        Expr where = null;
        if (buffer.peek(0).isKw(Kw.WHERE)) {
            buffer.next();
            where = exprs.parseExpr();
        }
        List<Expr> groupBy = null;
        if (buffer.peek(0).isKw(Kw.GROUP)) {
            buffer.next();
            expectKw(Kw.BY, "GROUP BY 的 BY");
            groupBy = new ArrayList<Expr>();
            while (true) {
                groupBy.add(exprs.parseExpr());
                if (TokenType.COMMA == buffer.peek(0).type()) {
                    buffer.next();
                    continue;
                }
                break;
            }
            if (buffer.peek(0).isKw(Kw.WITH)) {
                throw unsupported("GROUP BY ... WITH ROLLUP", buffer.peek(0));
            }
        }
        Expr having = null;
        if (buffer.peek(0).isKw(Kw.HAVING)) {
            buffer.next();
            having = exprs.parseExpr();
        }
        List<OrderByItem> orderBy = null;
        Limit limit = null;
        if (!compoundOperand) {
            if (buffer.peek(0).isKw(Kw.ORDER)) {
                buffer.next();
                expectKw(Kw.BY, "ORDER BY 的 BY");
                orderBy = new ArrayList<OrderByItem>();
                while (true) {
                    final Expr expr = exprs.parseExpr();
                    OrderDirection direction = OrderDirection.UNSPECIFIED;
                    if (buffer.peek(0).isKw(Kw.ASC)) {
                        direction = OrderDirection.ASC;
                        buffer.next();
                    } else if (buffer.peek(0).isKw(Kw.DESC)) {
                        direction = OrderDirection.DESC;
                        buffer.next();
                    }
                    final int stop = OrderDirection.UNSPECIFIED == direction ? expr.stop() : lastStop();
                    orderBy.add(new OrderByItem(expr.start(), stop, expr, direction));
                    if (TokenType.COMMA == buffer.peek(0).type()) {
                        buffer.next();
                        continue;
                    }
                    break;
                }
            }
            if (buffer.peek(0).isKw(Kw.LIMIT)) {
                limit = parseLimit();
            }
        }
        return new SelectStatement(select.start(), lastStop(), distinct, items, from, where, groupBy, having,
                orderBy, limit);
    }

    // ---------- FROM：表引用与 JOIN ----------

    private TableRef parseTableRef() {
        TableRef left = parseTableFactor();
        while (true) {
            if (TokenType.COMMA == buffer.peek(0).type()) {
                buffer.next();
                final TableRef right = parseTableFactor();
                left = new Join(left.start(), lastStop(), left, JoinType.COMMA, right, null, null);
                continue;
            }
            final JoinType type = joinKeywordAhead();
            if (null == type) {
                return left;
            }
            consumeJoinKeyword();
            final TableRef right = parseTableFactor();
            Expr condition = null;
            List<String> usingColumns = null;
            if (buffer.peek(0).isKw(Kw.ON)) {
                // 契约：CROSS/COMMA 两条件皆空；CROSS ... ON 语法合法但契约不可表达
                if (JoinType.CROSS == type) {
                    throw unsupported("CROSS JOIN 带 ON（契约：CROSS 无条件）", buffer.peek(0));
                }
                buffer.next();
                condition = exprs.parseExpr();
            } else if (buffer.peek(0).isKw(Kw.USING)) {
                if (JoinType.CROSS == type) {
                    throw unsupported("CROSS JOIN 带 USING（契约：CROSS 无条件）", buffer.peek(0));
                }
                buffer.next();
                expectType(TokenType.LPAREN, "USING 后的 (");
                usingColumns = new ArrayList<String>();
                while (true) {
                    usingColumns.add(expectName("USING 列名").text());
                    if (TokenType.COMMA == buffer.peek(0).type()) {
                        buffer.next();
                        continue;
                    }
                    break;
                }
                expectType(TokenType.RPAREN, "USING 清单后的 )");
            }
            // 无 ON/USING：各 JOIN 形态均合法（契约 condition 可空；MySQL 允许 LEFT JOIN 缺条件）
            final int stop = null != condition ? condition.stop() : lastStop();
            left = new Join(left.start(), stop, left, type, right, condition, usingColumns);
        }
    }

    /** 只读判断：当前位置是否 JOIN 关键字形态（不改游标，D2 只读前瞻）。 */
    private JoinType joinKeywordAhead() {
        final Token token = buffer.peek(0);
        if (token.isKw(Kw.JOIN) || token.isKw(Kw.STRAIGHT_JOIN)) {
            return JoinType.INNER;
        }
        if (token.isKw(Kw.INNER) && buffer.peek(1).isKw(Kw.JOIN)) {
            return JoinType.INNER;
        }
        if (token.isKw(Kw.CROSS) && buffer.peek(1).isKw(Kw.JOIN)) {
            return JoinType.CROSS;
        }
        if (token.isKw(Kw.LEFT) && buffer.peek(1).isKw(Kw.JOIN)) {
            return JoinType.LEFT;
        }
        if (token.isKw(Kw.LEFT) && buffer.peek(1).isKw(Kw.OUTER) && buffer.peek(2).isKw(Kw.JOIN)) {
            return JoinType.LEFT;
        }
        if (token.isKw(Kw.RIGHT) && buffer.peek(1).isKw(Kw.JOIN)) {
            return JoinType.RIGHT;
        }
        if (token.isKw(Kw.RIGHT) && buffer.peek(1).isKw(Kw.OUTER) && buffer.peek(2).isKw(Kw.JOIN)) {
            return JoinType.RIGHT;
        }
        return null;
    }

    private void consumeJoinKeyword() {
        buffer.next();
        final Token kind = lastToken();
        if (kind.isKw(Kw.INNER) || kind.isKw(Kw.CROSS) || kind.isKw(Kw.LEFT) || kind.isKw(Kw.RIGHT)) {
            buffer.next();
            if (lastToken().isKw(Kw.OUTER)) {
                buffer.next();
            }
        }
    }

    private TableRef parseTableFactor() {
        final Token first = buffer.peek(0);
        if (TokenType.LPAREN == first.type()) {
            if (buffer.peek(1).isKw(Kw.SELECT)) {
                buffer.next();
                final SelectQuery query = parseQueryBody();
                expectType(TokenType.RPAREN, "派生表后的 )");
                final Token alias = parseAlias();
                if (null == alias) {
                    throw new ParseException("派生表需要别名", first.start());
                }
                return new DerivedTable(first.start(), alias.stop(), query, alias.text());
            }
            throw unsupported("括号化表引用（嵌套 JOIN）", first);
        }
        if (!isNameToken(first)) {
            throw new ParseException("期望表引用但为 " + first, first.start());
        }
        final List<Token> parts = new ArrayList<Token>();
        parts.add(buffer.next());
        while (TokenType.DOT == buffer.peek(0).type() && isNameToken(buffer.peek(1))) {
            buffer.next();
            parts.add(buffer.next());
        }
        if (parts.size() > 2) {
            throw unsupported("多级限定表名（上限 db.t）", parts.get(2));
        }
        final Token name = parts.get(parts.size() - 1);
        final String qualifier = 2 == parts.size() ? parts.get(0).text() : null;
        final Token alias = parseAlias();
        final int stop = null != alias ? alias.stop() : name.stop();
        return new TableName(first.start(), stop, qualifier, name.text(), null != alias ? alias.text() : null);
    }

    // ---------- LIMIT ----------

    /** LIMIT n / LIMIT o, n / LIMIT n OFFSET o（实参仅数字字面量或参数标记，白名单窄口）。 */
    private Limit parseLimit() {
        final Token limitToken = buffer.next();
        final Expr first = parseLimitArg();
        if (TokenType.COMMA == buffer.peek(0).type()) {
            buffer.next();
            final Expr second = parseLimitArg();
            return new Limit(limitToken.start(), second.stop(), first, second);
        }
        if (buffer.peek(0).isKw(Kw.OFFSET)) {
            buffer.next();
            final Expr offset = parseLimitArg();
            return new Limit(limitToken.start(), offset.stop(), offset, first);
        }
        return new Limit(limitToken.start(), first.stop(), null, first);
    }

    private Expr parseLimitArg() {
        // 先按完整表达式解析再校验形态：拒绝覆盖 NULL/算式/子查询等一切非窄口形态，
        // 且不会把 "LIMIT 1 + 1" 误拆成 Limit(1) + 尾部残 token
        final Expr expr = exprs.parseExpr();
        if (expr instanceof Literal && LiteralKind.NUMBER == ((Literal) expr).kind()) {
            return expr;
        }
        if (expr instanceof ParameterMarker) {
            return expr;
        }
        throw unsupported("LIMIT 实参形态（仅数字字面量或 ?）", expr.start());
    }

    // ---------- 工具 ----------

    /** 别名：[AS] name（IDENT/包裹/非保留字）；有 AS 无名即语法错误。 */
    private Token parseAlias() {
        boolean hasAs = false;
        if (buffer.peek(0).isKw(Kw.AS)) {
            hasAs = true;
            buffer.next();
        }
        final Token token = buffer.peek(0);
        if (isNameToken(token)) {
            return buffer.next();
        }
        if (hasAs) {
            throw new ParseException("AS 后缺别名: " + token, token.start());
        }
        return null;
    }

    private boolean isNameToken(final Token token) {
        return TokenType.IDENTIFIER == token.type() || TokenType.QUOTED_IDENTIFIER == token.type()
                || TokenType.KEYWORD == token.type() && !token.kw().reserved();
    }

    private Token expectName(final String what) {
        final Token token = buffer.peek(0);
        if (!isNameToken(token)) {
            throw new ParseException("期望 " + what + " 但为 " + token, token.start());
        }
        return buffer.next();
    }

    private Token expectKw(final Kw kw, final String what) {
        final Token token = buffer.peek(0);
        if (!token.isKw(kw)) {
            throw new ParseException("期望 " + what + " 但为 " + token, token.start());
        }
        return buffer.next();
    }

    private Token expectType(final TokenType type, final String what) {
        final Token token = buffer.peek(0);
        if (token.type() != type) {
            throw new ParseException("期望 " + what + " 但为 " + token, token.start());
        }
        return buffer.next();
    }

    /**
     * 最后已消费 token。ExprParser 与本类共享同一 buffer 游标，故以
     * {@code consumed()} 直读比各自维护 last 字段可靠（表达式结尾的语句 stop 恒准）。
     */
    private Token lastToken() {
        return buffer.tokens().get(buffer.consumed() - 1);
    }

    private int lastStop() {
        return lastToken().stop();
    }

    private static UnsupportedSyntaxException unsupported(final String what, final Token at) {
        return unsupported(what, at.start());
    }

    private static UnsupportedSyntaxException unsupported(final String what, final int at) {
        return new UnsupportedSyntaxException("白名单外: " + what + " @" + at);
    }
}
