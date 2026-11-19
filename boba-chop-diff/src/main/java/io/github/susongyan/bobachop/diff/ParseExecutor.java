package io.github.susongyan.bobachop.diff;

import io.github.susongyan.bobachop.ast.Statement;

/**
 * 对拍执行器：SQL 文本 → AST（或失败观测值）。
 *
 * <p>典型两侧：候选实现（本内核，M1 起接入）与参考实现（成熟 ANTLR4 方言内核，经适配器接入）。
 * 实现方应把可预期的拒绝（白名单外语法）转为 {@link ParseResult#failure}；
 * 意外异常由 {@link DiffHarness} 兜底捕获，同样按失败观测值处理。</p>
 */
public interface ParseExecutor {

    /** 执行器名称，用于报告渲染与日志（如 {@code bobachop} / {@code antlr4-ref}）。 */
    String name();

    /** 解析单条 SQL。 */
    ParseResult parse(String sql);
}
