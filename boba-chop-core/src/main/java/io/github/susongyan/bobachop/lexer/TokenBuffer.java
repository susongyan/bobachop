package io.github.susongyan.bobachop.lexer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单向 token 游标（D2 无回溯策略的唯一执行位）。
 *
 * <p><b>约定</b>：</p>
 * <ul>
 *   <li>{@link #peek(int)} 只读前瞻任意远不推进游标；越界恒返回 EOF 哨兵（永不抛越界），
 *       语法器可在语句尾安全地 {@code peek(1)}。</li>
 *   <li>{@link #next()} 单向推进一格；EOF 上反复 next 恒返回 EOF 不再推进（永不越界）。
 *       EOF 哨兵即语法器的"看门条件"。</li>
 *   <li><b>无 undo/mark/reset API</b>——需要"看更远"就 peek 更远；无界歧义点
 *       （如 INSERT 列清单 vs 子查询）用只读扫描 helper（扫到配对右括号看后继，T8），
 *       两者都不是回溯：游标从不后退。</li>
 *   <li>构造即校验：token 序列必须以 EOF 哨兵结尾。</li>
 * </ul>
 */
public final class TokenBuffer {

    private final List<Token> tokens;
    private final String source;
    private int cursor;

    public TokenBuffer(final String sql) {
        this(sql, Lexer.lex(sql));
    }

    /** source 可空：为空时语法器对限定名/时间字面量等跨 token 原文退化为 token 拼接。 */
    public TokenBuffer(final String source, final List<Token> tokens) {
        final List<Token> copy = new ArrayList<Token>(Objects.requireNonNull(tokens, "tokens"));
        if (copy.isEmpty() || copy.get(copy.size() - 1).type() != TokenType.EOF) {
            throw new IllegalArgumentException("token 序列必须以 EOF 哨兵结尾");
        }
        this.tokens = Collections.unmodifiableList(copy);
        this.source = source;
        this.cursor = 0;
    }

    public TokenBuffer(final List<Token> tokens) {
        this(null, tokens);
    }

    /** 只读前瞻：ahead=0 为当前 token（不消费），越界恒返回 EOF 哨兵。 */
    public Token peek(final int ahead) {
        if (ahead < 0) {
            throw new IllegalArgumentException("ahead 必须 >= 0: " + ahead);
        }
        final int index = cursor + ahead;
        return index < tokens.size() ? tokens.get(index) : tokens.get(tokens.size() - 1);
    }

    /** 单向推进并返回当前 token；EOF 上恒返回 EOF 不推进。 */
    public Token next() {
        final Token current = tokens.get(cursor);
        if (TokenType.EOF != current.type()) {
            cursor += 1;
        }
        return current;
    }

    /** 当前是否已到 EOF。 */
    public boolean eof() {
        return TokenType.EOF == tokens.get(cursor).type();
    }

    /** 已消费 token 数（诊断与报错定位用）。 */
    public int consumed() {
        return cursor;
    }

    /** 只读视图（对拍与诊断）。 */
    public List<Token> tokens() {
        return tokens;
    }

    /** 源 SQL 文本（语法器取跨 token 原文切片用）；List 构造路径可为 null。 */
    public String source() {
        return source;
    }
}
