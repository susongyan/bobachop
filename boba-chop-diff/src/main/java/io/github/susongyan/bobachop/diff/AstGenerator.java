package io.github.susongyan.bobachop.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import io.github.susongyan.bobachop.ast.Assignment;
import io.github.susongyan.bobachop.ast.BetweenExpr;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.CaseExpr;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.CompoundSelect;
import io.github.susongyan.bobachop.ast.DeleteStatement;
import io.github.susongyan.bobachop.ast.DerivedTable;
import io.github.susongyan.bobachop.ast.ExistsExpr;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.FunctionCall;
import io.github.susongyan.bobachop.ast.InExpr;
import io.github.susongyan.bobachop.ast.InsertStatement;
import io.github.susongyan.bobachop.ast.IsNullExpr;
import io.github.susongyan.bobachop.ast.Join;
import io.github.susongyan.bobachop.ast.JoinType;
import io.github.susongyan.bobachop.ast.LikeExpr;
import io.github.susongyan.bobachop.ast.Limit;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.LiteralKind;
import io.github.susongyan.bobachop.ast.OrderByItem;
import io.github.susongyan.bobachop.ast.OrderDirection;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.SelectItem;
import io.github.susongyan.bobachop.ast.SelectQuery;
import io.github.susongyan.bobachop.ast.SelectSource;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.SetOperator;
import io.github.susongyan.bobachop.ast.Star;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.SubqueryExpr;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.ast.TableRef;
import io.github.susongyan.bobachop.ast.UnaryExpr;
import io.github.susongyan.bobachop.ast.UpdateStatement;
import io.github.susongyan.bobachop.ast.ValuesSource;
import io.github.susongyan.bobachop.ast.WhenClause;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.lexer.Kw;

/**
 * 对拍体系 v2 F2——规格生成器：按冻结 AST 契约加权递归采样合法语句树。
 *
 * <p><b>生成器即规格的可执行形态</b>：只产出 WHITELIST.md 白名单内的树（逗号连接无条件、
 * 派生表别名必填、ODKU 仅 MySQL、Limit.rowCount 必填、UNION 操作数不带 ORDER BY/LIMIT、
 * 表别名 AS 随 {@link DialectProfile#tableAliasAs()}、分页形态随方言）——生成器与解析器
 * 同规格两种机制，往返对拍（{@code parse(render(ast)) ≡ ast}）因此构成 N 版本校验。</p>
 *
 * <p><b>非保留字采样</b>（专打 T10 类缺陷的窝点）：标识符池混入该方言非保留字——
 * 别名位/表达式头出现 {@code left}/{@code join}/{@code row} 这类词的语句是 T10 缺陷的
 * 高发区。池剔除<b>位置键控让位词</b>：连接延续词（joinStartAhead 会让位）、
 * PG 的 SET、语句尾锚词（FOR/LOCK/WINDOW/EXCEPT/... 与 Oracle 的 MINUS/START/CONNECT）、
 * 表达式头键控词（Oracle 池的 CASE/WHEN/END/TRUE/FALSE——别名位可作标识符、
 * 表达式头不可）——它们只在特定邻接下合法，生成器不做邻接感知，保守剔除。</p>
 *
 * <p><b>参数访问序</b>：{@link ParameterMarker#index()} 按访问序分配且白名单构造内访问序=文本序，
 * 故生成序严格跟随渲染序（items→FROM→WHERE→GROUP/HAVING→ORDER→分页按方言形态），
 * 往返后 index 逐标记相等是断言的一部分。</p>
 *
 * <p>种子可复现：{@code new AstGenerator(profile, new Random(seed))}；CI 默认种子确定不闪，
 * 夜间扫量换种子加大 {@code count}。v1 保守面（后续可放宽）：包裹标识符不生成、
 * 子查询/派生表内不带 ORDER BY/LIMIT、UNION 仅两层、无 STRAIGHT_JOIN。</p>
 */
public final class AstGenerator {

    private static final String[] PLAIN_COLUMNS = {"id", "a", "b", "c", "amount", "status", "user_id", "cnt"};
    private static final String[] PLAIN_TABLES = {"t1", "t2", "t3", "orders", "users"};
    private static final String[] PLAIN_ALIASES = {"x", "y", "q", "d", "m"};
    private static final String[] FUNCTIONS = {"ABS", "CONCAT", "COUNT", "SUM", "MAX", "MIN", "UPPER", "COALESCE"};
    private static final String[] INFIX = {"+", "-", "*", "/", "%", "=", "<>", "!=", "<", "<=", ">", ">=", "AND", "OR", "||"};
    private static final String[] PREFIX = {"NOT", "-", "+"};
    private static final String[] SAFE_STRING = {"abc", "A b", "x1", "PAID", "o''b", "s''s"};
    private static final String[] SCHEMAS = {"db1", "app"};

    /**
     * 位置键控让位词（三方言并集，池统一剔除）：① 连接延续词（joinStartAhead 会让位）；
     * ② 语句尾锚词（FOR/LOCK/WINDOW/EXCEPT/...）；③ 表达式头键控词——CASE 产式词
     * （CASE/WHEN/END）与布尔字面量（TRUE/FALSE）在别名位可作标识符、表达式头不可，
     * 生成器不做邻接感知，保守剔除（PG 已按附录 C 保留、MySQL 保留，仅 Oracle 池含）。
     */
    private static final Kw[] YIELD_WORDS = {Kw.JOIN, Kw.LEFT, Kw.RIGHT, Kw.FULL, Kw.INNER, Kw.CROSS, Kw.NATURAL,
        Kw.OUTER, Kw.STRAIGHT_JOIN, Kw.SET, Kw.FOR, Kw.LOCK, Kw.WINDOW, Kw.INTO, Kw.PROCEDURE,
        Kw.EXCEPT, Kw.INTERSECT, Kw.MINUS, Kw.START, Kw.CONNECT,
        Kw.CASE, Kw.WHEN, Kw.END, Kw.TRUE, Kw.FALSE};

    private final DialectProfile profile;
    private final Random random;
    private final List<String> wordNames = new ArrayList<String>();
    private final int maxExprDepth;

    public AstGenerator(final DialectProfile profile, final Random random) {
        this(profile, random, 4);
    }

    public AstGenerator(final DialectProfile profile, final Random random, final int maxExprDepth) {
        this.profile = profile;
        this.random = random;
        this.maxExprDepth = maxExprDepth;
        for (final Kw kw : profile.lexerConfig().keywords().nonReservedWords()) {
            if (!isYieldWord(kw)) {
                wordNames.add(kw.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static boolean isYieldWord(final Kw kw) {
        for (final Kw yield : YIELD_WORDS) {
            if (yield == kw) {
                return true;
            }
        }
        return false;
    }

    /** 采样一条语句。参数访问序从 0 重新计数（解析器对每条语句独立编号）。 */
    public Statement statement() {
        nextParam = 0;
        final int pick = random.nextInt(100);
        if (pick < 55) {
            return (Statement) selectQuery(maxExprDepth);
        }
        if (pick < 70) {
            return insertStatement();
        }
        if (pick < 85) {
            return updateStatement();
        }
        return deleteStatement();
    }

    // ---------- SELECT ----------

    private SelectQuery selectQuery(final int depth) {
        if (random.nextInt(4) == 0) {
            final int operands = 2 + random.nextInt(2);
            final List<SelectQuery> queries = new ArrayList<SelectQuery>();
            final List<SetOperator> operators = new ArrayList<SetOperator>();
            for (int i = 0; i < operands; i++) {
                queries.add(selectBody(false, depth));
                if (i > 0) {
                    operators.add(random.nextBoolean() ? SetOperator.UNION : SetOperator.UNION_ALL);
                }
            }
            return new CompoundSelect(0, 0, queries, operators);
        }
        return selectBody(true, depth);
    }

    /** 查询体；root=true 时允许 ORDER BY/分页（复合操作数与嵌套查询 v1 保守不带）。 */
    private SelectStatement selectBody(final boolean root, final int depth) {
        final int itemCount = 1 + random.nextInt(4);
        final List<SelectItem> items = new ArrayList<SelectItem>();
        for (int i = 0; i < itemCount; i++) {
            final Expr expr;
            if (i == 0 && random.nextInt(10) == 0) {
                expr = new Star(0, 0, random.nextInt(4) == 0 ? tableName() : null);
            } else {
                expr = expr(depth);
            }
            items.add(new SelectItem(0, 0, expr, random.nextInt(3) == 0 ? alias() : null));
        }
        final TableRef from = random.nextInt(10) == 0 ? null : tableRef(depth);
        final Expr where = random.nextInt(10) < 7 ? expr(depth) : null;
        List<Expr> groupBy = null;
        Expr having = null;
        if (random.nextInt(10) < 3) {
            groupBy = new ArrayList<Expr>();
            final int groupCount = 1 + random.nextInt(2);
            for (int i = 0; i < groupCount; i++) {
                groupBy.add(expr(depth - 1));
            }
            if (random.nextBoolean()) {
                having = expr(depth - 1);
            }
        }
        List<OrderByItem> orderBy = null;
        Limit limit = null;
        if (root) {
            if (random.nextInt(10) < 4) {
                orderBy = new ArrayList<OrderByItem>();
                final int orderCount = 1 + random.nextInt(2);
                for (int i = 0; i < orderCount; i++) {
                    orderBy.add(new OrderByItem(0, 0, expr(depth - 1),
                            OrderDirection.values()[random.nextInt(OrderDirection.values().length)]));
                }
            }
            if (random.nextInt(10) < 3) {
                limit = limit();
            }
        }
        return new SelectStatement(0, 0, random.nextInt(7) == 0, items, from, where, groupBy, having, orderBy, limit);
    }

    /**
     * 分页实参窄口=数字字面量或 ?；生成序严格跟随渲染序（参数访问序=文本序的断言前提）：
     * 标准族先 offset 后 rowCount，LIMIT 族先 rowCount 后 offset。形态显式入契约
     * （F3），与渲染序一一对应；逗号形态为 MySQL 输入空间，生成器只产规范形态。
     */
    private Limit limit() {
        final boolean hasOffset = random.nextBoolean();
        if (profile.fetchFirstPagination() && !profile.limitCommaOffset()) {
            final Expr offset = hasOffset ? pageArg() : null;
            final Expr rowCount = pageArg();
            return new Limit(0, 0, offset, rowCount, Limit.Form.FETCH);
        }
        final Expr rowCount = pageArg();
        final Expr offset = hasOffset ? pageArg() : null;
        return new Limit(0, 0, offset, rowCount, Limit.Form.LIMIT);
    }

    private Expr pageArg() {
        return random.nextInt(2) == 0
                ? new Literal(0, 0, LiteralKind.NUMBER, String.valueOf(1 + random.nextInt(200)))
                : new ParameterMarker(0, 0, nextParam++);
    }

    /** 表引用：左结合连接链（右操作数恒为因子——括号化表引用白名单外）。 */
    private TableRef tableRef(final int depth) {
        TableRef current = tableFactor(depth);
        final int joins = random.nextInt(3);
        for (int i = 0; i < joins; i++) {
            final JoinType type = JoinType.values()[random.nextInt(JoinType.values().length)];
            final TableRef right = tableFactor(depth);
            Expr condition = null;
            List<String> using = null;
            if (JoinType.COMMA == type || JoinType.CROSS == type) {
                condition = null;
            } else if (random.nextInt(3) == 0) {
                using = new ArrayList<String>();
                final int cols = 1 + random.nextInt(2);
                for (int c = 0; c < cols; c++) {
                    using.add(columnName());
                }
            } else {
                condition = expr(depth - 1);
            }
            current = new Join(0, 0, current, type, right, condition, using);
        }
        return current;
    }

    private TableRef tableFactor(final int depth) {
        if (random.nextInt(6) == 0) {
            final SelectStatement query = selectBody(false, depth - 1);
            return new DerivedTable(0, 0, query, alias());
        }
        final boolean qualified = random.nextInt(8) == 0;
        return new TableName(0, 0, qualified ? SCHEMAS[random.nextInt(SCHEMAS.length)] : null, tableName(),
                random.nextInt(2) == 0 ? alias() : null);
    }

    // ---------- INSERT / UPDATE / DELETE ----------

    private Statement insertStatement() {
        final int columnCount = 1 + random.nextInt(3);
        List<String> columns = null;
        if (random.nextInt(4) != 0) {
            columns = new ArrayList<String>();
            for (int i = 0; i < columnCount; i++) {
                columns.add(columnName());
            }
        }
        final io.github.susongyan.bobachop.ast.InsertSource source;
        if (random.nextInt(3) == 0) {
            source = new SelectSource(0, 0, selectBody(false, maxExprDepth));
        } else {
            final int rows = 1 + random.nextInt(3);
            final List<List<Expr>> rowList = new ArrayList<List<Expr>>();
            for (int r = 0; r < rows; r++) {
                final List<Expr> row = new ArrayList<Expr>();
                for (int c = 0; c < columnCount; c++) {
                    row.add(expr(2));
                }
                rowList.add(row);
            }
            source = new ValuesSource(0, 0, rowList);
        }
        List<Assignment> odku = null;
        if ("MySQL".equals(profile.name()) && random.nextInt(3) == 0) {
            odku = new ArrayList<Assignment>();
            final int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                odku.add(assignment());
            }
        }
        final TableName table = new TableName(0, 0, null, tableName(), null);
        return new InsertStatement(0, 0, table, columns, source, odku);
    }

    private Statement updateStatement() {
        final int count = 1 + random.nextInt(3);
        final List<Assignment> assignments = new ArrayList<Assignment>();
        for (int i = 0; i < count; i++) {
            assignments.add(assignment());
        }
        final TableName table = new TableName(0, 0, null, tableName(), random.nextInt(2) == 0 ? alias() : null);
        final Expr where = random.nextInt(10) < 7 ? expr(maxExprDepth) : null;
        return new UpdateStatement(0, 0, table, assignments, where);
    }

    private Statement deleteStatement() {
        final TableName table = new TableName(0, 0, null, tableName(), random.nextInt(2) == 0 ? alias() : null);
        final Expr where = random.nextInt(2) == 0 ? expr(maxExprDepth) : null;
        return new DeleteStatement(0, 0, table, where);
    }

    private Assignment assignment() {
        final ColumnRef column = new ColumnRef(0, 0, null, columnName());
        return new Assignment(0, 0, column, expr(maxExprDepth - 1));
    }

    // ---------- 表达式 ----------

    private int nextParam;

    private Expr expr(final int depth) {
        if (depth <= 0) {
            return atom();
        }
        final int pick = random.nextInt(100);
        if (pick < 30) {
            return atom();
        }
        if (pick < 55) {
            return new BinaryExpr(0, 0, INFIX[random.nextInt(INFIX.length)], expr(depth - 1), expr(depth - 1));
        }
        if (pick < 62) {
            return new UnaryExpr(0, 0, PREFIX[random.nextInt(PREFIX.length)], expr(depth - 1), false);
        }
        if (pick < 68) {
            return new BetweenExpr(0, 0, expr(depth - 1), random.nextBoolean(), expr(depth - 1), expr(depth - 1));
        }
        if (pick < 76) {
            // 生成序=文本序（参数访问序断言的前提）：target 先于 IN 清单/子查询
            final Expr target = expr(depth - 1);
            final boolean negated = random.nextBoolean();
            if (random.nextInt(3) == 0) {
                return new InExpr(0, 0, target, negated, null, selectBody(false, depth - 1));
            }
            final List<Expr> values = new ArrayList<Expr>();
            final int count = 1 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                values.add(expr(depth - 1));
            }
            return new InExpr(0, 0, target, negated, values, null);
        }
        if (pick < 82) {
            final LikeExpr like = new LikeExpr(0, 0, expr(depth - 1), random.nextBoolean(),
                    stringLiteral(), random.nextInt(4) == 0 ? stringLiteral() : null);
            return like;
        }
        if (pick < 87) {
            return new IsNullExpr(0, 0, expr(depth - 1), random.nextBoolean());
        }
        if (pick < 90) {
            return new ExistsExpr(0, 0, selectBody(false, depth - 1));
        }
        if (pick < 93) {
            return new SubqueryExpr(0, 0, selectBody(false, depth - 1));
        }
        if (pick < 97) {
            // 生成序=文本序：CASE 操作数在最前（构造器实参求值序陷阱——局部变量先建）
            final Expr operand = random.nextInt(2) == 0 ? expr(depth - 1) : null;
            final int whens = 1 + random.nextInt(2);
            final List<WhenClause> clauses = new ArrayList<WhenClause>();
            for (int i = 0; i < whens; i++) {
                clauses.add(new WhenClause(0, 0, expr(depth - 1), expr(depth - 1)));
            }
            final Expr elseResult = random.nextInt(3) == 0 ? expr(depth - 1) : null;
            return new CaseExpr(0, 0, operand, clauses, elseResult);
        }
        final int args = random.nextInt(3);
        final List<Expr> arguments = new ArrayList<Expr>();
        for (int i = 0; i < args; i++) {
            arguments.add(expr(depth - 1));
        }
        return new FunctionCall(0, 0, FUNCTIONS[random.nextInt(FUNCTIONS.length)], arguments,
                args == 1 && random.nextInt(4) == 0);
    }

    private Expr atom() {
        final int pick = random.nextInt(100);
        if (pick < 55) {
            final int levels = random.nextInt(10);
            final String qualifier = levels < 3 ? alias() : levels < 4 ? alias() + "." + alias() : null;
            return new ColumnRef(0, 0, qualifier, columnName());
        }
        if (pick < 85) {
            return literal();
        }
        return new ParameterMarker(0, 0, nextParam++);
    }

    private Expr literal() {
        final int pick = random.nextInt(100);
        if (pick < 45) {
            return new Literal(0, 0, LiteralKind.NUMBER, String.valueOf(1 + random.nextInt(999999)));
        }
        if (pick < 60) {
            return new Literal(0, 0, LiteralKind.NUMBER,
                    (1 + random.nextInt(999)) + "." + (1 + random.nextInt(999)));
        }
        if (pick < 70) {
            return stringLiteral();
        }
        if (pick < 76) {
            return new Literal(0, 0, LiteralKind.NULL, "NULL");
        }
        if (pick < 82) {
            return new Literal(0, 0, LiteralKind.TRUE, "TRUE");
        }
        if (pick < 88) {
            return new Literal(0, 0, LiteralKind.FALSE, "FALSE");
        }
        if (pick < 94) {
            return new Literal(0, 0, LiteralKind.HEX, "0x" + Integer.toHexString(random.nextInt(0xFFFFF)));
        }
        if (pick < 97 && "MySQL".equals(profile.name())) {
            return new Literal(0, 0, LiteralKind.BIT, "0b" + (random.nextBoolean() ? "1" : "0")
                    + (random.nextBoolean() ? "01" : "10"));
        }
        final String prefix = random.nextBoolean() ? "DATE" : "TIMESTAMP";
        return new Literal(0, 0, LiteralKind.TEMPORAL, prefix + " '2026-01-0" + (1 + random.nextInt(9)) + "'");
    }

    private Expr stringLiteral() {
        final String content = SAFE_STRING[random.nextInt(SAFE_STRING.length)];
        return new Literal(0, 0, LiteralKind.STRING, "'" + content + "'");
    }

    // ---------- 名字池 ----------

    private String tableName() {
        return random.nextInt(6) == 0 ? word() : PLAIN_TABLES[random.nextInt(PLAIN_TABLES.length)];
    }

    private String columnName() {
        return random.nextInt(4) == 0 ? word() : PLAIN_COLUMNS[random.nextInt(PLAIN_COLUMNS.length)];
    }

    private String alias() {
        return random.nextInt(3) == 0 ? word() : PLAIN_ALIASES[random.nextInt(PLAIN_ALIASES.length)];
    }

    /** 该方言非保留字（已剔除让位词）——T10 类别名位/表达式头缺陷的高发采样。 */
    private String word() {
        if (wordNames.isEmpty()) {
            return PLAIN_ALIASES[random.nextInt(PLAIN_ALIASES.length)];
        }
        return wordNames.get(random.nextInt(wordNames.size()));
    }

    /** 本生成器可用的非保留词数（观察面：方言词表覆盖）。 */
    public int wordPoolSize() {
        return wordNames.size();
    }
}
