package io.github.susongyan.bobachop.bench;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.github.susongyan.bobachop.api.BobaChop;

/** 基准形态黄金用例：全部形态可被内核解析（形态进语料即受门禁保护）。 */
final class ShapesTest {

    @Test
    void 全部形态可解析() {
        assertNotNull(BobaChop.parse(Shapes.SIMPLE_SELECT));
        assertNotNull(BobaChop.parse(Shapes.JOIN_SELECT));
        assertNotNull(BobaChop.parse(Shapes.COMPOSITE_SELECT));
        assertNotNull(BobaChop.parse(Shapes.UPDATE_WHERE));
        assertNotNull(BobaChop.parse(Shapes.inListSelect(1000)));
        assertNotNull(BobaChop.parse(Shapes.inListSelect(100)));
        assertNotNull(BobaChop.parse(Shapes.batchInsert(100)));
        assertNotNull(BobaChop.parse(Shapes.batchInsert(1000)));
    }
}
