package io.github.susongyan.bobachop.lexer;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 关键字词汇表（枚举名 = 关键字大写原文，跨方言并集；MySQL 8.0 主段 + 方言追加段）。
 *
 * <p><b>主段</b>收录范围 = <b>MySQL 8.0 保留字全集</b> + 语法键控的少量非保留字
 * （{@link #OFFSET}、{@link #DUPLICATE}、{@link #ESCAPE}）。
 * 完备性不对称：漏收保留字 = 把非法 SQL 错误接受（如 {@code FROM order}），漏收非保留字 = 退化为
 * 标识符，无害。清单按 MySQL 8.0 参考手册整理；遗误由 ANTLR4 参考对拍校准（对拍是清单的验收器）。</p>
 *
 * <p><b>方言追加段</b>（T10 PostgreSQL）：仅 PostgreSQL 需要词法身份的词，MySQL 关键字表不含
 * 此段（在 MySQL 中为普通标识符，语义正确——如 {@code ilike}、{@code conflict} 在 MySQL 可作列名）。
 * 哪些词属于哪个方言、是否保留，由 {@code dialect} 包的 DialectProfile 装配进
 * {@link KeywordTable}（D8：方言差异是数据不是代码分支）。</p>
 */
public enum Kw {

    ADD, ALL, ALTER, ANALYZE, AND, AS, ASC, ASENSITIVE,
    BEFORE, BETWEEN, BIGINT, BINARY, BLOB, BOTH, BY,
    CALL, CASCADE, CASE, CHANGE, CHAR, CHARACTER, CHECK, COLLATE, COLUMN, CONDITION, CONSTRAINT,
    CONTINUE, CONVERT, CREATE, CROSS, CUBE, CUME_DIST, CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP,
    CURRENT_USER, CURSOR,
    DATABASE, DATABASES, DAY_HOUR, DAY_MICROSECOND, DAY_MINUTE, DAY_SECOND, DEC, DECIMAL, DECLARE,
    DEFAULT, DELAYED, DELETE, DENSE_RANK, DESC, DESCRIBE, DETERMINISTIC, DISTINCT, DISTINCTROW, DIV,
    DOUBLE, DROP, DUAL,
    EACH, ELSE, ELSEIF, EMPTY, END, ENCLOSED, ESCAPED, EXCEPT, EXISTS, EXIT, EXPLAIN,
    FALSE, FETCH, FIRST_VALUE, FLOAT, FOR, FORCE, FOREIGN, FROM, FULLTEXT,
    GENERATED, GET, GRANT, GROUP, GROUPING, GROUPS,
    HAVING, HIGH_PRIORITY, HOUR_MICROSECOND, HOUR_MINUTE, HOUR_SECOND,
    IF, IGNORE, IN, INDEX, INFILE, INNER, INOUT, INSENSITIVE, INSERT, INT, INTEGER, INTERVAL, INTO,
    IO_AFTER_GTIDS, IO_BEFORE_GTIDS, IS, ITERATE,
    JOIN, JSON_TABLE,
    KEY, KEYS, KILL,
    LAG, LAST_VALUE, LATERAL, LEAD, LEADING, LEAVE, LEFT, LIKE, LIMIT, LINEAR, LINES, LOAD,
    LOCALTIME, LOCALTIMESTAMP, LOCK, LONG, LONGBLOB, LONGTEXT, LOOP, LOW_PRIORITY,
    MASTER_BIND, MASTER_SSL_VERIFY_SERVER_CERT, MATCH, MAXVALUE, MEDIUMBLOB, MEDIUMINT, MEDIUMTEXT,
    MIDDLEINT, MINUTE_MICROSECOND, MINUTE_SECOND, MOD, MODIFIES,
    NATURAL, NOT, NO_WRITE_TO_BINLOG, NTH_VALUE, NTILE, NULL, NUMERIC,
    OF, ON, OPTIMIZE, OPTIMIZER_COSTS, OPTION, OPTIONALLY, OR, ORDER, OUT, OUTER, OUTFILE, OVER,
    PARTITION, PERCENT_RANK, PRECISION, PRIMARY, PROCEDURE, PS_THREAD, PURGE,
    RANGE, RANK, READ, READS, READ_WRITE, REAL, RECURSIVE, REFERENCES, REGEXP, RELEASE, RENAME,
    REPEAT, REPLACE, REQUIRE, RESIGNAL, RESTRICT, RETURN, REVOKE, RIGHT, RLIKE, ROW, ROWS, ROW_NUMBER,
    SCHEMA, SCHEMAS, SECOND_MICROSECOND, SELECT, SENSITIVE, SEPARATOR, SET, SHOW, SIGNAL, SMALLINT,
    SPATIAL, SPECIFIC, SQL, SQLEXCEPTION, SQLSTATE, SQLWARNING, SQL_BIG_RESULT, SQL_CALC_FOUND_ROWS,
    SQL_SMALL_RESULT, SSL, STARTING, STORED, STRAIGHT_JOIN, SYSTEM,
    TABLE, TERMINATED, THEN, TINYBLOB, TINYINT, TINYTEXT, TO, TRAILING, TRIGGER, TRUE,
    UNDO, UNION, UNIQUE, UNLOCK, UNSIGNED, UPDATE, USAGE, USE, USING, UTC_DATE, UTC_TIME, UTC_TIMESTAMP,
    VALUES, VARBINARY, VARCHAR, VARCHARACTER, VARYING, VIRTUAL,
    WHEN, WHERE, WHILE, WINDOW, WITH, WRITE,
    XOR, YEAR_MONTH, ZEROFILL,
    /** 非保留（语法键控：LIMIT ... OFFSET n）。 */
    OFFSET,
    /** 非保留（语法键控：ON DUPLICATE KEY UPDATE）。 */
    DUPLICATE,
    /** 非保留（语法键控：LIKE ... ESCAPE 'c'）。 */
    ESCAPE,
    /** 非保留（语法键控：DATE '...' 时间字面量）。 */
    DATE,
    /** 非保留（语法键控：TIME '...' 时间字面量）。 */
    TIME,
    /** 非保留（语法键控：TIMESTAMP '...' 时间字面量）。 */
    TIMESTAMP,
    // ---------- 方言追加段（T10 PostgreSQL；MySQL 关键字表不含本段） ----------
    /** PG 保留（EXCEPT 之外的集合运算；v1 白名单外，前置检查归类）。 */
    ANALYSE,
    /** PG 保留。 */
    ANY,
    /** PG 保留（数组构造；v1 白名单外）。 */
    ARRAY,
    /** PG 保留。 */
    ASYMMETRIC,
    /** PG 保留（MySQL 中 CAST 为非保留字，走标识符路径由受限函数名单拒绝，语义同样正确）。 */
    CAST,
    /** PG 非保留（语法键控：INSERT ... ON CONFLICT；v1 白名单外，前置检查归类）。 */
    CONFLICT,
    /** PG 保留。 */
    CURRENT_CATALOG,
    /** PG 保留。 */
    CURRENT_ROLE,
    /** PG 保留。 */
    DEFERRABLE,
    /** PG 保留（无实参语句体；v1 白名单外）。 */
    DO,
    /** PG 非保留（语法键控：FETCH FIRST n ROWS ONLY）。 */
    FIRST,
    /** PG 保留（FULL JOIN；v1 白名单外，前置检查归类）。 */
    FULL,
    /** PG 保留。 */
    INITIALLY,
    /** PG 保留（集合运算；v1 白名单外，前置检查归类）。 */
    INTERSECT,
    /** PG 非保留（语法键控：FETCH NEXT n ROWS ONLY）。 */
    NEXT,
    /** PG 保留（FETCH ... ONLY 的收尾词）。 */
    ONLY,
    /** PG 保留。 */
    PLACING,
    /** PG 保留（DML 尾部子句；v1 白名单外，前置检查归类）。 */
    RETURNING,
    /** PG 保留。 */
    SESSION_USER,
    /** PG 保留。 */
    SOME,
    /** PG 保留。 */
    SYMMETRIC,
    /** PG 保留。 */
    USER,
    /** PG 保留。 */
    VARIADIC;

    /** 方言追加段（PostgreSQL 专有；MySQL 关键字表 = 全集 − 本段）。 */
    private static final Set<Kw> DIALECT_ADDITIONS = Collections.unmodifiableSet(
            EnumSet.of(ANALYSE, ANY, ARRAY, ASYMMETRIC, CAST, CONFLICT, CURRENT_CATALOG, CURRENT_ROLE,
                    DEFERRABLE, DO, FIRST, FULL, INITIALLY, INTERSECT, NEXT, ONLY, PLACING, RETURNING,
                    SESSION_USER, SOME, SYMMETRIC, USER, VARIADIC));

    /** 方言追加段只读视图（关键字表装配用；Oracle 到来时在此追加并同步本方法）。 */
    public static Set<Kw> dialectAdditions() {
        return DIALECT_ADDITIONS;
    }
}
