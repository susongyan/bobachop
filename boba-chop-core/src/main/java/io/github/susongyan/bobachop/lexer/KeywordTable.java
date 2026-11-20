package io.github.susongyan.bobachop.lexer;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 方言关键字表（D9：标识符统一扫描后查表定身份，避免正则/逐字符分支）。
 *
 * <p><b>实例化于方言</b>（D8/T10）：收录哪些词、哪些词为保留字是方言数据，由
 * {@code dialect} 包的 DialectProfile 装配。键为小写原文，命中即 KEYWORD，未命中即
 * IDENTIFIER——"关键字当标识符用"由 {@link #reserved(Kw)} 在语法层裁决
 * （非保留字可进标识符位）。</p>
 *
 * <p>完备性不对称：漏收保留字 = 把非法 SQL 错误接受（如 {@code FROM offset}），
 * 漏收非保留字 = 退化为标识符，无害。清单由 ANTLR4 参考对拍校准（对拍是清单的验收器）。</p>
 */
public final class KeywordTable {

    private final Map<String, Kw> words;
    private final Set<Kw> reserved;

    /** keywords = 该方言收录的词；reserved = 其中不可出现在未包裹标识符位的保留字子集。 */
    public KeywordTable(final Set<Kw> keywords, final Set<Kw> reserved) {
        final Map<String, Kw> table = new HashMap<String, Kw>();
        for (final Kw kw : keywords) {
            table.put(kw.name().toLowerCase(Locale.ROOT), kw);
        }
        this.words = Collections.unmodifiableMap(table);
        this.reserved = Collections.unmodifiableSet(
                reserved.isEmpty() ? EnumSet.noneOf(Kw.class) : EnumSet.copyOf(reserved));
    }

    /** 关键字查找：返回命中的关键字，非关键字返回 null。 */
    public Kw lookup(final String scannedWord) {
        return words.get(scannedWord.toLowerCase(Locale.ROOT));
    }

    /** 是否该方言保留字：保留字不可出现在未包裹的标识符位。 */
    public boolean reserved(final Kw kw) {
        return reserved.contains(kw);
    }

    /** 本方言非保留字集（可作未包裹标识符，D9）——对拍体系 F2 生成器的标识符采样位。 */
    public Set<Kw> nonReservedWords() {
        final Set<Kw> out = EnumSet.copyOf(words.values());
        out.removeAll(reserved);
        return out;
    }

    /** MySQL 8.0 关键字表（Kw 主段全集，不含方言追加段）。 */
    public static KeywordTable mySql() {
        return MY_SQL;
    }

    private static final KeywordTable MY_SQL = new KeywordTable(mySqlKeywords(), mySqlReserved());

    private static Set<Kw> mySqlKeywords() {
        final Set<Kw> all = EnumSet.allOf(Kw.class);
        all.removeAll(Kw.dialectAdditions());
        return all;
    }

    private static Set<Kw> mySqlReserved() {
        final Set<Kw> reserved = mySqlKeywords();
        // MySQL 8.0 非保留（语法键控：LIMIT ... OFFSET / ON DUPLICATE KEY UPDATE /
        // LIKE ... ESCAPE / DATE|TIME|TIMESTAMP '...' 时间字面量 / FROM DUAL 虚表目标——
        // 官方表标 R 但专用产式恒允许，作保留字会误拒 SELECT 1 FROM DUAL）
        reserved.remove(Kw.OFFSET);
        reserved.remove(Kw.DUPLICATE);
        reserved.remove(Kw.ESCAPE);
        reserved.remove(Kw.DATE);
        reserved.remove(Kw.TIME);
        reserved.remove(Kw.TIMESTAMP);
        reserved.remove(Kw.DUAL);
        return reserved;
    }
}
