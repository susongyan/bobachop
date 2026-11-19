package io.github.susongyan.bobachop.dialect;

import java.util.EnumSet;
import java.util.Set;

import io.github.susongyan.bobachop.lexer.KeywordTable;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.LexerConfig;

/**
 * PostgreSQL 方言（T10 / DESIGN §7.4）：标准 conforming 字符串（无反斜杠转义，
 * {@code E''} 前缀恒转义）、ANSI 双引号包裹标识符、无反引号、无 {@code #} 注释、
 * {@code --} 恒为行注释、块注释嵌套、引导前缀 {@code E''/B''/X''}、dollar-quoted
 * 以非法字符显式拒绝（词法层）。
 *
 * <p><b>关键字收录原则</b>（与 T4 同一不对称方向）：① 本内核语法键控的词必收（否则
 * 语法器失锚）；② PG 保留字必收（否则 {@code FROM offset} 类非法 SQL 被错误接受）；
 * ③ 其余 PG 非保留字不收 = 退化为标识符，无害（PG 本就允许其作标识符）。清单按
 * PG 附录 C（关键字分类）整理，遗误由 ANTLR4 PG 参考对拍校准。MySQL 专有语法词
 * （{@code STRAIGHT_JOIN}/{@code XOR}/{@code RLIKE}/{@code REGEXP}/{@code DIV}/
 * {@code MOD}/{@code DUPLICATE}）不收录——在 PG 中为合法标识符。</p>
 */
final class PostgreSqlDialect extends DialectProfile {

    /** PG 保留字（附录 C reserved 列；不可出现在未包裹标识符位）。 */
    private static final Set<Kw> PG_RESERVED = EnumSet.of(
            Kw.ALL, Kw.ANALYSE, Kw.ANALYZE, Kw.AND, Kw.ANY, Kw.ARRAY, Kw.AS, Kw.ASC, Kw.ASYMMETRIC,
            Kw.BOTH, Kw.CASE, Kw.CAST, Kw.CHECK, Kw.COLLATE, Kw.COLUMN, Kw.CONSTRAINT, Kw.CREATE,
            Kw.CURRENT_CATALOG, Kw.CURRENT_DATE, Kw.CURRENT_ROLE, Kw.CURRENT_TIME, Kw.CURRENT_TIMESTAMP,
            Kw.CURRENT_USER, Kw.DEFAULT, Kw.DEFERRABLE, Kw.DESC, Kw.DISTINCT, Kw.DO, Kw.ELSE, Kw.END,
            Kw.EXCEPT, Kw.FALSE, Kw.FETCH, Kw.FOR, Kw.FOREIGN, Kw.FROM, Kw.FULL, Kw.GRANT, Kw.GROUP,
            Kw.HAVING, Kw.IN, Kw.INITIALLY, Kw.INTERSECT, Kw.INTO, Kw.IS, Kw.LATERAL, Kw.LEADING,
            Kw.LIMIT, Kw.LOCALTIME, Kw.LOCALTIMESTAMP, Kw.NOT, Kw.NULL, Kw.OFFSET, Kw.ON, Kw.ONLY,
            Kw.OR, Kw.ORDER, Kw.PLACING, Kw.PRIMARY, Kw.REFERENCES, Kw.RETURNING, Kw.SELECT,
            Kw.SESSION_USER, Kw.SOME, Kw.SYMMETRIC, Kw.TABLE, Kw.THEN, Kw.TO, Kw.TRAILING, Kw.TRUE,
            Kw.UNION, Kw.UNIQUE, Kw.USER, Kw.USING, Kw.VARIADIC, Kw.WHEN, Kw.WHERE, Kw.WINDOW, Kw.WITH);

    /** 语法键控的非保留字（本内核语法锚点；在 PG 中可作标识符，语法器按位置裁决）。 */
    private static final Set<Kw> PG_NON_RESERVED = EnumSet.of(
            Kw.BETWEEN, Kw.BY, Kw.CONFLICT, Kw.CROSS, Kw.DATE, Kw.DELETE, Kw.ESCAPE, Kw.EXISTS,
            Kw.FIRST, Kw.INNER, Kw.INSERT, Kw.JOIN, Kw.LEFT, Kw.LIKE, Kw.LOCK, Kw.NATURAL, Kw.NEXT,
            Kw.OUTER, Kw.PROCEDURE, Kw.RIGHT, Kw.ROW, Kw.ROWS, Kw.SET, Kw.TIME, Kw.TIMESTAMP,
            Kw.UPDATE, Kw.VALUES);

    /** PG 关键字 = 保留字 ∪ 语法键控非保留字。 */
    private static final KeywordTable PG_TABLE = new KeywordTable(union(), PG_RESERVED);

    private static Set<Kw> union() {
        final Set<Kw> all = EnumSet.copyOf(PG_RESERVED);
        all.addAll(PG_NON_RESERVED);
        return all;
    }

    @Override
    public String name() {
        return "PostgreSQL";
    }

    @Override
    public LexerConfig lexerConfig() {
        return LEXER_CONFIG;
    }

    private static final LexerConfig LEXER_CONFIG = LexerConfig.builder()
            .keywords(PG_TABLE)
            .backslashEscapes(false)
            .ansiQuotes(true)
            .backtickQuotes(false)
            .hashLineComments(false)
            .lineCommentRequiresWhitespace(false)
            .nestedBlockComments(true)
            .stringPrefixes("EBX")
            .escapedStringPrefixes("E")
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
        return true;
    }
}
