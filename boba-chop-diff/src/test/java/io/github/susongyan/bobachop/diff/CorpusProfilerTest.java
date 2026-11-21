package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.susongyan.bobachop.dialect.DialectProfile;

/**
 * 语料剖析器冒烟：MySQL 形态语料全接受（select 39 + dml 27 = 66 条）；
 * 合成目录的拒绝三支分类计数正确；报告含形态分布与长度段；
 * 三方言混合目录（跨方言语句）剖析不崩溃（观测工具不设门禁）。
 */
final class CorpusProfilerTest {

    @Test
    void mysql形态语料全接受() throws IOException {
        final String report = CorpusProfiler.profile(Paths.get("..", "corpus", "mysql"), DialectProfile.MYSQL);
        assertTrue(report.contains("语句数: 66"), report);
        assertTrue(report.contains("接受: 66 (100.0%)"), report);
        assertTrue(report.contains("SELECT: 37"), report);
        assertTrue(report.contains("SELECT(UNION): 2"), report);
        assertTrue(report.contains("INSERT: 17"), report);
    }

    @Test
    void 合成语料拒绝分类计数正确(@TempDir final Path temp) throws IOException {
        final Path file = temp.resolve("dump.sql");
        Files.write(file, (String.join("\n",
                "-- 注释行跳过",
                "SELECT id FROM t_order WHERE id = ?;",
                "WITH c AS (SELECT 1 FROM dual) SELECT * FROM c",
                "SELECT FROM",
                "")).getBytes(StandardCharsets.UTF_8));
        final String report = CorpusProfiler.profile(temp, DialectProfile.MYSQL);
        assertTrue(report.contains("语句数: 3"), report);
        assertTrue(report.contains("接受: 1 (33.3%)"), report);
        assertTrue(report.contains("Unsupported: 1"), report);
        assertTrue(report.contains("Parse: 1"), report);
        assertTrue(report.contains("SELECT: 1"), report);
        assertTrue(report.contains("p50:"), report);
    }

    @Test
    void 三方言混合目录剖析不崩溃() throws IOException {
        final String report = CorpusProfiler.profile(Paths.get("..", "corpus"), DialectProfile.MYSQL);
        // PG/Oracle 语句在 MySQL profile 下落入拒绝支属预期，只验证报告完整成形
        assertTrue(report.contains("语句数: 19"), report);
        assertTrue(report.contains("## Top Unsupported 构造"), report);
        assertTrue(report.contains("## 语句长度（字符）"), report);
    }
}
