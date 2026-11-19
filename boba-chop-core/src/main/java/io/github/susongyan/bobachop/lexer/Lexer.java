package io.github.susongyan.bobachop.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.susongyan.bobachop.exception.LexException;

/**
 * MySQL 词法器：显式字符类别分派 + maximal munch，无正则引擎（DESIGN §7.2）。
 *
 * <p><b>规则要点</b>：词（{@code [A-Za-z_][A-Za-z0-9_$]*}）统一扫描后查 {@link KeywordTable}（D9），
 * 命中即 KEYWORD；反引号包裹标识符永不查表。字符串支持 {@code ''} 双写转义与反斜杠转义，
 * {@code N''}/{@code X''}/{@code B''} 引导形态在词法层并入 STRING；{@code _charset'...'}
 * 引导形态 v1 不支持（词法层拆为标识符 + 字符串，由语法层拒绝）。数字含 {@code 0x}/{@code 0b}/
 * 科学计数并保留原文；运算符最长匹配（{@code <=>}、{@code :=}、{@code ->>}…）；注释跳过不产 token，
 * 偏移为源文本绝对偏移（跳过不断链）。非法字符与非 ASCII（字符串/注释外）抛 {@link LexException}。</p>
 *
 * <p><b>方言开关（§7.4，T4 以 MySQL 常量内嵌，T10 抽为 DialectProfile 数据）</b>：
 * {@code backslashEscapes=true}、{@code ansiQuotes=false}（双引号=字符串）、包裹符=反引号、
 * 行注释 {@code --}/{@code #}、块注释不嵌套、标识符 ASCII。</p>
 */
public final class Lexer {

    private final String sql;
    private final int length;

    /** 反斜杠转义开关（MySQL 默认开，NO_BACKSLASH_ESCAPES 关）。 */
    private final boolean backslashEscapes = true;

    private Lexer(final String sql) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.length = sql.length();
    }

    /** 单条 SQL 的完整 token 序列（末尾恒为 EOF 哨兵）。 */
    public static List<Token> lex(final String sql) {
        return new Lexer(sql).run();
    }

    private List<Token> run() {
        final List<Token> tokens = new ArrayList<Token>();
        int i = 0;
        while (true) {
            i = skipWhitespaceAndComments(i);
            if (i >= length) {
                tokens.add(new Token(TokenType.EOF, "", length, length, null));
                return tokens;
            }
            final char c = sql.charAt(i);
            if (isWordStart(c)) {
                i = scanWord(tokens, i);
            } else if ('\'' == c || '"' == c) {
                i = scanString(c, i, i, tokens);
            } else if ('`' == c) {
                i = scanQuotedIdentifier(i, tokens);
            } else if (isDigit(c) || ('.' == c && i + 1 < length && isDigit(sql.charAt(i + 1)))) {
                i = scanNumber(i, tokens);
            } else if ('.' == c) {
                tokens.add(new Token(TokenType.DOT, ".", i, i, null));
                i += 1;
            } else if ('?' == c) {
                tokens.add(new Token(TokenType.PARAM, "?", i, i, null));
                i += 1;
            } else {
                i = scanOperator(i, tokens);
            }
        }
    }

    // ---------- 词：关键字 / 标识符 / 引导字符串 ----------

    private int scanWord(final List<Token> tokens, final int start) {
        int i = start + 1;
        while (i < length && isWordChar(sql.charAt(i))) {
            i += 1;
        }
        // 单字母 N/X/B 紧邻单引号 → 引导字符串字面量（N'..' X'..' B'..'）
        if (i - start == 1 && i < length && '\'' == sql.charAt(i)) {
            final char lead = sql.charAt(start);
            if ('N' == lead || 'n' == lead || 'X' == lead || 'x' == lead || 'B' == lead || 'b' == lead) {
                return scanString('\'', i, start, tokens);
            }
        }
        final String word = sql.substring(start, i);
        final Kw kw = KeywordTable.lookupMySql(word);
        if (null != kw) {
            tokens.add(new Token(TokenType.KEYWORD, word, start, i - 1, kw));
        } else {
            tokens.add(new Token(TokenType.IDENTIFIER, word, start, i - 1, null));
        }
        return i;
    }

    // ---------- 字符串 / 包裹标识符 ----------

    /** 开引号位于 openAt，token 起点为 tokenStart（引导前缀时早于 openAt）。文本含引号与前缀原文。 */
    private int scanString(final char quote, final int openAt, final int tokenStart, final List<Token> tokens) {
        int i = openAt + 1;
        while (i < length) {
            final char c = sql.charAt(i);
            if ('\\' == c && backslashEscapes) {
                i += 2;
                continue;
            }
            if (quote == c) {
                if (i + 1 < length && quote == sql.charAt(i + 1)) {
                    i += 2;
                    continue;
                }
                tokens.add(new Token(TokenType.STRING, sql.substring(tokenStart, i + 1), tokenStart, i, null));
                return i + 1;
            }
            i += 1;
        }
        throw new LexException("未闭合的字符串字面量", tokenStart);
    }

    private int scanQuotedIdentifier(final int start, final List<Token> tokens) {
        int i = start + 1;
        while (i < length) {
            if ('`' == sql.charAt(i)) {
                if (i + 1 < length && '`' == sql.charAt(i + 1)) {
                    i += 2;
                    continue;
                }
                tokens.add(new Token(TokenType.QUOTED_IDENTIFIER, sql.substring(start, i + 1), start, i, null));
                return i + 1;
            }
            i += 1;
        }
        throw new LexException("未闭合的包裹标识符", start);
    }

    // ---------- 数字 ----------

    private int scanNumber(final int start, final List<Token> tokens) {
        int i = start;
        TokenType type = TokenType.NUMBER;
        if ('0' == sql.charAt(i) && i + 1 < length) {
            final char next = sql.charAt(i + 1);
            if ('x' == next || 'X' == next) {
                i += 2;
                final int digits = i;
                while (i < length && isHexDigit(sql.charAt(i))) {
                    i += 1;
                }
                if (i == digits) {
                    throw new LexException("0x 后缺十六进制数字", start);
                }
                return endNumber(tokens, TokenType.HEX, start, i);
            }
            if ('b' == next || 'B' == next) {
                i += 2;
                final int digits = i;
                while (i < length && ('0' == sql.charAt(i) || '1' == sql.charAt(i))) {
                    i += 1;
                }
                if (i == digits) {
                    throw new LexException("0b 后缺二进制数字", start);
                }
                return endNumber(tokens, TokenType.BIT, start, i);
            }
        }
        if ('.' == sql.charAt(i)) {
            i += 1;
            while (i < length && isDigit(sql.charAt(i))) {
                i += 1;
            }
        } else {
            while (i < length && isDigit(sql.charAt(i))) {
                i += 1;
            }
            if (i < length && '.' == sql.charAt(i)) {
                i += 1;
                while (i < length && isDigit(sql.charAt(i))) {
                    i += 1;
                }
            }
        }
        if (i < length && ('e' == sql.charAt(i) || 'E' == sql.charAt(i))) {
            final int eAt = i;
            i += 1;
            if (i < length && ('+' == sql.charAt(i) || '-' == sql.charAt(i))) {
                i += 1;
            }
            if (i >= length || !isDigit(sql.charAt(i))) {
                throw new LexException("非法科学计数形态", eAt);
            }
            while (i < length && isDigit(sql.charAt(i))) {
                i += 1;
            }
        }
        if (i < length && isWordChar(sql.charAt(i))) {
            throw new LexException("数字字面量后紧跟标识符字符", start);
        }
        return endNumber(tokens, type, start, i);
    }

    private int endNumber(final List<Token> tokens, final TokenType type, final int start, final int stop) {
        tokens.add(new Token(type, sql.substring(start, stop), start, stop - 1, null));
        return stop;
    }

    // ---------- 运算符（最长匹配） ----------

    private int scanOperator(final int start, final List<Token> tokens) {
        final char c = sql.charAt(start);
        switch (c) {
            case '=':
                return op(tokens, "=", start, 1);
            case '<':
                if (matches(start, "<<")) {
                    return op(tokens, "<<", start, 2);
                }
                if (matches(start, "<=>")) {
                    return op(tokens, "<=>", start, 3);
                }
                if (matches(start, "<=")) {
                    return op(tokens, "<=", start, 2);
                }
                if (matches(start, "<>")) {
                    return op(tokens, "<>", start, 2);
                }
                return op(tokens, "<", start, 1);
            case '>':
                if (matches(start, ">>")) {
                    return op(tokens, ">>", start, 2);
                }
                if (matches(start, ">=")) {
                    return op(tokens, ">=", start, 2);
                }
                return op(tokens, ">", start, 1);
            case ':':
                if (matches(start, ":=")) {
                    return op(tokens, ":=", start, 2);
                }
                throw new LexException("非法字符 ':'", start);
            case '-':
                if (matches(start, "->>")) {
                    return op(tokens, "->>", start, 3);
                }
                if (matches(start, "->")) {
                    return op(tokens, "->", start, 2);
                }
                return op(tokens, "-", start, 1);
            case '|':
                return op(tokens, matches(start, "||") ? "||" : "|", start, matches(start, "||") ? 2 : 1);
            case '&':
                return op(tokens, matches(start, "&&") ? "&&" : "&", start, matches(start, "&&") ? 2 : 1);
            case '!':
                if (matches(start, "!=")) {
                    return op(tokens, "!=", start, 2);
                }
                return op(tokens, "!", start, 1);
            case '+':
            case '*':
            case '/':
            case '%':
            case '~':
            case '^':
                return op(tokens, String.valueOf(c), start, 1);
            case '(':
                tokens.add(new Token(TokenType.LPAREN, "(", start, start, null));
                return start + 1;
            case ')':
                tokens.add(new Token(TokenType.RPAREN, ")", start, start, null));
                return start + 1;
            case ',':
                tokens.add(new Token(TokenType.COMMA, ",", start, start, null));
                return start + 1;
            case ';':
                tokens.add(new Token(TokenType.SEMICOLON, ";", start, start, null));
                return start + 1;
            default:
                throw new LexException("非法字符 '" + c + "'", start);
        }
    }

    private int op(final List<Token> tokens, final String text, final int start, final int size) {
        tokens.add(new Token(TokenType.OPERATOR, text, start, start + size - 1, null));
        return start + size;
    }

    private boolean matches(final int at, final String literal) {
        return sql.regionMatches(at, literal, 0, literal.length());
    }

    // ---------- 空白与注释（跳过不产 token，偏移绝对不断链） ----------

    private int skipWhitespaceAndComments(final int from) {
        int i = from;
        while (i < length) {
            final char c = sql.charAt(i);
            if (' ' == c || '\t' == c || '\n' == c || '\r' == c) {
                i += 1;
                continue;
            }
            if ('#' == c) {
                i = skipLineComment(i + 1);
                continue;
            }
            // MySQL 规则：-- 后须跟空白/EOL 才是行注释，"--x" 为两个减号
            if ('-' == c && i + 1 < length && '-' == sql.charAt(i + 1)
                    && (i + 2 >= length || isSpaceOrLineBreak(sql.charAt(i + 2)))) {
                i = skipLineComment(i + 2);
                continue;
            }
            if ('/' == c && i + 1 < length && '*' == sql.charAt(i + 1)) {
                i = skipBlockComment(i);
                continue;
            }
            return i;
        }
        return i;
    }

    private int skipLineComment(final int from) {
        int i = from;
        while (i < length && '\n' != sql.charAt(i)) {
            i += 1;
        }
        return i;
    }

    private int skipBlockComment(final int start) {
        for (int i = start + 2; i < length; i++) {
            if ('*' == sql.charAt(i) && i + 1 < length && '/' == sql.charAt(i + 1)) {
                return i + 2;
            }
        }
        throw new LexException("未闭合的块注释", start);
    }

    // ---------- 字符类别 ----------

    private static boolean isWordStart(final char c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || '_' == c;
    }

    private static boolean isWordChar(final char c) {
        return isWordStart(c) || isDigit(c) || '$' == c;
    }

    private static boolean isDigit(final char c) {
        return '0' <= c && c <= '9';
    }

    private static boolean isHexDigit(final char c) {
        return isDigit(c) || 'a' <= c && c <= 'f' || 'A' <= c && c <= 'F';
    }

    private static boolean isSpaceOrLineBreak(final char c) {
        return ' ' == c || '\t' == c || '\n' == c || '\r' == c;
    }
}
