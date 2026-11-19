package io.github.susongyan.bobachop.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 函数调用。
 *
 * <p>实参可含 {@link Star}（{@code COUNT(*)}）与嵌套表达式；函数名保源大小写。
 * 带 {@code CAST(a AS t)} 类型的函数不在 v1 白名单（后续以专门节点追加）。</p>
 */
public final class FunctionCall extends AbstractNode implements Expr {

    private final String name;
    private final List<Expr> arguments;
    private final boolean distinct;

    public FunctionCall(final int start, final int stop, final String name, final List<Expr> arguments, final boolean distinct) {
        super(start, stop);
        this.name = Objects.requireNonNull(name, "name");
        this.arguments = Collections.unmodifiableList(new ArrayList<Expr>(Objects.requireNonNull(arguments, "arguments")));
        this.distinct = distinct;
    }

    /** 函数名（源大小写）。 */
    public String name() {
        return name;
    }

    /** 实参列表（不可变；可为空列表）。 */
    public List<Expr> arguments() {
        return arguments;
    }

    /** 是否带 DISTINCT（如 {@code COUNT(DISTINCT x)}）。 */
    public boolean distinct() {
        return distinct;
    }
}
