package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * INSERT 语句：{@code INSERT INTO t (cols) VALUES/SELECT ... [ON DUPLICATE KEY UPDATE ...]
 * [RETURNING 清单]}。
 *
 * <p>方言锚点子句（追加可空字段，只增不改）：MySQL 专有 ON DUPLICATE KEY UPDATE、
 * PG 专有 RETURNING（返回列清单，SELECT 投影项同形）。PG 的 ON CONFLICT 仍白名单外，
 * 待契约演进。</p>
 */
public final class InsertStatement extends AbstractNode implements Statement {

    private final TableName table;
    private final List<String> columns;
    private final InsertSource source;
    private final List<Assignment> onDuplicateKey;
    private final List<SelectItem> returning;

    public InsertStatement(final int start, final int stop, final TableName table, final List<String> columns,
                           final InsertSource source, final List<Assignment> onDuplicateKey) {
        this(start, stop, table, columns, source, onDuplicateKey, null);
    }

    public InsertStatement(final int start, final int stop, final TableName table, final List<String> columns,
                           final InsertSource source, final List<Assignment> onDuplicateKey,
                           final List<SelectItem> returning) {
        super(start, stop);
        this.table = Objects.requireNonNull(table, "table");
        this.columns = null == columns ? null : Collections.unmodifiableList(new ArrayList<String>(columns));
        this.source = Objects.requireNonNull(source, "source");
        this.onDuplicateKey = null == onDuplicateKey ? null : Collections.unmodifiableList(new ArrayList<Assignment>(onDuplicateKey));
        this.returning = null == returning ? null : Collections.unmodifiableList(new ArrayList<SelectItem>(returning));
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

    /** RETURNING 返回列清单（PG 方言锚点），未出现为 null。 */
    public List<SelectItem> returning() {
        return returning;
    }
}
