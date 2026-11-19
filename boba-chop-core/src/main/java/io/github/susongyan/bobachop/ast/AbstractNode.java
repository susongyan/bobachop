package io.github.susongyan.bobachop.ast;

/**
 * 位置信息基类：start/stop 由解析器在构造时一次写入，此后只读。
 */
public abstract class AbstractNode implements Node {

    private final int start;
    private final int stop;

    protected AbstractNode(final int start, final int stop) {
        this.start = start;
        this.stop = stop;
    }

    @Override
    public final int start() {
        return start;
    }

    @Override
    public final int stop() {
        return stop;
    }
}
