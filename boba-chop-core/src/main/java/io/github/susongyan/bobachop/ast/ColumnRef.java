package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 列引用。
 *
 * <p>限定名保持源形态（如 {@code t.id}、{@code db.t.id}），整段存入 {@link #qualifier()}，
 * 不拆分、不解析归属（表名还是别名由绑定层裁决）。名称保大小写原文。</p>
 */
public final class ColumnRef extends AbstractNode implements Expr {

    private final String qualifier;
    private final String name;

    public ColumnRef(final int start, final int stop, final String qualifier, final String name) {
        super(start, stop);
        this.qualifier = qualifier;
        this.name = Objects.requireNonNull(name, "name");
    }

    /** 限定前缀（表名/别名/db.table 形态的原文），可空。 */
    public String qualifier() {
        return qualifier;
    }

    /** 列名（或 Oracle 伪列，如 {@code ROWNUM}——伪列按语法事实存为列引用，不做归一化）。 */
    public String name() {
        return name;
    }
}
