package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;

/**
 * F1 语料门禁（对拍体系 v2 第一器）：三方言全部形态语料，解析成功的语句必须
 * 全过 SpanInvariants 六条偏移不变量——D5 偏移契约从此以输入串为真值机械化验收，
 * 为 boba-pour 子串手术式改写供内核侧保证。
 */
final class SpanInvariantsCorpusTest {

    /** 语料 → 方言（形态语料分方言目录，语料切割线见 corpus/README.md；与 MutationFuzzTest 同一清单）。 */
    private static final String[][] CORPORA = {
        {"mysql", "select-v1.txt"}, {"mysql", "dml-v1.txt"},
        {"pg", "select-v1.txt"}, {"pg", "dml-v1.txt"},
        {"oracle", "select-v1.txt"}, {"oracle", "dml-v1.txt"},
    };

    @Test
    void 全语料六条偏移不变量全绿() throws IOException {
        final List<String> failures = new ArrayList<String>();
        int total = 0;
        for (final String[] corpus : CORPORA) {
            final DialectProfile profile = "mysql".equals(corpus[0]) ? DialectProfile.MYSQL
                    : "pg".equals(corpus[0]) ? DialectProfile.POSTGRESQL : DialectProfile.ORACLE;
            final Path path = Paths.get("..", "corpus", corpus[0], corpus[1]);
            assertTrue(Files.exists(path), "语料文件缺失: " + path.toAbsolutePath());
            for (final String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                final String sql = line.trim();
                if (sql.isEmpty() || sql.startsWith("--")) {
                    continue;
                }
                total++;
                final Statement stmt = BobaChop.parse(sql, profile);
                final List<String> violations = SpanInvariants.check(stmt, sql, profile);
                if (!violations.isEmpty()) {
                    failures.add(sql + "\n    " + String.join("\n    ", violations));
                }
            }
        }
        assertTrue(total >= 100, "语料条数异常: " + total);
        assertTrue(failures.isEmpty(), "偏移不变量违规 " + failures.size() + "/" + total + " 条语句:\n"
                + String.join("\n", failures));
    }
}
