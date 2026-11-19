package io.github.susongyan.bobachop.lexer;

import java.util.Objects;

/**
 * 词法器方言旋钮（D8 / DESIGN §7.4：方言差异集中在词法层）。纯数据载体，由
 * {@code dialect} 包的 DialectProfile 装配——本类不引用解析层，lexer 包保持自闭环
 * （依赖方向恒为 dialect → lexer，避免环）。
 *
 * <p><b>旋钮清单</b>：字符串反斜杠转义、双引号身份（字符串 vs ANSI 标识符）、反引号包裹、
 * {@code #} 行注释、{@code --} 行注释的空白后随要求、块注释嵌套、引导字符串前缀集
 * （如 MySQL {@code N''/X''/B''}、PG {@code E''/B''/X''}）及强制转义前缀集（PG 的
 * {@code E''} 无论全局开关恒做反斜杠转义）。</p>
 */
public final class LexerConfig {

    private final KeywordTable keywords;
    private final boolean backslashEscapes;
    private final boolean ansiQuotes;
    private final boolean backtickQuotes;
    private final boolean hashLineComments;
    private final boolean lineCommentRequiresWhitespace;
    private final boolean nestedBlockComments;
    private final String stringPrefixes;
    private final String escapedStringPrefixes;

    private LexerConfig(final Builder builder) {
        this.keywords = builder.keywords;
        this.backslashEscapes = builder.backslashEscapes;
        this.ansiQuotes = builder.ansiQuotes;
        this.backtickQuotes = builder.backtickQuotes;
        this.hashLineComments = builder.hashLineComments;
        this.lineCommentRequiresWhitespace = builder.lineCommentRequiresWhitespace;
        this.nestedBlockComments = builder.nestedBlockComments;
        this.stringPrefixes = builder.stringPrefixes;
        this.escapedStringPrefixes = builder.escapedStringPrefixes;
    }

    /** MySQL 8.0 默认词法配置（反斜杠转义开、双引号=字符串、反引号包裹、# 注释、-- 须后随空白）。 */
    public static final LexerConfig MY_SQL = builder().keywords(KeywordTable.mySql()).build();

    /** 建造者：未显式设置的旋钮取 MySQL 基线值（方言差异显式声明，不藏默认）。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 本方言关键字表。 */
    public KeywordTable keywords() {
        return keywords;
    }

    /** 反斜杠转义开关（MySQL 默认开；PG 标准字符串关，E'' 前缀另见强制集）。 */
    public boolean backslashEscapes() {
        return backslashEscapes;
    }

    /** 双引号 = ANSI 包裹标识符（PG）；false 时双引号为字符串（MySQL）。 */
    public boolean ansiQuotes() {
        return ansiQuotes;
    }

    /** 反引号包裹标识符（MySQL）；false 时反引号为非法字符。 */
    public boolean backtickQuotes() {
        return backtickQuotes;
    }

    /** {@code #} 行注释（MySQL）。 */
    public boolean hashLineComments() {
        return hashLineComments;
    }

    /** {@code --} 行注释须后随空白/EOL（MySQL）；false 时 {@code --} 恒为注释（PG）。 */
    public boolean lineCommentRequiresWhitespace() {
        return lineCommentRequiresWhitespace;
    }

    /** 块注释嵌套（PG；MySQL 首个 {@code *&#47;} 即闭合）。 */
    public boolean nestedBlockComments() {
        return nestedBlockComments;
    }

    /** 引导字符串前缀集（大写字母串；命中紧邻单引号即并入 STRING token）。 */
    public String stringPrefixes() {
        return stringPrefixes;
    }

    /** 强制反斜杠转义的前缀集（大写字母串；如 PG 的 E，无视全局开关恒转义）。 */
    public String escapedStringPrefixes() {
        return escapedStringPrefixes;
    }

    /** {@link #stringPrefixes} 前缀是否强制反斜杠转义。 */
    public boolean forcesEscape(final char lead) {
        return escapedStringPrefixes.indexOf(Character.toUpperCase(lead)) >= 0;
    }

    /** 单字母是否为本方言引导字符串前缀。 */
    public boolean isStringPrefix(final char lead) {
        return stringPrefixes.indexOf(Character.toUpperCase(lead)) >= 0;
    }

    /** 建造者（Java 8 手写，链式）。 */
    public static final class Builder {

        private KeywordTable keywords;
        private boolean backslashEscapes = true;
        private boolean ansiQuotes = false;
        private boolean backtickQuotes = true;
        private boolean hashLineComments = true;
        private boolean lineCommentRequiresWhitespace = true;
        private boolean nestedBlockComments = false;
        private String stringPrefixes = "NXB";
        private String escapedStringPrefixes = "";

        /** 关键字表（必填，方言身份的词法半边）。 */
        public Builder keywords(final KeywordTable table) {
            this.keywords = Objects.requireNonNull(table, "keywords");
            return this;
        }

        public Builder backslashEscapes(final boolean value) {
            this.backslashEscapes = value;
            return this;
        }

        public Builder ansiQuotes(final boolean value) {
            this.ansiQuotes = value;
            return this;
        }

        public Builder backtickQuotes(final boolean value) {
            this.backtickQuotes = value;
            return this;
        }

        public Builder hashLineComments(final boolean value) {
            this.hashLineComments = value;
            return this;
        }

        public Builder lineCommentRequiresWhitespace(final boolean value) {
            this.lineCommentRequiresWhitespace = value;
            return this;
        }

        public Builder nestedBlockComments(final boolean value) {
            this.nestedBlockComments = value;
            return this;
        }

        public Builder stringPrefixes(final String value) {
            this.stringPrefixes = Objects.requireNonNull(value, "stringPrefixes");
            return this;
        }

        public Builder escapedStringPrefixes(final String value) {
            this.escapedStringPrefixes = Objects.requireNonNull(value, "escapedStringPrefixes");
            return this;
        }

        public LexerConfig build() {
            Objects.requireNonNull(keywords, "keywords 未设置");
            return new LexerConfig(this);
        }
    }
}
