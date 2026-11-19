package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 具名表引用：{@code [qualifier.]name [AS alias]}。名称保源大小写；归属解析（逻辑表/物理表）是绑定层职责。
 */
public final class TableName extends AbstractNode implements TableRef {

    private final String qualifier;
    private final String name;
    private final String alias;

    public TableName(final int start, final int stop, final String qualifier, final String name, final String alias) {
        super(start, stop);
        this.qualifier = qualifier;
        this.name = Objects.requireNonNull(name, "name");
        this.alias = alias;
    }

    /** 库限定前缀（如 {@code db}），可空。 */
    public String qualifier() {
        return qualifier;
    }

    /** 表名（源大小写）。 */
    public String name() {
        return name;
    }

    /** 别名（源文本，不含 AS），可空。 */
    public String alias() {
        return alias;
    }
}
