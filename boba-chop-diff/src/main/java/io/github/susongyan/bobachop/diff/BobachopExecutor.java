package io.github.susongyan.bobachop.diff;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;

/**
 * 候选侧执行器：本内核经 {@link BobaChop} 入口对拍（默认 MySQL，可指定方言 profile）。
 *
 * <p>拒绝按观测值收集不抛出（{@code ParseExecutor} 约定）；{@code RuntimeException}
 * 兜底由 {@link DiffHarness} 负责。词法/语法/白名单三类拒绝统一呈现为
 * {@code 类名: 消息}，对拍比较的是拒绝<b>事实</b>而非消息文本（D7）。</p>
 */
public final class BobachopExecutor implements ParseExecutor {

    private final DialectProfile profile;

    /** MySQL 方言执行器。 */
    public BobachopExecutor() {
        this(DialectProfile.MYSQL);
    }

    /** 指定方言执行器（T10：PG 语料对拍）。 */
    public BobachopExecutor(final DialectProfile profile) {
        this.profile = profile;
    }

    @Override
    public String name() {
        return "bobachop-" + profile.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public ParseResult parse(final String sql) {
        try {
            final Statement statement = BobaChop.parse(sql, profile);
            return ParseResult.success(statement);
        } catch (final RuntimeException e) {
            return ParseResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
