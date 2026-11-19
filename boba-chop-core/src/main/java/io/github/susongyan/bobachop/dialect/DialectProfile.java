package io.github.susongyan.bobachop.dialect;

import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.LexerConfig;

/**
 * 方言 Profile（D8 / DESIGN §7.4）：方言 = 词法配置 + 保留字裁决 + 分页形态。
 * DML 骨架单内核共享，差异全部收敛为本类数据与少量语法锚点开关；AST 存语法事实，
 * 不做跨方言语义归一化。
 *
 * <p><b>依赖方向</b>：dialect → lexer（装配 {@link LexerConfig}）；parser → dialect
 * （保留字裁决与分页开关）。本包不依赖 parser，lexer 不依赖本包——无环。</p>
 *
 * <p><b>分页形态开关</b>与 {@code ast.Limit} 语义槽位对应：MySQL 三形态
 * （{@code LIMIT n} / {@code LIMIT o, n} / {@code LIMIT n OFFSET o}）、PG
 * {@code LIMIT n [OFFSET m]} 与 SQL 标准 {@code OFFSET m FETCH FIRST n ROWS ONLY}；
 * Oracle 12c+ 采用标准 {@code OFFSET m ROWS FETCH FIRST n ROWS ONLY}（复用同一开关），
 * {@code ROWNUM} 分页为白名单外（表达式位前置归类）。</p>
 */
public abstract class DialectProfile {

    /** MySQL 8.0 profile（单例，无状态）。 */
    public static final DialectProfile MYSQL = new MySqlDialect();

    /** PostgreSQL profile（单例，无状态；关键字清单以 PG 附录 C 为准，对拍校准）。 */
    public static final DialectProfile POSTGRESQL = new PostgreSqlDialect();

    /** Oracle profile（单例，无状态；关键字清单以 Oracle 保留字表为准，对拍校准）。 */
    public static final DialectProfile ORACLE = new OracleDialect();

    /** 方言名（诊断与报告）。 */
    public abstract String name();

    /** 词法配置（关键字表 + 词法旋钮）。 */
    public abstract LexerConfig lexerConfig();

    /** 是否该方言保留字（语法层标识符位准入裁决；委托关键字表）。 */
    public boolean reserved(final Kw kw) {
        return lexerConfig().keywords().reserved(kw);
    }

    /** 是否支持 {@code LIMIT o, n} 逗号形态（MySQL 专有；PG 为语法错误）。 */
    public abstract boolean limitCommaOffset();

    /** 是否支持 SQL 标准分页族 {@code OFFSET m FETCH FIRST n ROWS ONLY}（含 OFFSET 的 ROW/ROWS 后缀；PG、Oracle 12c+）。 */
    public abstract boolean fetchFirstPagination();

    /**
     * 表因子（含派生表）别名是否允许 {@code AS} 引导（MySQL/PG 允许；Oracle 拒绝
     * {@code FROM t AS a}，列别名两方言皆允许 {@code AS}——列位恒开）。
     */
    public abstract boolean tableAliasAs();
}
