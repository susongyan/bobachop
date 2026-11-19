package io.github.susongyan.bobachop.ast;

/**
 * AST 节点公共契约。
 *
 * <p>所有节点携带源文本位置：{@link #start()}/{@link #stop()} 为字符偏移的<b>闭区间</b>，
 * 对齐主流生成器约定（DESIGN D5）。下游改写按偏移切片原文，偏移错 = 改写错。</p>
 */
public interface Node {

    /** 节点起始字符偏移（含）。 */
    int start();

    /** 节点结束字符偏移（含）。 */
    int stop();
}
