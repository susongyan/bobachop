package io.github.susongyan.bobachop.diff;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Statement;

/**
 * 候选侧执行器：本内核经 {@link BobaChop} 入口对拍。
 *
 * <p>拒绝按观测值收集不抛出（{@code ParseExecutor} 约定）；{@code RuntimeException}
 * 兜底由 {@link DiffHarness} 负责。词法/语法/白名单三类拒绝统一呈现为
 * {@code 类名: 消息}，对拍比较的是拒绝<b>事实</b>而非消息文本（D7）。</p>
 */
public final class BobachopExecutor implements ParseExecutor {

    @Override
    public String name() {
        return "bobachop";
    }

    @Override
    public ParseResult parse(final String sql) {
        try {
            final Statement statement = BobaChop.parse(sql);
            return ParseResult.success(statement);
        } catch (final RuntimeException e) {
            return ParseResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
