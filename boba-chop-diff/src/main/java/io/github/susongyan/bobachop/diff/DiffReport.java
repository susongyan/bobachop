package io.github.susongyan.bobachop.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单条 SQL 的对拍结果：差异清单为空即"一致"。
 *
 * <p>"一致"含两种情形：双方成功且树全等（含全部位置偏移），或双方一致拒绝
 * （接受度对齐，同拒同收）；单侧失败记为结构性差异。</p>
 */
public final class DiffReport {

    private final String sql;
    private final List<DiffDifference> differences;

    public DiffReport(final String sql, final List<DiffDifference> differences) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.differences = Collections.unmodifiableList(new ArrayList<DiffDifference>(Objects.requireNonNull(differences, "differences")));
    }

    public String sql() {
        return sql;
    }

    /** 差异清单（无差异即一致）。 */
    public List<DiffDifference> differences() {
        return differences;
    }

    public boolean identical() {
        return differences.isEmpty();
    }

    @Override
    public String toString() {
        return sql + ": " + (identical() ? "一致" : differences.size() + " 处差异");
    }
}
