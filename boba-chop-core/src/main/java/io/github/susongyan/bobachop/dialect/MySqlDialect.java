package io.github.susongyan.bobachop.dialect;

import io.github.susongyan.bobachop.lexer.LexerConfig;

/**
 * MySQL 8.0 方言（T4 冻结的词法事实于 T10 落为 profile 数据）：
 * 反斜杠转义开、双引号=字符串、反引号包裹、{@code #} 行注释、{@code --} 须后随空白、
 * 块注释不嵌套、引导前缀 {@code N''/X''/B''}、{@code LIMIT [o,]n} 三形态。
 */
final class MySqlDialect extends DialectProfile {

    @Override
    public String name() {
        return "MySQL";
    }

    @Override
    public LexerConfig lexerConfig() {
        return LexerConfig.MY_SQL;
    }

    @Override
    public boolean limitCommaOffset() {
        return true;
    }

    @Override
    public boolean fetchFirstPagination() {
        return false;
    }
}
