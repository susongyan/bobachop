package io.github.susongyan.bobachop.parser;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.ast.Assignment;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.ast.UpdateStatement;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenBuffer;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * UPDATE 语句语法器（v1 MySQL 白名单）：{@code UPDATE t [AS] alias SET col = expr, ... [WHERE ...]}。
 *
 * <p><b>v1 白名单外</b>：多表 UPDATE（{@code UPDATE t1, t2 / UPDATE t1 JOIN t2 SET}，
 * 契约 UpdateStatement 为单表）、尾部 ORDER BY/LIMIT（MySQL 允许、契约无锚点，追加可空字段演进）、
 * SET 赋值 := 形态、修饰符 LOW_PRIORITY/IGNORE。
 */
public final class UpdateParser {

    private final TokenBuffer buffer;
    private final ExprParser exprs;

    UpdateParser(final TokenBuffer buffer, final ExprParser exprs) {
        this.buffer = buffer;
        this.exprs = exprs;
    }

    Statement parseStatement() {
        final Token update = expectKw(Kw.UPDATE, "UPDATE");
        final TableName table = parseTargetTable();
        if (TokenType.COMMA == buffer.peek(0).type() || joinKeywordAhead()) {
            throw unsupported("多表 UPDATE（契约为单表）", buffer.peek(0));
        }
        expectKw(Kw.SET, "SET");
        final List<Assignment> assignments = new ArrayList<Assignment>();
        while (true) {
            assignments.add(parseAssignment());
            if (TokenType.COMMA == buffer.peek(0).type()) {
                buffer.next();
                continue;
            }
            break;
        }
        Expr where = null;
        if (buffer.peek(0).isKw(Kw.WHERE)) {
            buffer.next();
            where = exprs.parseExpr();
        }
        if (buffer.peek(0).isKw(Kw.ORDER) || buffer.peek(0).isKw(Kw.LIMIT)) {
            throw unsupported("UPDATE 尾部 ORDER BY/LIMIT（契约无锚点）", buffer.peek(0));
        }
        return new UpdateStatement(update.start(), lastStop(), table, assignments, where);
    }

    private Assignment parseAssignment() {
        final ColumnRef column = parseColumnRef();
        expectAssign();
        final Expr value = exprs.parseExpr();
        return new Assignment(column.start(), value.stop(), column, value);
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

    private ColumnRef parseColumnRef() {
        final Token first = expectName("列名");
        String qualifier = null;
        Token name = first;
        if (TokenType.DOT == buffer.peek(0).type() && isNameToken(buffer.peek(1))) {
            buffer.next();
            name = buffer.next();
            qualifier = first.text();
        }
        return new ColumnRef(first.start(), name.stop(), qualifier, name.text());
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

    private Token expectAssign() {
        final Token token = buffer.peek(0);
        if (TokenType.OPERATOR != token.type() || !"=".equals(token.text())) {
            throw new ParseException("期望赋值 = 但为 " + token, token.start());
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
