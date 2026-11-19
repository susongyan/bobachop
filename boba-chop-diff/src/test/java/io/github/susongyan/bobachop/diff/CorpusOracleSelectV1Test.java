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

import io.github.susongyan.bobachop.dialect.DialectProfile;

/**
 * corpus T11 门禁：Oracle SELECT v1 形态语料全量经本内核（Oracle profile）解析必须全绿
 * （DoD："Oracle 语料对拍全绿" 的本内核侧前置——双执行器对拍待 ANTLR4 Oracle 参考适配器接入）。
 */
final class CorpusOracleSelectV1Test {

    @Test
    void oracleSelectV1语料全量解析成功() throws IOException {
        final Path corpus = Paths.get("..", "corpus", "oracle", "select-v1.txt");
        assertTrue(Files.exists(corpus), "语料文件缺失: " + corpus.toAbsolutePath());
        final List<String> failures = new ArrayList<String>();
        int total = 0;
        for (final String line : Files.readAllLines(corpus, StandardCharsets.UTF_8)) {
            final String sql = line.trim();
            if (sql.isEmpty() || sql.startsWith("--")) {
                continue;
            }
            total++;
            final ParseResult result = new BobachopExecutor(DialectProfile.ORACLE).parse(sql);
            if (!result.success()) {
                failures.add(sql + "  =>  " + result.error());
            }
        }
        assertTrue(total >= 20, "语料条数异常: " + total);
        assertTrue(failures.isEmpty(), "解析失败 " + failures.size() + "/" + total + " 条:\n"
                + String.join("\n", failures));
    }
}
