package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 白名单三件套门禁（WHITELIST.md §9）：规格文档必须存在，且其声明的对齐版本
 * 在 DESIGN.md 版本记录中有对应冻结条目——规格条目与冻结决策一一对应（四者一致性的机械化锚）。
 */
final class WhitelistDocTest {

    private static final Pattern ALIGNED = Pattern.compile("对齐 DESIGN v(\\d+\\.\\d+)");

    @Test
    void 白名单规格存在且与design冻结版本对齐() throws IOException {
        final Path whitelist = Paths.get("..", "WHITELIST.md");
        assertTrue(Files.exists(whitelist), "WHITELIST.md 缺失: " + whitelist.toAbsolutePath());
        final String spec = new String(Files.readAllBytes(whitelist), StandardCharsets.UTF_8);
        final Matcher matcher = ALIGNED.matcher(spec);
        assertTrue(matcher.find(), "WHITELIST.md 未声明对齐 DESIGN 版本");
        final String version = matcher.group(1);
        final Path design = Paths.get("..", "DESIGN.md");
        assertTrue(Files.exists(design), "DESIGN.md 缺失");
        final String designText = new String(Files.readAllBytes(design), StandardCharsets.UTF_8);
        assertNotNull(version);
        assertTrue(designText.contains("v" + version + "（"),
                "WHITELIST.md 对齐 v" + version + " 但 DESIGN.md 版本记录无此冻结条目");
    }
}
