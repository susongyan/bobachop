package io.github.susongyan.bobachop.parser;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.ast.DeleteStatement;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenBuffer;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * DELETE 语句语法器（v1 MySQL 白名单）：{@code DELETE FROM t [AS] alias [WHERE ...]}。
 *
 * <p><b>v1 白名单外</b>：多表 DELETE（{@code DELETE t1, t2 FROM ... / DELETE FROM t1 USING ...}，
 * 契约 DeleteStatement 为单表）、尾部 ORDER BY/LIMIT（MySQL 允许、契约无锚点）、修饰符
 * LOW_PRIORITY/QUICK/IGNORE。</p>
 */
public final class DeleteParser {

    private final TokenBuffer buffer;
    private final ExprParser exprs;

    DeleteParser(final TokenBuffer buffer, final ExprParser exprs) {
        this.buffer = buffer;
        this.exprs = exprs;
    }

    Statement parseStatement() {
        final Token delete = expectKw(Kw.DELETE, "DELETE");
        if (!buffer.peek(0).isKw(Kw.FROM)) {
            // 多表形态首 token 即表名（DELETE t1, t2 FROM ...）
            throw unsupported("多表 DELETE（契约为单表）", buffer.peek(0));
        }
        buffer.next();
        final TableName table = parseTargetTable();
        if (TokenType.COMMA == buffer.peek(0).type() || buffer.peek(0).isKw(Kw.USING) || joinKeywordAhead()) {
            throw unsupported("多表 DELETE（契约为单表）", buffer.peek(0));
        }
        Expr where = null;
        if (buffer.peek(0).isKw(Kw.WHERE)) {
            buffer.next();
            where = exprs.parseExpr();
        }
        if (buffer.peek(0).isKw(Kw.ORDER) || buffer.peek(0).isKw(Kw.LIMIT)) {
            throw unsupported("DELETE 尾部 ORDER BY/LIMIT（契约无锚点）", buffer.peek(0));
        }
        return new DeleteStatement(delete.start(), lastStop(), table, where);
    }

    /** 只读判断：当前位置是否多表 JOIN 形态（不改游标，D2 只读前瞻）。 */
    private boolean joinKeywordAhead() {
        final Token token = buffer.peek(0);
        return token.isKw(Kw.JOIN) || token.isKw(Kw.INNER) || token.isKw(Kw.LEFT) || token.isKw(Kw.RIGHT)
                || token.isKw(Kw.CROSS) || token.isKw(Kw.STRAIGHT_JOIN);
    }

    // ---------- 名字与工具 ----------

    private TableName parseTargetTable() {
        final Token first = expectName("目标表名");
        final List<Token> parts = new ArrayList<Token>();
        parts.add(first);
        while (TokenType.DOT == buffer.peek(0).type() && isNameToken(buffer.peek(1))) {
            buffer.next();
            parts.add(buffer.next());
        }
        if (parts.size() > 2) {
            throw unsupported("多级限定表名（上限 db.t）", parts.get(2));
        }
        final Token name = parts.get(parts.size() - 1);
        final String qualifier = 2 == parts.size() ? parts.get(0).text() : null;
        Token alias = null;
        if (buffer.peek(0).isKw(Kw.AS)) {
            buffer.next();
            alias = expectName("AS 后的表别名");
        } else if (isNameToken(buffer.peek(0))) {
            alias = buffer.next();
        }
        return new TableName(first.start(), null != alias ? alias.stop() : name.stop(), qualifier, name.text(),
                null != alias ? alias.text() : null);
    }

    private boolean isNameToken(final Token token) {
        return TokenType.IDENTIFIER == token.type() || TokenType.QUOTED_IDENTIFIER == token.type()
                || TokenType.KEYWORD == token.type() && !token.kw().reserved();
    }

    private Token expectName(final String what) {
        final Token token = buffer.peek(0);
        if (!isNameToken(token)) {
            throw new ParseException("期望 " + what + " 但为 " + token, token.start());
        }
        return buffer.next();
    }

    private Token expectKw(final Kw kw, final String what) {
        final Token token = buffer.peek(0);
        if (!token.isKw(kw)) {
            throw new ParseException("期望 " + what + " 但为 " + token, token.start());
        }
        return buffer.next();
    }

    private Token lastToken() {
        return buffer.tokens().get(buffer.consumed() - 1);
    }

    private int lastStop() {
        return lastToken().stop();
    }

    private static UnsupportedSyntaxException unsupported(final String what, final Token at) {
        return new UnsupportedSyntaxException("白名单外: " + what + " @" + at.start());
    }
}
