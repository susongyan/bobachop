package io.github.susongyan.bobachop.bench;

/** 单项基准指标：标签 + 数值 + 单位 + 备注（可空）。 */
final class Metric {

    final String label;
    final double value;
    final String unit;
    final String note;

    Metric(final String label, final double value, final String unit, final String note) {
        this.label = label;
        this.value = value;
        this.unit = unit;
        this.note = note;
    }

    /** 数值格式化：ns 指标渲染为自适应单位（ns/µs/ms）。 */
    String render() {
        if ("ns".equals(unit)) {
            if (value >= 1_000_000) {
                return String.format("%.2f ms", value / 1_000_000);
            }
            if (value >= 1_000) {
                return String.format("%.2f µs", value / 1_000);
            }
            return String.format("%.0f ns", value);
        }
        return String.format("%s %s", formatValue(), unit);
    }

    String formatValue() {
        if (value == Math.rint(value)) {
            return String.format("%,.0f", value);
        }
        return String.format("%s", value);
    }
}
