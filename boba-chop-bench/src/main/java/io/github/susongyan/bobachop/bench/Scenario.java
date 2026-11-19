package io.github.susongyan.bobachop.bench;

import java.util.List;

/** 基准场景：产出指标清单（报告与 CSV 行的数据源）。 */
interface Scenario {

    String name();

    List<Metric> run();
}
