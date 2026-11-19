package io.github.susongyan.bobachop.exception;

/**
 * 词法错误：非法字符、未闭合的字符串/包裹标识符/块注释、坏数字形态等（输入的错）。
 *
 * <p>携带出错偏移便于诊断；对外入口层将其归并为拒绝（UnsupportedSyntax 语义，DESIGN §7.2）。</p>
 */
public final class LexException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int offset;

    public LexException(final String message, final int offset) {
        super(message + " @" + offset);
        this.offset = offset;
    }

    /** 出错位置（源文本字符偏移）。 */
    public int offset() {
        return offset;
    }
}
