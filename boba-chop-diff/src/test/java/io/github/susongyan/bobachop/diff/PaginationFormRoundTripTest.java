package io.github.susongyan.bobachop.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;

/**
 * F3 变异模糊发现的确定性回归：分页形态入契约（Limit.form）与 REGEXP/RLIKE 渲染收录。
 *
 * <p><b>分页形态</b>：参数文本序随书写形态翻转（逗号形态/标准族 offset 在前，LIMIT
 * 形态 rowCount 在前）——渲染若规范化为方言默认形态，往返后参数标记的语义槽位静默
 * 换位（BobaPour 场景绑定值错位，数据损坏级缺陷）。往返 compareStructure 含参数
 * index，全等即参数序保真。</p>
 *
 * <p><b>REGEXP/RLIKE</b>：解析器按比较级收录（§2 表），渲染器优先级表漏收
 * （IllegalArgumentException）——生成器空间照不到，变异空间暴露。</p>
 */
final class PaginationFormRoundTripTest {

    @Test
    void MySQL逗号形态_往返参数序不翻转() {
        // LIMIT o, n：文本序 offset 在前（?₁=offset ?₂=rowCount），不得规范化为 LIMIT n OFFSET o
        roundTrip(DialectProfile.MYSQL, "SELECT id FROM t_order LIMIT ?, ?", "LIMIT ?, ?");
        roundTrip(DialectProfile.MYSQL, "SELECT id FROM t_order LIMIT 20, 10", "LIMIT 20, 10");
    }

    @Test
    void MySQL两形态并存_各按原形态还原() {
        roundTrip(DialectProfile.MYSQL, "SELECT id FROM t_order LIMIT 10 OFFSET 20", "LIMIT 10 OFFSET 20");
        roundTrip(DialectProfile.MYSQL, "SELECT id FROM t_order LIMIT 10", "LIMIT 10");
    }

    @Test
    void PG的LIMIT输入_不被规范化为标准族形态() {
        // PG 同时接受 LIMIT 形态与 OFFSET/FETCH 形态；前者不得被改写为后者（参数序会翻转）
        roundTrip(DialectProfile.POSTGRESQL, "SELECT id FROM t_order LIMIT ? OFFSET ?", "LIMIT ? OFFSET ?");
        roundTrip(DialectProfile.POSTGRESQL, "SELECT id FROM t_order OFFSET 20 ROWS FETCH FIRST 10 ROWS ONLY",
                "FETCH FIRST 10 ROWS ONLY");
    }

    @Test
    void Oracle标准族形态_往返保真() {
        roundTrip(DialectProfile.ORACLE, "SELECT id FROM t_order OFFSET 20 ROWS FETCH FIRST 10 ROWS ONLY",
                "FETCH FIRST 10 ROWS ONLY");
        roundTrip(DialectProfile.ORACLE, "SELECT id FROM t_order FETCH FIRST 10 ROWS ONLY",
                "FETCH FIRST 10 ROWS ONLY");
    }

    @Test
    void REGEXP与RLIKE_渲染收录并往返() {
        roundTrip(DialectProfile.MYSQL, "SELECT id FROM t_order WHERE name REGEXP '^a'", "name REGEXP '^a'");
        roundTrip(DialectProfile.MYSQL, "SELECT id FROM t_order WHERE name RLIKE 'b$'", "name RLIKE 'b$'");
        // 与比较同级：混排时括号策略正确（两侧同级可裸、右邻低级必括）
        roundTrip(DialectProfile.MYSQL, "SELECT id FROM t_order WHERE name REGEXP 'a' AND id = 1", "REGEXP");
    }

    /** 解析→渲染（含形态标记断言）→重解析 compareStructure 全等（含参数 index）→打印幂等。 */
    private static void roundTrip(final DialectProfile profile, final String sql, final String marker) {
        final Statement first = BobaChop.parse(sql, profile);
        final AstRenderer renderer = new AstRenderer(profile);
        final String rendered = renderer.render(first);
        assertTrue(rendered.contains(marker), "渲染缺形态标记 '" + marker + "': " + rendered);
        final Statement reparsed = BobaChop.parse(rendered, profile);
        assertTrue(AstDiff.compareStructure(first, reparsed).isEmpty(),
                "往返结构不等（含参数序）: " + AstDiff.compareStructure(first, reparsed) + "\n  原文: " + sql
                        + "\n  渲染: " + rendered);
        assertEquals(rendered, renderer.render(reparsed), "打印不幂等: " + rendered);
    }
}
