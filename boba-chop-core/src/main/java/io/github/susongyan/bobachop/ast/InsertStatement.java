package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * INSERT 语句：{@code INSERT INTO t (cols) VALUES/SELECT ... [ON DUPLICATE KEY UPDATE ...]}。
 *
 * <p>MySQL 专有子句 ON DUPLICATE KEY UPDATE 在本节点（方言锚点产物）；
 * PostgreSQL 的 ON CONFLICT / RETURNING 等锚点子句将随 M3 追加字段（只增不改）。</p>
 */
public final class InsertStatement extends AbstractNode implements Statement {

    private final TableName table;
    private final List<String> columns;
    private final InsertSource source;
    private final List<Assignment> onDuplicateKey;

    public InsertStatement(final int start, final int stop, final TableName table, final List<String> columns,
                           final InsertSource source, final List<Assignment> onDuplicateKey) {
        super(start, stop);
        this.table = Objects.requireNonNull(table, "table");
        this.columns = null == columns ? null : Collections.unmodifiableList(new ArrayList<String>(columns));
        this.source = Objects.requireNonNull(source, "source");
        this.onDuplicateKey = null == onDuplicateKey ? null : Collections.unmodifiableList(new ArrayList<Assignment>(onDuplicateKey));
    }

    public TableName table() {
        return table;
    }

    /** 目标列名清单（源文本），未书写为 null。 */
    public List<String> columns() {
        return columns;
    }

    public InsertSource source() {
        return source;
    }

    /** ON DUPLICATE KEY UPDATE 赋值清单（MySQL），未出现为 null。 */
    public List<Assignment> onDuplicateKey() {
        return onDuplicateKey;
    }
}
