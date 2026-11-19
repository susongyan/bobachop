package io.github.susongyan.bobachop.lexer;

import java.util.Objects;

/**
 * 词法 token：类别 + 源文本（保大小写）+ 闭区间偏移 + 关键字（可空）。
 *
 * <p>不变量：{@code type == KEYWORD} 当且仅当 {@code kw != null}。
 * 偏移为源文本绝对偏移——注释/空白跳过不记账、不断链，后续 token 偏移不受影响。</p>
 */
public final class Token {

    private final TokenType type;
    private final String text;
    private final int start;
    private final int stop;
    private final Kw kw;

    public Token(final TokenType type, final String text, final int start, final int stop, final Kw kw) {
        this.type = Objects.requireNonNull(type, "type");
        this.text = Objects.requireNonNull(text, "text");
        if ((type == TokenType.KEYWORD) != (null != kw)) {
            throw new IllegalArgumentException("KEYWORD 与 kw 必须同时非空/同时空: " + type);
        }
        this.start = start;
        this.stop = stop;
        this.kw = kw;
    }

    public TokenType type() {
        return type;
    }

    /** 源文本（保大小写，含引号/前缀原文）。 */
    public String text() {
        return text;
    }

    /** 起始偏移（含）。 */
    public int start() {
        return start;
    }

    /** 结束偏移（含）。 */
    public int stop() {
        return stop;
    }

    /** 命中的关键字；非 KEYWORD token 为 null。 */
    public Kw kw() {
        return kw;
    }

    /** 关键字匹配（忽略大小写）；非 KEYWORD 恒 false。供语法器分派。 */
    public boolean isKw(final Kw candidate) {
        return type == TokenType.KEYWORD && kw == candidate;
    }

    @Override
    public String toString() {
        return type + "(" + text + ")[" + start + "," + stop + "]";
    }
}
