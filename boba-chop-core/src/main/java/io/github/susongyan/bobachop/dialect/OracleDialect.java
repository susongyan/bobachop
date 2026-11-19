package io.github.susongyan.bobachop.dialect;

import java.util.EnumSet;
import java.util.Set;

import io.github.susongyan.bobachop.lexer.KeywordTable;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.LexerConfig;

/**
 * Oracle 方言（T11 / DESIGN §7.4）：字符串无反斜杠转义、ANSI 双引号包裹标识符、
 * 无反引号、无 {@code #} 注释、{@code --} 恒为行注释、块注释嵌套、引导前缀仅
 * {@code N''}（国家字符）、q-字符串以既有路径拒绝（q 识别为标识符后随字符串，
 * 表达式位落 ParseException）。
 *
 * <p><b>分页</b>：Oracle 12c+ 的 {@code OFFSET m ROWS FETCH FIRST n ROWS ONLY}
 * 复用 PG 的标准分页族路径（{@link #fetchFirstPagination()}）；{@code ROWNUM}
 * 伪列分页白名单外（表达式位前置归类 Unsupported）；{@code LIMIT} 非本方言词
 * （不进关键字表，出现即语法错误）。{@code OFFSET} 后缀省略（{@code OFFSET 10
 * FETCH ...}）为本内核接受的 PG 超集形态，对拍校准点。</p>
 *
 * <p><b>表别名</b>：Oracle 拒绝表因子 {@code AS} 引导（{@link #tableAliasAs()}
 * = false），列别名 {@code AS} 各方言皆允许。</p>
 *
 * <p><b>关键字收录原则</b>（与 T4/T10 同一不对称方向）：① 本内核语法键控的词必收；
 * ② Oracle 保留字必收（否则非法 SQL 被错误接受）；③ 其余 Oracle 非保留词不收 =
 * 退化为标识符，无害。清单按 Oracle 保留字表整理，遗误由 ANTLR4 Oracle 参考
 * 对拍校准。MySQL 专有词（{@code LIMIT}/{@code DUAL}/{@code STRAIGHT_JOIN}/
 * {@code XOR}/{@code RLIKE}/{@code REGEXP}/{@code DIV}/{@code DUPLICATE}）不收录
 * ——{@code FROM dual} 在本方言为普通标识符表名（DUAL 在 Oracle 非关键字）。</p>
 */
final class OracleDialect extends DialectProfile {

    /**
     * Oracle 保留字（经典保留字表 + 12c 分页 OFFSET/FETCH + 连接条件词 USING——与 PG 同一
     * 取舍：子句锚点词在表别名位必须让位给子句，放保留集消除别名歧义；不可出现在未包裹标识符位）。
     */
    private static final Set<Kw> ORACLE_RESERVED = EnumSet.of(
            Kw.ACCESS, Kw.ADD, Kw.ALL, Kw.ALTER, Kw.AND, Kw.ANY, Kw.AS, Kw.ASC, Kw.AUDIT,
            Kw.BETWEEN, Kw.BY, Kw.CHAR, Kw.CHECK, Kw.CLUSTER, Kw.COLUMN, Kw.COMMENT, Kw.COMPRESS,
            Kw.CONNECT, Kw.CREATE, Kw.CURRENT, Kw.DATE, Kw.DECIMAL, Kw.DEFAULT, Kw.DELETE, Kw.DESC,
            Kw.DISTINCT, Kw.DROP, Kw.ELSE, Kw.EXCLUSIVE, Kw.EXISTS, Kw.FETCH, Kw.FILE, Kw.FLOAT,
            Kw.FOR,
            Kw.FROM, Kw.GRANT, Kw.GROUP, Kw.HAVING, Kw.IDENTIFIED, Kw.IMMEDIATE, Kw.IN,
            Kw.INCREMENT, Kw.INDEX, Kw.INITIAL, Kw.INSERT, Kw.INTEGER, Kw.INTERSECT, Kw.INTO,
            Kw.IS, Kw.LEVEL, Kw.LIKE, Kw.LOCK, Kw.LONG, Kw.MAXEXTENTS, Kw.MINUS, Kw.MLSLABEL,
            Kw.MODE, Kw.MODIFY, Kw.NOAUDIT, Kw.NOCOMPRESS, Kw.NOT, Kw.NOWAIT, Kw.NULL, Kw.NUMBER,
            Kw.OF, Kw.OFFLINE, Kw.OFFSET, Kw.ON, Kw.ONLINE, Kw.OPTION, Kw.OR, Kw.ORDER,
            Kw.PCTFREE, Kw.PRIOR,
            Kw.PUBLIC, Kw.RAW, Kw.RENAME, Kw.RESOURCE, Kw.REVOKE, Kw.ROW, Kw.ROWID, Kw.ROWNUM,
            Kw.ROWS, Kw.SELECT, Kw.SESSION, Kw.SET, Kw.SHARE, Kw.SIZE, Kw.SMALLINT, Kw.START,
            Kw.SUCCESSFUL, Kw.SYNONYM, Kw.SYSDATE, Kw.TABLE, Kw.THEN, Kw.TO, Kw.TRIGGER, Kw.UID,
            Kw.UNION, Kw.UNIQUE, Kw.UPDATE, Kw.USER, Kw.USING, Kw.VALIDATE, Kw.VALUES,
            Kw.VARCHAR, Kw.VARCHAR2, Kw.VIEW, Kw.WHENEVER, Kw.WHERE, Kw.WITH);

    /** 语法键控的非保留字（本内核语法锚点；在 Oracle 中可作标识符，语法器按位置裁决）。 */
    private static final Set<Kw> ORACLE_NON_RESERVED = EnumSet.of(
            Kw.CASE, Kw.CROSS, Kw.END, Kw.ESCAPE, Kw.FALSE, Kw.FIRST, Kw.FULL, Kw.INNER,
            Kw.JOIN, Kw.LEFT, Kw.MERGE, Kw.NATURAL, Kw.NEXT, Kw.NULLS, Kw.ONLY,
            Kw.OUTER, Kw.RIGHT, Kw.TIMESTAMP, Kw.TRUE, Kw.WHEN);

    /** Oracle 关键字 = 保留字 ∪ 语法键控非保留字。 */
    private static final KeywordTable ORACLE_TABLE = new KeywordTable(union(), ORACLE_RESERVED);

    private static Set<Kw> union() {
        final Set<Kw> all = EnumSet.copyOf(ORACLE_RESERVED);
        all.addAll(ORACLE_NON_RESERVED);
        return all;
    }

    @Override
    public String name() {
        return "Oracle";
    }

    @Override
    public LexerConfig lexerConfig() {
        return LEXER_CONFIG;
    }

    private static final LexerConfig LEXER_CONFIG = LexerConfig.builder()
            .keywords(ORACLE_TABLE)
            .backslashEscapes(false)
            .ansiQuotes(true)
            .backtickQuotes(false)
            .hashLineComments(false)
            .lineCommentRequiresWhitespace(false)
            .nestedBlockComments(true)
            .stringPrefixes("N")
            .escapedStringPrefixes("")
            .build();

    @Override
    public boolean limitCommaOffset() {
        return false;
    }

    @Override
    public boolean fetchFirstPagination() {
        return true;
    }

    @Override
    public boolean tableAliasAs() {
        return false;
    }
}
