package io.github.susongyan.bobachop.bench;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 场景冒烟（缩规模参数）：三温场景产出有限值指标；冷启动场景只测子进程协议本身
 * （一次子 JVM），全量重复由 BenchMain 执行。
 */
final class ScenarioSmokeTest {

    @Test
    void 温场景缩规模可跑且指标有限() {
        assertSane(new FirstWalkScenario(200, 50).run());
        assertSane(new SteadyStateScenario(100, 500, 100).run());
        assertSane(new LinearityScenario(new int[]{10, 20, 40}, 5, 3).run());
    }

    @Test
    void 冷启动子进程协议可用() {
        final List<Metric> metrics = new ColdStartScenario(1).run();
        assertSane(metrics);
        assertTrue(metrics.get(0).value > 0);
    }

    private void assertSane(final List<Metric> metrics) {
        assertFalse(metrics.isEmpty());
        for (final Metric metric : metrics) {
            assertTrue(!Double.isNaN(metric.value) && !Double.isInfinite(metric.value),
                    "指标非法: " + metric.label);
        }
    }
}
