package io.github.susongyan.bobachop.ast;

import java.util.List;

/**
 * 指纹结果契约：字面量归一化后的模板串 + 被摘出的字面量序列。
 *
 * <p>供集成方自建模板缓存（DESIGN 非目标：本内核不做解析结果缓存，只提供指纹原料）。
 * 实现由后续任务提供；契约先行冻结。</p>
 */
public interface Fingerprint {

    /**
     * 模板串：原 SQL 中所有字面量替换为 {@code ?} 后的文本。
     * 已是参数标记的位置保持原样；空白与大小写保持源形态（模板串相等即字符串相等）。
     */
    String template();

    /** 按文本序摘出的字面量清单（与模板串中的占位一一对应；源参数标记不出现在此清单）。 */
    List<Literal> literals();
}
