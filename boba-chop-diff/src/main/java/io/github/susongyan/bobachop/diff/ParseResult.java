package io.github.susongyan.bobachop.diff;

import java.util.Objects;

import io.github.susongyan.bobachop.ast.Statement;

/**
 * 单执行器一次解析的观测值：成功（statement 非空）或失败（error 描述非空），恰一非空。
 *
 * <p>失败不作为异常抛出——对拍把"拒绝"当作与"成功"同级的正常观测值收集，
 * 批量比对不因单条输入中断（DESIGN D7）。</p>
 */
public final class ParseResult {

    private final Statement statement;
    private final String error;

    private ParseResult(final Statement statement, final String error) {
        this.statement = statement;
        this.error = error;
    }

    public static ParseResult success(final Statement statement) {
        return new ParseResult(Objects.requireNonNull(statement, "statement"), null);
    }

    /** 失败描述：异常类名 + 消息等，由执行器或兜底捕获方给出。 */
    public static ParseResult failure(final String error) {
        return new ParseResult(null, Objects.requireNonNull(error, "error"));
    }

    public boolean success() {
        return null != statement;
    }

    /** 解析出的语句根节点；失败时为 null。 */
    public Statement statement() {
        return statement;
    }

    /** 失败描述；成功时为 null。 */
    public String error() {
        return error;
    }
}
