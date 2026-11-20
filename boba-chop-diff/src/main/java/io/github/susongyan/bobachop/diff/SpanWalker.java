package io.github.susongyan.bobachop.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import io.github.susongyan.bobachop.ast.Assignment;
import io.github.susongyan.bobachop.ast.BetweenExpr;
import io.github.susongyan.bobachop.ast.BinaryExpr;
import io.github.susongyan.bobachop.ast.CaseExpr;
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
import io.github.susongyan.bobachop.ast.Node;
import io.github.susongyan.bobachop.ast.OrderByItem;
import io.github.susongyan.bobachop.ast.SelectItem;
import io.github.susongyan.bobachop.ast.SelectSource;
import io.github.susongyan.bobachop.ast.SelectStatement;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.SubqueryExpr;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.ast.UnaryExpr;
import io.github.susongyan.bobachop.ast.UpdateStatement;
import io.github.susongyan.bobachop.ast.ValuesSource;
import io.github.susongyan.bobachop.ast.WhenClause;

/**
 * 冻结 AST 的结构遍历函数：节点 → 源序子节点清单（叶节点空表）。
 *
 * <p>对拍体系 v2 F1（偏移不变量校验器）的遍历底座——不变量校验
 * {@link SpanInvariants} 以本函数为唯一结构事实源，不自行 instanceof 分支，
 * 保证"新 AST 类型漏更遍历器"只会有一处暴露。与 AST 契约同冻结纪律：
 * 追加节点类型必须同 PR 补 {@link #children} 分支，否则遍历静默缺子树
 * ——由 {@code SpanWalkerTest} 的全类型覆盖用例兜底。</p>
 *
 * <p>字符串字段（别名、列清单、USING 列、限定前缀）非 {@link Node}，不进子节点清单；
 * 其偏移不在 D5 契约内（潜在改写需求已记 WHITELIST §8 后续观察项）。</p>
 */
public final class SpanWalker {

    private SpanWalker() {
    }

    /** 源序子节点清单（新集合，可安全持有）。 */
    public static List<Node> children(final Node node) {
        final List<Node> out = new ArrayList<Node>();
        if (node instanceof SelectStatement) {
            final SelectStatement s = (SelectStatement) node;
            addAll(out, s.items());
            add(out, s.from());
            add(out, s.where());
            addAll(out, s.groupBy());
            add(out, s.having());
            addAll(out, s.orderBy());
            add(out, s.limit());
        } else if (node instanceof CompoundSelect) {
            addAll(out, ((CompoundSelect) node).queries());
        } else if (node instanceof InsertStatement) {
            final InsertStatement i = (InsertStatement) node;
            add(out, i.table());
            add(out, i.source());
            addAll(out, i.onDuplicateKey());
            addAll(out, i.returning());
        } else if (node instanceof UpdateStatement) {
            final UpdateStatement u = (UpdateStatement) node;
            add(out, u.table());
            addAll(out, u.assignments());
            add(out, u.where());
            addAll(out, u.returning());
        } else if (node instanceof DeleteStatement) {
            final DeleteStatement d = (DeleteStatement) node;
            add(out, d.table());
            add(out, d.where());
            addAll(out, d.returning());
        } else if (node instanceof TableName) {
            // 叶子：qualifier/name/alias 为字符串字段
        } else if (node instanceof DerivedTable) {
            add(out, ((DerivedTable) node).query());
        } else if (node instanceof Join) {
            final Join j = (Join) node;
            add(out, j.left());
            add(out, j.right());
            add(out, j.condition());
        } else if (node instanceof SelectItem) {
            add(out, ((SelectItem) node).expr());
        } else if (node instanceof OrderByItem) {
            add(out, ((OrderByItem) node).expr());
        } else if (node instanceof Assignment) {
            final Assignment a = (Assignment) node;
            add(out, a.column());
            add(out, a.value());
        } else if (node instanceof Limit) {
            final Limit l = (Limit) node;
            // 注意：子字段源序随方言形态翻转（LIMIT n OFFSET o vs OFFSET o FETCH FIRST n），
            // 本清单只承诺遍历完整性，不承诺字段间源序——顺序不变量因此不进校验集
            add(out, l.rowCount());
            add(out, l.offset());
        } else if (node instanceof SelectSource) {
            add(out, ((SelectSource) node).query());
        } else if (node instanceof ValuesSource) {
            for (final List<Expr> row : ((ValuesSource) node).rows()) {
                addAll(out, row);
            }
        } else if (node instanceof BinaryExpr) {
            final BinaryExpr b = (BinaryExpr) node;
            add(out, b.left());
            add(out, b.right());
        } else if (node instanceof UnaryExpr) {
            add(out, ((UnaryExpr) node).operand());
        } else if (node instanceof BetweenExpr) {
            final BetweenExpr b = (BetweenExpr) node;
            add(out, b.target());
            add(out, b.low());
            add(out, b.high());
        } else if (node instanceof InExpr) {
            final InExpr i = (InExpr) node;
            add(out, i.target());
            addAll(out, i.values());
            add(out, i.query());
        } else if (node instanceof LikeExpr) {
            final LikeExpr l = (LikeExpr) node;
            add(out, l.target());
            add(out, l.pattern());
            add(out, l.escape());
        } else if (node instanceof IsNullExpr) {
            add(out, ((IsNullExpr) node).target());
        } else if (node instanceof ExistsExpr) {
            add(out, ((ExistsExpr) node).query());
        } else if (node instanceof SubqueryExpr) {
            add(out, ((SubqueryExpr) node).query());
        } else if (node instanceof CaseExpr) {
            final CaseExpr c = (CaseExpr) node;
            add(out, c.operand());
            addAll(out, c.whens());
            add(out, c.elseResult());
        } else if (node instanceof WhenClause) {
            final WhenClause w = (WhenClause) node;
            add(out, w.condition());
            add(out, w.result());
        } else if (node instanceof FunctionCall) {
            addAll(out, ((FunctionCall) node).arguments());
        }
        // 叶子（ColumnRef/Literal/Star/ParameterMarker）无子节点
        return out;
    }

    /** 自根深度遍历（前序），visitor 收到 (节点, 父节点)。 */
    public static void walk(final Statement root, final BiConsumer<Node, Node> visitor) {
        visit(root, null, visitor);
    }

    private static void visit(final Node node, final Node parent, final BiConsumer<Node, Node> visitor) {
        visitor.accept(node, parent);
        for (final Node child : children(node)) {
            visit(child, node, visitor);
        }
    }

    private static void add(final List<Node> out, final Node node) {
        if (null != node) {
            out.add(node);
        }
    }

    private static void addAll(final List<Node> out, final List<? extends Node> nodes) {
        if (null != nodes) {
            out.addAll(nodes);
        }
    }
}
