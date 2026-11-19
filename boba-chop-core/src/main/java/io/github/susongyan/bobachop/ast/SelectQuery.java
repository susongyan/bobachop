package io.github.susongyan.bobachop.ast;

/**
 * 查询体标记：单查询（{@link SelectStatement}）或集合操作复合（{@link CompoundSelect}）。
 *
 * <p>凡"可以出现 SELECT 的位置"（子查询、派生表、IN 右侧、EXISTS）持有的都是本接口，
 * 便于后续追加查询体形态而不破坏调用方。</p>
 */
public interface SelectQuery extends Node {
}
