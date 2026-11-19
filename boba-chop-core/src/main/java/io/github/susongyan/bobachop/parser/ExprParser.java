package io.github.susongyan.bobachop.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.susongyan.bobachop.ast.BetweenExpr;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.CaseExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.ExistsExpr;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.FunctionCall;
import io.github.susongyan.bobachop.ast.InExpr;
import io.github.susongyan.bobachop.ast.IsNullExpr;
import io.github.susongyan.bobachop.ast.LikeExpr;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.LiteralKind;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.SelectQuery;
import io.github.susongyan.bobachop.ast.Star;
import io.github.susongyan.bobachop.ast.SubqueryExpr;
import io.github.susongyan.bobachop.ast.UnaryExpr;
import io.github.susongyan.bobachop.ast.WhenClause;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenBuffer;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * 表达式语法器：Pratt 单循环（precedence climbing）+ 全部特判函数（DESIGN §7.3）。
 *
 * <p><b>优先级表</b>（数值大 = 结合更紧；§7.3 表的相对序不变，粒度按 MySQL 官方优先级细化：
 * NOT 插在 AND 之上、BETWEEN 之下；BETWEEN 与比较分离（更松）；{@code ^} 插在乘除之上、一元之下）：</p>
 * <pre> OR=1 &lt; XOR=2 &lt; AND=3 &lt; NOT=4 &lt; BETWEEN=5 &lt; 比较/IS/LIKE/IN/REGEXP=6
 * &lt; |=7 &lt; &amp;=8 &lt; 移位=9 &lt; 加减=10 &lt; 乘除模/DIV/MOD=11 &lt; ^=12 &lt; 一元=13</pre>
 *
 * <p><b>特判点</b>（各成独立小函数，函数即白名单条目可执行形态）：
 * BETWEEN 的 AND 归属、NOT IN/LIKE/BETWEEN、IS [NOT] NULL、IN(清单) vs IN(子查询) 分派、
 * 一元符号、{@code *} 双身份（裸星/限定星）、限定名链 vs 小数点。</p>
 *
 * <p><b>v1 白名单外</b>（UnsupportedSyntaxException）：行构造器 {@code (a,b)}、子查询
 * （EXISTS/IN/标量，待 T7 接入）、CAST 类特殊形态函数、限定函数调用 {@code db.f(x)}。
 * 子查询槽位的分派判断已就位，占位拒绝。</p>
 */
public final class ExprParser {

    private static final int PREC_OR = 1;
    private static final int PREC_XOR = 2;
    private static final int PREC_AND = 3;
    private static final int PREC_NOT = 4;
    private static final int PREC_BETWEEN = 5;
    private static final int PREC_CMP = 6;
    private static final int PREC_BIT_OR = 7;
    private static final int PREC_BIT_AND = 8;
    private static final int PREC_SHIFT = 9;
    private static final int PREC_ADD = 10;
    private static final int PREC_MUL = 11;
    private static final int PREC_BIT_XOR = 12;
    private static final int PREC_UNARY = 13;

    /** 受限函数名（特殊形态语法或保留字函数名，v1 一律拒绝，宁窄勿错）。 */
    private static final Set<String> BLOCKED_FUNCTION_NAMES = new HashSet<String>(
            Arrays.asList("CAST", "CONVERT", "EXTRACT", "POSITION", "SUBSTRING", "TRIM", "WEIGHT_STRING",
                    "DEFAULT", "LEFT", "RIGHT", "CHAR"));

    /** 子查询桥接：语句层语法器注入，表达式内的 (SELECT/IN(SELECT/EXISTS( 槽位经此回调真解析。 */
    public interface SubqueryBridge {

        /** 从当前位置解析一个查询体（不消费外层括号）。 */
        SelectQuery parseSelectQuery();
    }

    private static final SubqueryBridge NO_SUBQUERY = new SubqueryBridge() {
        @Override
        public SelectQuery parseSelectQuery() {
            throw new UnsupportedSyntaxException("白名单外: 子查询（独立表达式模式未接语句层语法器）");
        }
    };

    private final TokenBuffer buffer;
    private final SubqueryBridge subqueries;
    private final DialectProfile profile;

    /** 参数下标按访问序分配（白名单构造内访问序 = 文本序）。 */
    private int paramCounter;

    public ExprParser(final TokenBuffer buffer) {
        this(buffer, NO_SUBQUERY, DialectProfile.MYSQL);
    }

    public ExprParser(final TokenBuffer buffer, final SubqueryBridge subqueries) {
        this(buffer, subqueries, DialectProfile.MYSQL);
    }

    public ExprParser(final TokenBuffer buffer, final SubqueryBridge subqueries, final DialectProfile profile) {
        this.buffer = buffer;
        this.subqueries = subqueries;
        this.profile = profile;
    }

    /** 已分配的参数标记个数（0 起计，下标上限 = 此值）。 */
    public int paramCount() {
        return paramCounter;
    }

    /** 语句层（如 LIMIT 参数位）取用下一个参数下标，保持访问序 = 文本序。 */
    int nextParamIndex() {
        return paramCounter++;
    }

    /** 便捷入口：整串恰为一个表达式（到 EOF），多余 token 即语法错误。 */
    public static Expr parse(final String sql) {
        final ExprParser parser = new ExprParser(new TokenBuffer(sql));
        final Expr expr = parser.parseExpr();
        if (!parser.buffer.eof()) {
            throw new ParseException("表达式后多余 token: " + parser.buffer.peek(0), parser.buffer.peek(0).start());
        }
        return expr;
    }

    /** 便捷入口：逗号分隔表达式清单到 EOF（SELECT 投影位驱动，测试与对拍用）。 */
    public static List<Expr> parseList(final String sql) {
        final ExprParser parser = new ExprParser(new TokenBuffer(sql));
        final List<Expr> exprs = new ArrayList<Expr>();
        while (!parser.buffer.eof()) {
            exprs.add(parser.parseExpr());
            if (TokenType.COMMA == parser.buffer.peek(0).type()) {
                parser.tok();
            } else if (!parser.buffer.eof()) {
                throw new ParseException("清单中期望 , 或结束: " + parser.buffer.peek(0), parser.buffer.peek(0).start());
            }
        }
        return exprs;
    }

    /** 解析一个表达式（最低优先级门槛），在无结合力的 token（,/)/END/FROM/…）处自然停。 */
    public Expr parseExpr() {
        return parseExpr(0);
    }

    private Expr parseExpr(final int minPrec) {
        Expr left = parsePrimary();
        while (true) {
            final Token at = buffer.peek(0);
            final Integer prec = infixBindingPower(at);
            if (null == prec || prec < minPrec) {
                return left;
            }
            left = parseInfix(left, at, prec);
        }
    }

    // ---------- 前缀（nud） ----------

    private Expr parsePrimary() {
        final Token token = buffer.peek(0);
        switch (token.type()) {
            case NUMBER:
            case HEX:
            case BIT:
                tok();
                return new Literal(token.start(), token.stop(), kindOf(token.type()), token.text());
            case STRING:
                tok();
                return new Literal(token.start(), token.stop(), LiteralKind.STRING, token.text());
            case PARAM:
                tok();
                return new ParameterMarker(token.start(), token.stop(), paramCounter++);
            case IDENTIFIER:
            case QUOTED_IDENTIFIER:
            case KEYWORD:
                return parseWordStart();
            case OPERATOR:
                if ("*".equals(token.text())) {
                    tok();
                    return new Star(token.start(), token.stop(), null);
                }
                if ("-".equals(token.text()) || "+".equals(token.text()) || "!".equals(token.text()) || "~".equals(token.text())) {
                    tok();
                    final Expr operand = parseExpr(PREC_UNARY);
                    return new UnaryExpr(token.start(), operand.stop(), token.text(), operand, false);
                }
                throw new ParseException("非法表达式起点: " + token, token.start());
            case LPAREN:
                return parseParenStart();
            default:
                throw new ParseException("非法表达式起点: " + token, token.start());
        }
    }

    /** 关键字前缀：NULL/TRUE/FALSE、时间字面量、CASE、NOT、EXISTS、非保留字标识符路径。 */
    private Expr parseWordStart() {
        final Token token = buffer.peek(0);
        if (TokenType.IDENTIFIER == token.type() || TokenType.QUOTED_IDENTIFIER == token.type()) {
            return parseNameOrCall();
        }
        final Kw kw = token.kw();
        if (Kw.NULL == kw) {
            tok();
            return new Literal(token.start(), token.stop(), LiteralKind.NULL, token.text());
        }
        if (Kw.TRUE == kw || Kw.FALSE == kw) {
            tok();
            return new Literal(token.start(), token.stop(), Kw.TRUE == kw ? LiteralKind.TRUE : LiteralKind.FALSE, token.text());
        }
        if (Kw.DATE == kw || Kw.TIME == kw || Kw.TIMESTAMP == kw) {
            if (TokenType.STRING == buffer.peek(1).type()) {
                final Token str = buffer.peek(1);
                tok();
                tok();
                return new Literal(token.start(), str.stop(), LiteralKind.TEMPORAL, span(token, str, token.text() + str.text()));
            }
            return parseNameOrCall();
        }
        if (Kw.CASE == kw) {
            return parseCase();
        }
        if (Kw.NOT == kw) {
            tok();
            final Expr operand = parseExpr(PREC_NOT + 1);
            return new UnaryExpr(token.start(), operand.stop(), token.text(), operand, false);
        }
        if (Kw.EXISTS == kw) {
            tok();
            expectType(TokenType.LPAREN, "EXISTS 后的 (");
            final SelectQuery query = subqueries.parseSelectQuery();
            final Token rparen = expectType(TokenType.RPAREN, "EXISTS 子查询后的 )");
            return new ExistsExpr(token.start(), rparen.stop(), query);
        }
        if (!profile.reserved(kw)) {
            return parseNameOrCall();
        }
        // 保留字函数名（如 CONVERT(a, CHAR)）：合法 MySQL 但 v1 白名单外，走白名单拒绝而非语法错误
        if (TokenType.LPAREN == buffer.peek(1).type() && BLOCKED_FUNCTION_NAMES.contains(kw.name())) {
            throw unsupported(kw.name() + " 特殊形态函数语法", token);
        }
        throw new ParseException("保留字出现在表达式起点: " + token, token.start());
    }

    /** 括号起点：(SELECT → 标量子查询；(expr)；(expr, …) → 行构造器（白名单外）。 */
    private Expr parseParenStart() {
        final Token lparen = buffer.peek(0);
        if (buffer.peek(1).isKw(Kw.SELECT)) {
            tok();
            final SelectQuery query = subqueries.parseSelectQuery();
            final Token rparen = expectType(TokenType.RPAREN, "子查询后的 )");
            return new SubqueryExpr(lparen.start(), rparen.stop(), query);
        }
        tok();
        final Expr inner = parseExpr();
        final Token after = buffer.peek(0);
        if (TokenType.COMMA == after.type()) {
            throw unsupported("行构造器 (a, b)", after);
        }
        if (TokenType.RPAREN != after.type()) {
            throw new ParseException("期望 ) 但为 " + after, after.start());
        }
        tok();
        return inner;
    }

    private Expr parseCase() {
        final Token caseTok = buffer.peek(0);
        tok();
        Expr operand = null;
        if (!buffer.peek(0).isKw(Kw.WHEN)) {
            operand = parseExpr();
        }
        final List<WhenClause> whens = new ArrayList<WhenClause>();
        while (buffer.peek(0).isKw(Kw.WHEN)) {
            final Token whenTok = buffer.peek(0);
            tok();
            final Expr condition = parseExpr();
            expectKw(Kw.THEN, "WHEN 条件后的 THEN");
            final Expr result = parseExpr();
            whens.add(new WhenClause(whenTok.start(), result.stop(), condition, result));
        }
        if (whens.isEmpty()) {
            throw new ParseException("CASE 至少需要一个 WHEN 分支", caseTok.start());
        }
        Expr elseResult = null;
        if (buffer.peek(0).isKw(Kw.ELSE)) {
            tok();
            elseResult = parseExpr();
        }
        final Token end = expectKw(Kw.END, "CASE 的 END");
        return new CaseExpr(caseTok.start(), end.stop(), operand, whens, elseResult);
    }

    /** 标识符/非保留字：函数调用（单名+括号）或限定名链（含限定星号 t.*）。 */
    private Expr parseNameOrCall() {
        final Token first = buffer.peek(0);
        final boolean callable = TokenType.IDENTIFIER == first.type()
                || TokenType.KEYWORD == first.type() && !profile.reserved(first.kw());
        if (callable && TokenType.LPAREN == buffer.peek(1).type()) {
            return parseFunctionCall(first);
        }
        final List<Token> parts = new ArrayList<Token>();
        parts.add(tok());
        while (TokenType.DOT == buffer.peek(0).type() && isNamePart(buffer.peek(1))) {
            tok();
            parts.add(tok());
        }
        final Token last = parts.get(parts.size() - 1);
        // 限定星号：a.b.* → Star(qualifier="a.b")
        if (TokenType.DOT == buffer.peek(0).type() && TokenType.OPERATOR == buffer.peek(1).type()
                && "*".equals(buffer.peek(1).text())) {
            tok();
            final Token star = tok();
            return new Star(first.start(), star.stop(), span(parts.get(0), last, joinParts(parts)));
        }
        final String name = last.text();
        final String qualifier;
        if (parts.size() > 1) {
            final Token qualifierEnd = parts.get(parts.size() - 2);
            qualifier = span(parts.get(0), qualifierEnd, joinParts(parts.subList(0, parts.size() - 1)));
        } else {
            qualifier = null;
        }
        return new ColumnRef(first.start(), last.stop(), qualifier, name);
    }

    private static String joinParts(final List<Token> parts) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(parts.get(i).text());
        }
        return sb.toString();
    }

    private boolean isNamePart(final Token token) {
        return TokenType.IDENTIFIER == token.type() || TokenType.QUOTED_IDENTIFIER == token.type()
                || TokenType.KEYWORD == token.type() && !profile.reserved(token.kw());
    }

    private Expr parseFunctionCall(final Token nameToken) {
        final String upper = nameToken.text().toUpperCase(java.util.Locale.ROOT);
        if (BLOCKED_FUNCTION_NAMES.contains(upper)) {
            throw unsupported(upper + " 特殊形态函数语法", nameToken);
        }
        if (TokenType.QUOTED_IDENTIFIER == nameToken.type()) {
            throw unsupported("包裹标识符作函数名", nameToken);
        }
        tok();
        tok();
        boolean distinct = false;
        if (buffer.peek(0).isKw(Kw.DISTINCT)) {
            distinct = true;
            tok();
        }
        final List<Expr> arguments = new ArrayList<Expr>();
        if (TokenType.RPAREN != buffer.peek(0).type()) {
            while (true) {
                arguments.add(parseExpr());
                if (TokenType.COMMA == buffer.peek(0).type()) {
                    tok();
                    continue;
                }
                break;
            }
        }
        final Token rparen = expectType(TokenType.RPAREN, "函数实参后的 )");
        return new FunctionCall(nameToken.start(), rparen.stop(), nameToken.text(), arguments, distinct);
    }

    // ---------- 中缀（led）与特判 ----------

    private Expr parseInfix(final Expr left, final Token at, final int prec) {
        if (TokenType.OPERATOR == at.type()) {
            tok();
            final Expr right = parseExpr(prec + 1);
            return new BinaryExpr(left.start(), right.stop(), at.text(), left, right);
        }
        final Kw kw = at.kw();
        if (Kw.AND == kw || Kw.OR == kw || Kw.XOR == kw || Kw.REGEXP == kw || Kw.RLIKE == kw || Kw.DIV == kw || Kw.MOD == kw) {
            tok();
            final Expr right = parseExpr(prec + 1);
            return new BinaryExpr(left.start(), right.stop(), at.text(), left, right);
        }
        if (Kw.IS == kw) {
            return parseIsSuffix(left);
        }
        if (Kw.LIKE == kw) {
            return parseLikeSuffix(left, false);
        }
        if (Kw.IN == kw) {
            return parseInSuffix(left, false);
        }
        if (Kw.BETWEEN == kw) {
            return parseBetweenSuffix(left, false);
        }
        if (Kw.NOT == kw) {
            // 表达式后的 NOT 只能引导 NOT IN/LIKE/BETWEEN，否则语法错误
            if (buffer.peek(1).isKw(Kw.IN)) {
                tok();
                return parseInSuffix(left, true);
            }
            if (buffer.peek(1).isKw(Kw.LIKE)) {
                tok();
                return parseLikeSuffix(left, true);
            }
            if (buffer.peek(1).isKw(Kw.BETWEEN)) {
                tok();
                return parseBetweenSuffix(left, true);
            }
            throw new ParseException("表达式后的 NOT 未跟 IN/LIKE/BETWEEN: " + at, at.start());
        }
        throw new ParseException("非法中缀位置 token: " + at, at.start());
    }

    private Expr parseIsSuffix(final Expr left) {
        tok();
        boolean not = false;
        if (buffer.peek(0).isKw(Kw.NOT)) {
            not = true;
            tok();
        }
        final Token after = buffer.peek(0);
        if (after.isKw(Kw.TRUE) || after.isKw(Kw.FALSE)) {
            throw unsupported("IS TRUE/IS FALSE 谓词", after);
        }
        if (!after.isKw(Kw.NULL)) {
            throw new ParseException("期望 IS [NOT] NULL 但为 " + after, after.start());
        }
        final Token nullTok = tok();
        return new IsNullExpr(left.start(), nullTok.stop(), left, not);
    }

    private Expr parseLikeSuffix(final Expr left, final boolean not) {
        tok();
        final Expr pattern = parseExpr(PREC_CMP + 1);
        Expr escape = null;
        if (buffer.peek(0).isKw(Kw.ESCAPE)) {
            tok();
            escape = parseExpr(PREC_CMP + 1);
        }
        final int stop = null != escape ? escape.stop() : pattern.stop();
        return new LikeExpr(left.start(), stop, left, not, pattern, escape);
    }

    private Expr parseInSuffix(final Expr left, final boolean not) {
        tok();
        expectType(TokenType.LPAREN, "IN 后的 (");
        if (buffer.peek(0).isKw(Kw.SELECT)) {
            final SelectQuery query = subqueries.parseSelectQuery();
            final Token rparen = expectType(TokenType.RPAREN, "IN 子查询后的 )");
            return new InExpr(left.start(), rparen.stop(), left, not, null, query);
        }
        final List<Expr> values = new ArrayList<Expr>();
        while (true) {
            values.add(parseExpr());
            if (TokenType.COMMA == buffer.peek(0).type()) {
                tok();
                continue;
            }
            break;
        }
        final Token rparen = expectType(TokenType.RPAREN, "IN 清单后的 )");
        return new InExpr(left.start(), rparen.stop(), left, not, values, null);
    }

    /** BETWEEN 特判：low 以 PREC_BETWEEN+1 门槛解析，逻辑 AND（更低优先级）自然停在其归属位。 */
    private Expr parseBetweenSuffix(final Expr left, final boolean not) {
        tok();
        final Expr low = parseExpr(PREC_BETWEEN + 1);
        expectKw(Kw.AND, "BETWEEN 的 AND");
        final Expr high = parseExpr(PREC_BETWEEN + 1);
        return new BetweenExpr(left.start(), high.stop(), left, not, low, high);
    }

    // ---------- 结合力与工具 ----------

    private Integer infixBindingPower(final Token token) {
        if (TokenType.OPERATOR == token.type()) {
            final String text = token.text();
            if ("||".equals(text)) {
                return PREC_OR;
            }
            if ("&&".equals(text)) {
                return PREC_AND;
            }
            if ("=".equals(text) || "<=>".equals(text) || ">=".equals(text) || ">".equals(text)
                    || "<=".equals(text) || "<".equals(text) || "<>".equals(text) || "!=".equals(text)
                    || "->".equals(text) || "->>".equals(text)) {
                return PREC_CMP;
            }
            if ("|".equals(text)) {
                return PREC_BIT_OR;
            }
            if ("&".equals(text)) {
                return PREC_BIT_AND;
            }
            if ("<<".equals(text) || ">>".equals(text)) {
                return PREC_SHIFT;
            }
            if ("+".equals(text) || "-".equals(text)) {
                return PREC_ADD;
            }
            if ("*".equals(text) || "/".equals(text) || "%".equals(text)) {
                return PREC_MUL;
            }
            if ("^".equals(text)) {
                return PREC_BIT_XOR;
            }
            return null;
        }
        if (TokenType.KEYWORD == token.type()) {
            switch (token.kw()) {
                case OR:
                    return PREC_OR;
                case XOR:
                    return PREC_XOR;
                case AND:
                    return PREC_AND;
                case IS:
                case LIKE:
                case IN:
                case REGEXP:
                case RLIKE:
                    return PREC_CMP;
                case BETWEEN:
                    return PREC_BETWEEN;
                case NOT:
                    // 仅当 NOT 引导 IN/LIKE/BETWEEN 时算中缀
                    return buffer.peek(1).isKw(Kw.IN) || buffer.peek(1).isKw(Kw.LIKE) || buffer.peek(1).isKw(Kw.BETWEEN) ? PREC_CMP : null;
                case DIV:
                case MOD:
                    return PREC_MUL;
                default:
                    return null;
            }
        }
        return null;
    }

    private Token tok() {
        return buffer.next();
    }

    private Token expectKw(final Kw kw, final String what) {
        final Token token = buffer.peek(0);
        if (!token.isKw(kw)) {
            throw new ParseException("期望 " + what + " 但为 " + token, token.start());
        }
        return tok();
    }

    private Token expectType(final TokenType type, final String what) {
        final Token token = buffer.peek(0);
        if (token.type() != type) {
            throw new ParseException("期望 " + what + " 但为 " + token, token.start());
        }
        return tok();
    }

    private static LiteralKind kindOf(final TokenType type) {
        if (TokenType.HEX == type) {
            return LiteralKind.HEX;
        }
        if (TokenType.BIT == type) {
            return LiteralKind.BIT;
        }
        return LiteralKind.NUMBER;
    }

    /** 跨 token 原文切片（限定名/时间字面量保源形态）；source 缺失时用调用方给的退化拼接种。 */
    private String span(final Token first, final Token last, final String fallback) {
        final String source = buffer.source();
        return null != source ? source.substring(first.start(), last.stop() + 1) : fallback;
    }

    private static UnsupportedSyntaxException unsupported(final String what, final Token at) {
        return new UnsupportedSyntaxException("白名单外: " + what + " @" + at.start());
    }
}
