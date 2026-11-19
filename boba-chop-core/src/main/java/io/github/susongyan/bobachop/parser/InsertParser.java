package io.github.susongyan.bobachop.parser;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.ast.Assignment;
import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.Expr;
import io.github.susongyan.bobachop.ast.FunctionCall;
import io.github.susongyan.bobachop.ast.InsertSource;
import io.github.susongyan.bobachop.ast.InsertStatement;
import io.github.susongyan.bobachop.ast.SelectSource;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.ast.TableName;
import io.github.susongyan.bobachop.ast.ValuesSource;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.exception.ParseException;
import io.github.susongyan.bobachop.exception.UnsupportedSyntaxException;
import io.github.susongyan.bobachop.lexer.Kw;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenBuffer;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * INSERT 语句语法器（v1 MySQL 白名单）：{@code INSERT [INTO] t [(cols)] VALUES rows | SELECT ...
 * [ON DUPLICATE KEY UPDATE 赋值清单]}。
 *
 * <p><b>列清单 vs 括号化数据源的消歧</b>（DESIGN §7.3 无界歧义点）：表名后 LPAREN 时
 * {@code peek(1)==SELECT} 即判（SELECT 保留字，不可能为列名）——括号化查询数据源 v1 白名单外；
 * 其余按列清单（名字+逗号）贪心解析后看后继 token 分派 VALUES/SELECT，无需扫描 helper。</p>
 *
 * <p><b>v1 白名单外</b>：INSERT ... SET 形态、VALUE 同义形态、VALUES ROW() 构造、
 * 括号化查询数据源、多级限定表名（db.t 为上限）、INSERT ... ON DUPLICATE KEY UPDATE 之外的
 * MySQL 修饰符（LOW_PRIORITY/DELAYED/HIGH_PRIORITY/IGNORE/REPLACE）。</p>
 */
public final class InsertParser {

    private final TokenBuffer buffer;
    private final ExprParser exprs;
    private final SelectParser selects;
    private final DialectProfile profile;

    InsertParser(final TokenBuffer buffer, final ExprParser exprs, final SelectParser selects,
                 final DialectProfile profile) {
        this.buffer = buffer;
        this.exprs = exprs;
        this.selects = selects;
        this.profile = profile;
    }

    Statement parseStatement() {
        final Token insert = expectKw(Kw.INSERT, "INSERT");
        if (buffer.peek(0).isKw(Kw.INTO)) {
            buffer.next();
        }
        // Oracle 多表 INSERT（ALL/FIRST）：合法 Oracle 语法但契约为单表，前置归类
        //（ALL/FIRST 在 MySQL 语法下 INSERT 后无合法形态，误触发仅影响异常类型不影响拒绝事实）
        if (buffer.peek(0).isKw(Kw.ALL) || buffer.peek(0).isKw(Kw.FIRST)) {
            throw unsupported("多表 INSERT（ALL/FIRST，契约为单表）", buffer.peek(0));
        }
        final TableName table = parseTargetTable();
        List<String> columns = null;
        if (TokenType.LPAREN == buffer.peek(0).type()) {
            if (buffer.peek(1).isKw(Kw.SELECT)) {
                throw unsupported("括号化查询数据源（INSERT t (SELECT ...)）", buffer.peek(0));
            }
            columns = parseColumnList();
        }
        final InsertSource source = parseSource();
        if (null != columns && source instanceof ValuesSource) {
            final List<List<Expr>> rows = ((ValuesSource) source).rows();
            for (final List<Expr> row : rows) {
                if (columns.size() != row.size()) {
                    throw new ParseException("列数 " + columns.size() + " 与值数 " + row.size() + " 不匹配", source.start());
                }
            }
        }
        List<Assignment> onDuplicateKey = null;
        if (buffer.peek(0).isKw(Kw.ON)) {
            // PG 的 ON CONFLICT 为合法语法但 v1 白名单外，前置归类（CONFLICT 仅 PG 关键字，MySQL 恒不触发）
            if (buffer.peek(1).isKw(Kw.CONFLICT)) {
                throw unsupported("ON CONFLICT upsert 形态", buffer.peek(0));
            }
            buffer.next();
            expectKw(Kw.DUPLICATE, "ON DUPLICATE 的 DUPLICATE");
            expectKw(Kw.KEY, "ON DUPLICATE KEY 的 KEY");
            expectKw(Kw.UPDATE, "ON DUPLICATE KEY 的 UPDATE");
            onDuplicateKey = new ArrayList<Assignment>();
            while (true) {
                onDuplicateKey.add(parseOnDuplicateAssignment());
                if (TokenType.COMMA == buffer.peek(0).type()) {
                    buffer.next();
                    continue;
                }
                break;
            }
        }
        return new InsertStatement(insert.start(), lastStop(), table, columns, source, onDuplicateKey);
    }

    // ---------- 数据源 ----------

    private InsertSource parseSource() {
        final Token token = buffer.peek(0);
        if (token.isKw(Kw.VALUES)) {
            return parseValuesSource();
        }
        if (token.isKw(Kw.SELECT)) {
            return new SelectSource(token.start(), lastStop(), selects.parseQueryBody());
        }
        if (TokenType.IDENTIFIER == token.type() && "VALUE".equalsIgnoreCase(token.text())) {
            throw unsupported("VALUE 同义形态", token);
        }
        if (token.isKw(Kw.SET)) {
            throw unsupported("INSERT ... SET 形态", token);
        }
        throw new ParseException("期望 VALUES 或 SELECT 但为 " + token, token.start());
    }

    private ValuesSource parseValuesSource() {
        final Token values = expectKw(Kw.VALUES, "VALUES");
        final List<List<Expr>> rows = new ArrayList<List<Expr>>();
        while (true) {
            expectType(TokenType.LPAREN, "VALUES 行的 (");
            final List<Expr> row = new ArrayList<Expr>();
            if (TokenType.RPAREN != buffer.peek(0).type()) {
                while (true) {
                    row.add(exprs.parseExpr());
                    if (TokenType.COMMA == buffer.peek(0).type()) {
                        buffer.next();
                        continue;
                    }
                    break;
                }
            }
            if (row.isEmpty()) {
                throw new ParseException("VALUES 行至少一个表达式（空行形态 v1 不收）", lastToken().start());
            }
            if (!rows.isEmpty() && rows.get(0).size() != row.size()) {
                throw new ParseException("VALUES 各行值数不一致: " + row.size() + " vs " + rows.get(0).size(),
                        row.get(0).start());
            }
            expectType(TokenType.RPAREN, "VALUES 行的 )");
            rows.add(row);
            if (TokenType.COMMA == buffer.peek(0).type()) {
                buffer.next();
                continue;
            }
            break;
        }
        return new ValuesSource(values.start(), lastStop(), rows);
    }

    // ---------- 赋值（ON DUPLICATE KEY UPDATE） ----------

    private Assignment parseOnDuplicateAssignment() {
        final ColumnRef column = parseColumnRef();
        expectAssign();
        final Token token = buffer.peek(0);
        final Expr value;
        if (token.isKw(Kw.VALUES) && TokenType.LPAREN == buffer.peek(1).type()) {
            // MySQL 专有 VALUES(col)：ODKU 语境内白名单收窄接受，手工建 FunctionCall
            buffer.next();
            buffer.next();
            final ColumnRef inner = parseColumnRef();
            expectType(TokenType.RPAREN, "VALUES() 的 )");
            final List<Expr> args = new ArrayList<Expr>();
            args.add(inner);
            value = new FunctionCall(token.start(), lastStop(), token.text(), args, false);
        } else {
            value = exprs.parseExpr();
        }
        return new Assignment(column.start(), value.stop(), column, value);
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
        return new TableName(first.start(), name.stop(), qualifier, name.text(), null);
    }

    private List<String> parseColumnList() {
        expectType(TokenType.LPAREN, "列清单的 (");
        final List<String> columns = new ArrayList<String>();
        while (true) {
            columns.add(expectName("列名").text());
            if (TokenType.COMMA == buffer.peek(0).type()) {
                buffer.next();
                continue;
            }
            break;
        }
        expectType(TokenType.RPAREN, "列清单的 )");
        return columns;
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
                || TokenType.KEYWORD == token.type() && !profile.reserved(token.kw());
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

    private Token expectType(final TokenType type, final String what) {
        final Token token = buffer.peek(0);
        if (token.type() != type) {
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
