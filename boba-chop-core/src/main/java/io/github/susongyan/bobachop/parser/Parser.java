package io.github.susongyan.bobachop.parser;

import io.github.susongyan.bobachop.ast.SelectQuery;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenBuffer;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * 语句级语法器：装配共享的 {@link TokenBuffer}/{@link ExprParser}，按首 token 分派
 * SELECT/INSERT/UPDATE/DELETE（DESIGN §7.3），并统一执行尾分号与 EOF 检查（v1 单语句）。
 *
 * <p>子查询互递归（表达式→查询体→表达式）经 {@link ExprParser.SubqueryBridge} 注入解耦：
 * bridge 目标为 {@link SelectParser#parseQueryBody}，insert-select 复用同一目标。</p>
 */
public final class Parser {

    private final TokenBuffer buffer;
    private final ExprParser exprs;
    private final SelectParser selects;
    private final InsertParser inserts;
    private final UpdateParser updates;
    private final DeleteParser deletes;

    public Parser(final String sql) {
        buffer = new TokenBuffer(sql);
        // bridge 在构造期只被捕获不被调用，字段延迟可达成立
        exprs = new ExprParser(buffer, new ExprParser.SubqueryBridge() {
            @Override
            public SelectQuery parseSelectQuery() {
                return selects.parseQueryBody();
            }
        });
        selects = new SelectParser(buffer, exprs);
        inserts = new InsertParser(buffer, exprs, selects);
        updates = new UpdateParser(buffer, exprs);
        deletes = new DeleteParser(buffer, exprs);
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
        } else {
            throw new ParseException("期望 DML 语句但为 " + first, first.start());
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
