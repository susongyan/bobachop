package io.github.susongyan.bobachop.parser;

import io.github.susongyan.bobachop.ast.SelectQuery;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenBuffer;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * 语句级语法器：装配共享的 {@link TokenBuffer}/{@link ExprParser} 与方言 profile，
 * 按首 token 分派 SELECT/INSERT/UPDATE/DELETE（DESIGN §7.3），并统一执行尾分号与
 * EOF 检查（v1 单语句）。
 *
 * <p>子查询互递归（表达式→查询体→表达式）经 {@link ExprParser.SubqueryBridge} 注入解耦：
 * bridge 目标为 {@link SelectParser#parseQueryBody}，insert-select 复用同一目标。</p>
 *
 * <p><b>方言</b>（T10/D8）：{@code Parser(sql)} 默认 MySQL；profile 贯穿全部子语法器
 * （保留字裁决、分页形态）。</p>
 */
public final class Parser {

    private final TokenBuffer buffer;
    private final ExprParser exprs;
    private final SelectParser selects;
    private final InsertParser inserts;
    private final UpdateParser updates;
    private final DeleteParser deletes;

    public Parser(final String sql) {
        this(sql, DialectProfile.MYSQL);
    }

    public Parser(final String sql, final DialectProfile profile) {
        this.buffer = new TokenBuffer(sql, profile.lexerConfig());
        // bridge 在构造期只被捕获不被调用，字段延迟可达成立
        exprs = new ExprParser(buffer, new ExprParser.SubqueryBridge() {
            @Override
            public SelectQuery parseSelectQuery() {
                return selects.parseQueryBody();
            }
        }, profile);
        selects = new SelectParser(buffer, exprs, profile);
        inserts = new InsertParser(buffer, exprs, selects, profile);
        updates = new UpdateParser(buffer, exprs, profile);
        deletes = new DeleteParser(buffer, exprs, profile);
    }

    /** 顶层入口：DML 分派 + 可选尾分号 + 强制 EOF（多语句/尾部残 token 为语法错误）。 */
    public Statement parse() {
        final Token first = buffer.peek(0);
        final Statement statement;
        if (first.isKw(Kw.SELECT)) {
            statement = selects.parseStatement();
        } else if (first.isKw(Kw.INSERT)) {
            statement = inserts.parseStatement();
        } else if (first.isKw(Kw.UPDATE)) {
            statement = updates.parseStatement();
        } else if (first.isKw(Kw.DELETE)) {
            statement = deletes.parseStatement();
        } else if (first.isKw(Kw.WITH)) {
            throw new UnsupportedSyntaxException("白名单外: CTE（WITH 子句）@" + first.start());
        } else if (first.isKw(Kw.MERGE)) {
            // MERGE 仅 Oracle 关键字（MySQL/PG 恒为标识符走"期望 DML"路径），合法 Oracle 语法前置归类
            throw new UnsupportedSyntaxException("白名单外: MERGE 语句（Oracle upsert）@" + first.start());
        } else {
            throw new ParseException("期望 DML 语句但为 " + first, first.start());
        }
        // PG：DML 尾部 RETURNING 为合法语法但契约无锚点，前置归类白名单外（MySQL 无此词，恒不触发）
        if (buffer.peek(0).isKw(Kw.RETURNING)) {
            throw new UnsupportedSyntaxException("白名单外: RETURNING 子句（契约待演进）@" + buffer.peek(0).start());
        }
        if (TokenType.SEMICOLON == buffer.peek(0).type()) {
            buffer.next();
        }
        if (!buffer.eof()) {
            throw new ParseException("语句后多余 token: " + buffer.peek(0), buffer.peek(0).start());
        }
        return statement;
    }
}
