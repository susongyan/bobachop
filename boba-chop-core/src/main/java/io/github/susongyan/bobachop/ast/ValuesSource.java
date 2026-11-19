package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * VALUES 形态：{@code VALUES (...), (...)} 多行清单（MySQL 多行、PG 同构）。
 */
public final class ValuesSource extends AbstractNode implements InsertSource {

    private final List<List<Expr>> rows;

    public ValuesSource(final int start, final int stop, final List<List<Expr>> rows) {
        super(start, stop);
        List<List<Expr>> copy = new ArrayList<List<Expr>>();
        for (List<Expr> row : Objects.requireNonNull(rows, "rows")) {
            copy.add(Collections.unmodifiableList(new ArrayList<Expr>(row)));
        }
        this.rows = Collections.unmodifiableList(copy);
    }

    /** 行清单（每行为表达式列表，列数由解析器保证一致或由下游校验）。 */
    public List<List<Expr>> rows() {
        return rows;
    }
}
