package io.github.susongyan.bobachop.bench;

/**
 * 基准 SQL 形态（确定性内容，无随机源——跨运行可比）。
 *
 * <p>形态族对齐 DESIGN §12 复现方法：简单查询稳态、IN(1000)、批量 INSERT 线性度，
 * 另含 JOIN/派生表+子查询复合形态与 UPDATE；首走场景用"同族新形态"检验无惰性 DFA 的
 * 冷热一致性假设（D1）。</p>
 */
final class Shapes {

    static final String SIMPLE_SELECT = "SELECT id, user_id, amount FROM t_order WHERE id = 42";

    static final String JOIN_SELECT =
            "SELECT o.id, u.name FROM t_order o JOIN t_user u ON o.user_id = u.id "
                    + "WHERE o.status = 'PAID' ORDER BY o.id DESC LIMIT 10";

    /** 首走形态：与 SIMPLE 同为查询但覆盖派生表/GROUP BY/HAVING/IN 子查询新路径。 */
    static final String COMPOSITE_SELECT =
            "SELECT d.uid FROM (SELECT user_id AS uid, COUNT(*) AS c FROM t_order GROUP BY user_id HAVING c > 3) d "
                    + "WHERE d.uid IN (SELECT id FROM t_vip)";

    static final String UPDATE_WHERE = "UPDATE t_order SET status = 'DONE', retry = retry + 1 WHERE id = 42";

    /** IN 清单形态：{@code SELECT id FROM t_order WHERE status IN ('s0', 's1', ...)}。 */
    static String inListSelect(final int n) {
        final StringBuilder sql = new StringBuilder(16 * n + 64);
        sql.append("SELECT id FROM t_order WHERE status IN (");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("'s").append(i).append("'");
        }
        sql.append(")");
        return sql.toString();
    }

    /** 批量 INSERT：{@code INSERT INTO t_bench (id, a, b, c) VALUES (...), (...)} 共 rows 行。 */
    static String batchInsert(final int rows) {
        final StringBuilder sql = new StringBuilder(64 * rows + 64);
        sql.append("INSERT INTO t_bench (id, a, b, c) VALUES ");
        for (int i = 0; i < rows; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('(').append(i).append(", 'a").append(i).append("', ").append(i * 1.5)
                    .append(", 'remark-").append(i).append("')");
        }
        return sql.toString();
    }

    private Shapes() {
    }
}
