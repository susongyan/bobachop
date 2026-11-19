package io.github.susongyan.bobachop.ast;

/**
 * 排序方向三态：未指定 / ASC / DESC。
 */
public enum OrderDirection {
    /** 未书写方向（缺省 ASC）。 */
    UNSPECIFIED,
    /** 显式 ASC。 */
    ASC,
    /** DESC。 */
    DESC
}
