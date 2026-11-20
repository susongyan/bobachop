package io.github.susongyan.bobachop.diff;

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
import io.github.susongyan.bobachop.ast.LikeExpr;
import io.github.susongyan.bobachop.ast.Limit;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.OrderByItem;
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

/**
 * 对拍体系 v2 F2——canonical 渲染器：冻结 AST → 规范 SQL 文本。
 *
 * <p><b>规范形态</b>（同一棵树恒渲染同一字符串）：列别名恒用 {@code AS}；表别名按
 * {@link DialectProfile#tableAliasAs()} 决定 AS/裸；分页按方言——Oracle 用标准族
 * {@code OFFSET o ROWS FETCH FIRST n ROWS ONLY}，MySQL/PG 用 {@code LIMIT n [OFFSET m]}；
 * 连接词 INNER/LEFT/RIGHT JOIN + ON/USING，CROSS/逗号无条件。</p>
 *
 * <p><b>表达式最小括号化</b>（优先级表镜像 WHITELIST §2 的 13 级，与内核
 * infixBindingPower 同源同值——本表按规格书写，是对内核表的独立复述）：
 * 左结合二元——左子裸当且仅当 子优先级 ≥ 父，右子裸当且仅当 子优先级 &gt; 父；
 * 前缀一元操作数须 ≥ 13 级；BETWEEN/IN/LIKE/IS 的目标须 &gt; 5/6 级。
 * 原子（字面量/列/函数/CASE/参数）恒不括号。</p>
 *
 * <p>F2 往返断言三件：{@code parse(render(ast))} 结构全等（AstDiff.compareStructure）、
 * {@code render(parse(render(ast))) == render(ast)}（打印规范化幂等）、重解析树过
 * SpanInvariants 六条（F1 在生成语料上复用）。渲染器本身是 boba-pour 改写输出的首付。</p>
 */
public final class AstRenderer {

    // 优先级表（WHITELIST §2；数值与 ExprParser 常量一致）
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
    private static final int PREC_ATOM = 99;

    private final DialectProfile profile;

    public AstRenderer(final DialectProfile profile) {
        this.profile = profile;
    }

    /** 渲染整条语句（含尾分号）。 */
    public String render(final Statement statement) {
        final StringBuilder out = new StringBuilder(128);
        renderStatement(statement, out);
        out.append(';');
        return out.toString();
    }

    private void renderStatement(final Statement statement, final StringBuilder out) {
        if (statement instanceof SelectQuery) {
            renderQuery((SelectQuery) statement, out);
        } else if (statement instanceof InsertStatement) {
            renderInsert((InsertStatement) statement, out);
        } else if (statement instanceof UpdateStatement) {
            renderUpdate((UpdateStatement) statement, out);
        } else if (statement instanceof DeleteStatement) {
            renderDelete((DeleteStatement) statement, out);
        } else {
            throw new IllegalArgumentException("未支持的语句类型: " + statement.getClass().getSimpleName());
        }
    }

    // ---------- SELECT ----------

    private void renderQuery(final SelectQuery query, final StringBuilder out) {
        if (query instanceof CompoundSelect) {
            final CompoundSelect compound = (CompoundSelect) query;
            renderQuery(compound.queries().get(0), out);
            for (int i = 1; i < compound.queries().size(); i++) {
                out.append(SetOperator.UNION_ALL == compound.operators().get(i - 1) ? " UNION ALL " : " UNION ");
                renderQuery(compound.queries().get(i), out);
            }
            return;
        }
        renderSelect((SelectStatement) query, out);
    }

    private void renderSelect(final SelectStatement select, final StringBuilder out) {
        out.append("SELECT ");
        if (select.distinct()) {
            out.append("DISTINCT ");
        }
        for (int i = 0; i < select.items().size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            final SelectItem item = select.items().get(i);
            renderExpr(item.expr(), out);
            if (null != item.alias()) {
                out.append(" AS ").append(item.alias());
            }
        }
        if (null != select.from()) {
            out.append(" FROM ");
            renderTableRef(select.from(), out);
        }
        if (null != select.where()) {
            out.append(" WHERE ");
            renderExpr(select.where(), out);
        }
        if (null != select.groupBy()) {
            out.append(" GROUP BY ");
            for (int i = 0; i < select.groupBy().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                renderExpr(select.groupBy().get(i), out);
            }
        }
        if (null != select.having()) {
            // HAVING 与 GROUP BY 契约上独立（隐式分组的裸 HAVING 三方言解析器皆接受，F3 变异体暴露）
            out.append(" HAVING ");
            renderExpr(select.having(), out);
        }
        if (null != select.orderBy()) {
            out.append(" ORDER BY ");
            for (int i = 0; i < select.orderBy().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                final OrderByItem order = select.orderBy().get(i);
                renderExpr(order.expr(), out);
                if (null != order.direction() && io.github.susongyan.bobachop.ast.OrderDirection.UNSPECIFIED != order.direction()) {
                    out.append(' ').append(order.direction());
                }
            }
        }
        if (null != select.limit()) {
            renderLimit(select.limit(), out);
        }
    }

    /**
     * 分页按<b>原书写形态</b>还原（F3：形态入契约）——offset/rowCount 的文本序随形态
     * 翻转，参数访问序随之翻转，规范化为方言默认形态会静默对调绑定值。形态未声明
     * （null，手工构造）→ 方言默认：标准族 OFFSET/FETCH，其余 LIMIT n [OFFSET m]。
     */
    private void renderLimit(final Limit limit, final StringBuilder out) {
        final Limit.Form form = null != limit.form() ? limit.form()
                : fetchForm() ? Limit.Form.FETCH : Limit.Form.LIMIT;
        if (Limit.Form.FETCH == form) {
            if (null != limit.offset()) {
                out.append(" OFFSET ");
                renderExpr(limit.offset(), out);
                out.append(" ROWS ");
            }
            out.append(" FETCH FIRST ");
            renderExpr(limit.rowCount(), out);
            out.append(" ROWS ONLY");
        } else if (Limit.Form.COMMA == form) {
            out.append(" LIMIT ");
            renderExpr(limit.offset(), out);
            out.append(", ");
            renderExpr(limit.rowCount(), out);
        } else {
            out.append(" LIMIT ");
            renderExpr(limit.rowCount(), out);
            if (null != limit.offset()) {
                out.append(" OFFSET ");
                renderExpr(limit.offset(), out);
            }
        }
    }

    private boolean fetchForm() {
        return profile.fetchFirstPagination() && !profile.limitCommaOffset();
    }

    private void renderTableRef(final TableRef ref, final StringBuilder out) {
        if (ref instanceof TableName) {
            renderTableName((TableName) ref, out);
        } else if (ref instanceof DerivedTable) {
            final DerivedTable derived = (DerivedTable) ref;
            out.append('(');
            renderQuery(derived.query(), out);
            out.append(')');
            renderAlias(derived.alias(), out);
        } else {
            final Join join = (Join) ref;
            renderTableRef(join.left(), out);
            if (io.github.susongyan.bobachop.ast.JoinType.COMMA == join.type()) {
                out.append(", ");
            } else {
                out.append(' ').append(joinKeyword(join.type())).append(' ');
            }
            renderTableRef(join.right(), out);
            if (null != join.condition()) {
                out.append(" ON ");
                renderExpr(join.condition(), out);
            } else if (null != join.usingColumns() && !join.usingColumns().isEmpty()) {
                out.append(" USING (");
                for (int i = 0; i < join.usingColumns().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(join.usingColumns().get(i));
                }
                out.append(')');
            }
        }
    }

    private static String joinKeyword(final io.github.susongyan.bobachop.ast.JoinType type) {
        switch (type) {
            case INNER:
                return "INNER JOIN";
            case LEFT:
                return "LEFT JOIN";
            case RIGHT:
                return "RIGHT JOIN";
            case CROSS:
                return "CROSS JOIN";
            default:
                throw new IllegalArgumentException("连接类型缺条件词: " + type);
        }
    }

    private void renderTableName(final TableName table, final StringBuilder out) {
        if (null != table.qualifier()) {
            out.append(table.qualifier()).append('.');
        }
        out.append(table.name());
        renderAlias(table.alias(), out);
    }

    private void renderAlias(final String alias, final StringBuilder out) {
        if (null == alias) {
            return;
        }
        out.append(profile.tableAliasAs() ? " AS " : ' ').append(alias);
    }

    // ---------- INSERT / UPDATE / DELETE ----------

    private void renderInsert(final InsertStatement insert, final StringBuilder out) {
        out.append("INSERT INTO ");
        renderTableNameNoAlias(insert.table(), out);
        if (null != insert.columns() && !insert.columns().isEmpty()) {
            out.append(" (");
            for (int i = 0; i < insert.columns().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(insert.columns().get(i));
            }
            out.append(')');
        }
        if (insert.source() instanceof ValuesSource) {
            out.append(" VALUES ");
            final ValuesSource values = (ValuesSource) insert.source();
            for (int r = 0; r < values.rows().size(); r++) {
                if (r > 0) {
                    out.append(", ");
                }
                out.append('(');
                for (int c = 0; c < values.rows().get(r).size(); c++) {
                    if (c > 0) {
                        out.append(", ");
                    }
                    renderExpr(values.rows().get(r).get(c), out);
                }
                out.append(')');
            }
        } else {
            out.append(' ');
            renderQuery(((SelectSource) insert.source()).query(), out);
        }
        if (null != insert.onDuplicateKey() && !insert.onDuplicateKey().isEmpty()) {
            out.append(" ON DUPLICATE KEY UPDATE ");
            renderAssignments(insert.onDuplicateKey(), out);
        }
    }

    private void renderTableNameNoAlias(final TableName table, final StringBuilder out) {
        if (null != table.qualifier()) {
            out.append(table.qualifier()).append('.');
        }
        out.append(table.name());
    }

    private void renderAssignments(final java.util.List<Assignment> assignments, final StringBuilder out) {
        for (int i = 0; i < assignments.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            final Assignment assignment = assignments.get(i);
            if (null != assignment.column().qualifier()) {
                out.append(assignment.column().qualifier()).append('.');
            }
            out.append(assignment.column().name()).append(" = ");
            renderExpr(assignment.value(), out);
        }
    }

    private void renderUpdate(final UpdateStatement update, final StringBuilder out) {
        out.append("UPDATE ");
        renderTableName(update.table(), out);
        out.append(" SET ");
        renderAssignments(update.assignments(), out);
        if (null != update.where()) {
            out.append(" WHERE ");
            renderExpr(update.where(), out);
        }
    }

    private void renderDelete(final DeleteStatement delete, final StringBuilder out) {
        out.append("DELETE FROM ");
        renderTableName(delete.table(), out);
        if (null != delete.where()) {
            out.append(" WHERE ");
            renderExpr(delete.where(), out);
        }
    }

    // ---------- 表达式 ----------

    private void renderExpr(final Expr expr, final StringBuilder out) {
        if (expr instanceof Literal) {
            out.append(((Literal) expr).text());
        } else if (expr instanceof ColumnRef) {
            final ColumnRef column = (ColumnRef) expr;
            if (null != column.qualifier()) {
                out.append(column.qualifier()).append('.');
            }
            out.append(column.name());
        } else if (expr instanceof Star) {
            final Star star = (Star) expr;
            if (null != star.qualifier()) {
                out.append(star.qualifier()).append(".");
            }
            out.append('*');
        } else if (expr instanceof ParameterMarker) {
            out.append('?');
        } else if (expr instanceof BinaryExpr) {
            renderBinary((BinaryExpr) expr, out);
        } else if (expr instanceof UnaryExpr) {
            renderUnary((UnaryExpr) expr, out);
        } else if (expr instanceof BetweenExpr) {
            renderBetween((BetweenExpr) expr, out);
        } else if (expr instanceof InExpr) {
            renderIn((InExpr) expr, out);
        } else if (expr instanceof LikeExpr) {
            renderLike((LikeExpr) expr, out);
        } else if (expr instanceof IsNullExpr) {
            final IsNullExpr isNull = (IsNullExpr) expr;
            renderChild(isNull.target(), PREC_CMP + 1, out);
            out.append(isNull.not() ? " IS NOT NULL" : " IS NULL");
        } else if (expr instanceof ExistsExpr) {
            out.append("EXISTS (");
            renderQuery(((ExistsExpr) expr).query(), out);
            out.append(')');
        } else if (expr instanceof SubqueryExpr) {
            out.append('(');
            renderQuery(((SubqueryExpr) expr).query(), out);
            out.append(')');
        } else if (expr instanceof CaseExpr) {
            renderCase((CaseExpr) expr, out);
        } else if (expr instanceof FunctionCall) {
            renderFunction((FunctionCall) expr, out);
        } else {
            throw new IllegalArgumentException("未支持的表达式类型: " + expr.getClass().getSimpleName());
        }
    }

    private void renderBinary(final BinaryExpr binary, final StringBuilder out) {
        final int prec = binaryPrecedence(binary.operator());
        renderChild(binary.left(), prec, out);
        out.append(' ').append(binary.operator()).append(' ');
        renderChild(binary.right(), prec + 1, out);
    }

    /** 左结合：左子门槛 = 父级（同级可裸），右子门槛 = 父级+1（同级必括，防重组）。 */
    private void renderChild(final Expr child, final int minPrec, final StringBuilder out) {
        if (precedence(child) < minPrec) {
            out.append('(');
            renderExpr(child, out);
            out.append(')');
        } else {
            renderExpr(child, out);
        }
    }

    private void renderUnary(final UnaryExpr unary, final StringBuilder out) {
        if (unary.postfix()) {
            renderChild(unary.operand(), PREC_UNARY, out);
            out.append(unary.operator());
        } else {
            out.append(unary.operator()).append(' ');
            // NOT 与 -/+ 不同级（§2 表：NOT=4，前缀符号=13），操作数门槛各随其级
            renderChild(unary.operand(), isNot(unary) ? PREC_NOT + 1 : PREC_UNARY, out);
        }
    }

    private static boolean isNot(final UnaryExpr unary) {
        return "NOT".equalsIgnoreCase(unary.operator());
    }

    private void renderBetween(final BetweenExpr between, final StringBuilder out) {
        renderChild(between.target(), PREC_BETWEEN + 1, out);
        out.append(between.not() ? " NOT BETWEEN " : " BETWEEN ");
        renderChild(between.low(), PREC_BETWEEN + 1, out);
        out.append(" AND ");
        renderChild(between.high(), PREC_BETWEEN + 1, out);
    }

    private void renderIn(final InExpr in, final StringBuilder out) {
        renderChild(in.target(), PREC_CMP + 1, out);
        out.append(in.not() ? " NOT IN (" : " IN (");
        if (null != in.query()) {
            renderQuery(in.query(), out);
        } else {
            for (int i = 0; i < in.values().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                renderExpr(in.values().get(i), out);
            }
        }
        out.append(')');
    }

    private void renderLike(final LikeExpr like, final StringBuilder out) {
        renderChild(like.target(), PREC_CMP + 1, out);
        out.append(like.not() ? " NOT LIKE " : " LIKE ");
        renderChild(like.pattern(), PREC_CMP + 1, out);
        if (null != like.escape()) {
            out.append(" ESCAPE ");
            renderChild(like.escape(), PREC_CMP + 1, out);
        }
    }

    private void renderCase(final CaseExpr caseExpr, final StringBuilder out) {
        out.append("CASE");
        if (null != caseExpr.operand()) {
            out.append(' ');
            renderExpr(caseExpr.operand(), out);
        }
        for (final WhenClause when : caseExpr.whens()) {
            out.append(" WHEN ");
            renderExpr(when.condition(), out);
            out.append(" THEN ");
            renderExpr(when.result(), out);
        }
        if (null != caseExpr.elseResult()) {
            out.append(" ELSE ");
            renderExpr(caseExpr.elseResult(), out);
        }
        out.append(" END");
    }

    private void renderFunction(final FunctionCall function, final StringBuilder out) {
        out.append(function.name()).append('(');
        if (function.distinct()) {
            out.append("DISTINCT ");
        }
        for (int i = 0; i < function.arguments().size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            renderExpr(function.arguments().get(i), out);
        }
        out.append(')');
    }

    /** 节点结合力（WHITELIST §2 优先级表；原子恒 99）。 */
    static int precedence(final Expr expr) {
        if (expr instanceof BinaryExpr) {
            return binaryPrecedence(((BinaryExpr) expr).operator());
        }
        if (expr instanceof UnaryExpr) {
            // NOT=4 级（比比较还松，NOT a = b 即 NOT(a=b)），作左子/操作数时须括号
            return isNot((UnaryExpr) expr) ? PREC_NOT : PREC_UNARY;
        }
        if (expr instanceof BetweenExpr) {
            return PREC_BETWEEN;
        }
        if (expr instanceof InExpr || expr instanceof LikeExpr || expr instanceof IsNullExpr) {
            return PREC_CMP;
        }
        return PREC_ATOM;
    }

    private static int binaryPrecedence(final String operator) {
        final String op = operator.toUpperCase(java.util.Locale.ROOT);
        if ("OR".equals(op) || "||".equals(op)) {
            return PREC_OR;
        }
        if ("XOR".equals(op)) {
            return PREC_XOR;
        }
        if ("AND".equals(op) || "&&".equals(op)) {
            return PREC_AND;
        }
        if ("=".equals(op) || "<=>".equals(op) || ">=".equals(op) || ">".equals(op) || "<=".equals(op)
                || "<".equals(op) || "<>".equals(op) || "!=".equals(op) || "->".equals(op) || "->>".equals(op)
                // REGEXP/RLIKE 与比较同级（§2 表；F3 变异体暴露渲染器漏收，解析器已按 PREC_CMP 收录）
                || "REGEXP".equals(op) || "RLIKE".equals(op)) {
            return PREC_CMP;
        }
        if ("|".equals(op)) {
            return PREC_BIT_OR;
        }
        if ("&".equals(op)) {
            return PREC_BIT_AND;
        }
        if ("<<".equals(op) || ">>".equals(op)) {
            return PREC_SHIFT;
        }
        if ("+".equals(op) || "-".equals(op)) {
            return PREC_ADD;
        }
        if ("*".equals(op) || "/".equals(op) || "%".equals(op) || "DIV".equals(op) || "MOD".equals(op)) {
            return PREC_MUL;
        }
        if ("^".equals(op)) {
            return PREC_BIT_XOR;
        }
        throw new IllegalArgumentException("未收录的中缀运算符: " + operator);
    }
}
