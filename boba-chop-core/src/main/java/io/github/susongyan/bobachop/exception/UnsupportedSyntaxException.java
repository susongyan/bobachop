package io.github.susongyan.bobachop.exception;

/**
 * 白名单外拒绝：语法识别但明确不在支持清单（产品行为，拒绝率是 KPI 统计对象）。
 *
 * <p>每条拒绝对应白名单文档条目（三件套：规格 + 拒绝点 + 对拍用例）。词法/语法错误
 * 在对外入口层同样归并为本异常上抛（DESIGN §7.2"LexException 转 UnsupportedSyntax 上抛"）。</p>
 */
public final class UnsupportedSyntaxException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedSyntaxException(final String message) {
        super(message);
    }

    public UnsupportedSyntaxException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
