package io.github.susongyan.bobachop.lexer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 关键字静态哈希表（D9：标识符统一扫描后查表定身份，避免正则/逐字符分支）。
 *
 * <p>键为小写原文，命中即 KEYWORD，未命中即 IDENTIFIER——"关键字当标识符用"由
 * {@link Kw#reserved()} 在语法层裁决（非保留字可进标识符位）。</p>
 */
public final class KeywordTable {

    private static final Map<String, Kw> MY_SQL;

    static {
        final Map<String, Kw> table = new HashMap<String, Kw>();
        for (final Kw kw : Kw.values()) {
            table.put(kw.name().toLowerCase(Locale.ROOT), kw);
        }
        MY_SQL = Collections.unmodifiableMap(table);
    }

    private KeywordTable() {
    }

    /** MySQL 关键字查找：返回命中的关键字，非关键字返回 null。 */
    public static Kw lookupMySql(final String scannedWord) {
        return MY_SQL.get(scannedWord.toLowerCase(Locale.ROOT));
    }
}
