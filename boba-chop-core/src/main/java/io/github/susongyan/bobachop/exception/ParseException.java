package io.github.susongyan.bobachop.exception;

/**
 * 语法错误：输入不合文法（缺操作数、括号不配、表达式后多余 token 等，输入的错）。
 *
 * <p>与 {@link UnsupportedSyntaxException}（白名单外拒绝，产品行为）相区分；
 * 对外入口层两者都归并为拒绝语义上抛。</p>
 */
public final class ParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int offset;

    public ParseException(final String message, final int offset) {
        super(message + " @" + offset);
        this.offset = offset;
    }

    /** 出错位置（源文本字符偏移）。 */
    public int offset() {
        return offset;
    }
}
