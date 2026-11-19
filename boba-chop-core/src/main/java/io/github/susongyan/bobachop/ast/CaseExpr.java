package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * CASE 表达式：简单式（{@code CASE x WHEN ...}，operand 非空）与搜索式（operand 为 null）。
 */
public final class CaseExpr extends AbstractNode implements Expr {

    private final Expr operand;
    private final List<WhenClause> whens;
    private final Expr elseResult;

    public CaseExpr(final int start, final int stop, final Expr operand, final List<WhenClause> whens, final Expr elseResult) {
        super(start, stop);
        this.operand = operand;
        this.whens = Collections.unmodifiableList(new ArrayList<WhenClause>(Objects.requireNonNull(whens, "whens")));
        this.elseResult = elseResult;
    }

    /** 简单式的操作数；搜索式为 null。 */
    public Expr operand() {
        return operand;
    }

    /** WHEN 分支列表（至少一项）。 */
    public List<WhenClause> whens() {
        return whens;
    }

    /** ELSE 结果，缺省为 null。 */
    public Expr elseResult() {
        return elseResult;
    }
}
