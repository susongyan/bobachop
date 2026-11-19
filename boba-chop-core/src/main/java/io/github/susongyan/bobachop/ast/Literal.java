package io.github.susongyan.bobachop.ast;

import java.util.Objects;

/**
 * 字面量叶子。
 *
 * <p><b>只存源文本（含引号/前缀，保大小写），不做值解释</b>——值语义（数值大小、字符串内容、
 * 进制）由下游按需解析。这是"AST 存语法事实"原则（DESIGN §7.4）在字面量上的体现。</p>
 */
public final class Literal extends AbstractNode implements Expr {

    private final LiteralKind kind;
    private final String text;

    public Literal(final int start, final int stop, final LiteralKind kind, final String text) {
        super(start, stop);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.text = Objects.requireNonNull(text, "text");
    }

    /** 字面量类别。 */
    public LiteralKind kind() {
        return kind;
    }

    /** 源文本：含引号与前缀（如 {@code 'a''b'}、{@code 0x1F}、{@code 1.5e3}），保大小写。 */
    public String text() {
        return text;
    }
}
