/**
 * AST 契约 v1（DESIGN D4：发布即冻结，只增不改）。
 *
 * <p><b>约定</b>：</p>
 * <ul>
 *   <li>全节点携带源文本位置（闭区间字符偏移，{@code Node#start()}/{@code Node#stop()}）。</li>
 *   <li>全部节点不可变：位置与字段由解析器构造时一次写入；集合字段返回不可变视图。</li>
 *   <li>AST 存语法事实：运算符/标识符/字面量一律保源文本与大小写，不做跨方言语义归一化。</li>
 *   <li>字面量只存文本不解释值；参数标记下标按访问序（白名单构造内 = 文本序）。</li>
 *   <li>可空字段以 null 表示且逐字段 javadoc 标注；"恰一非空"的不变量由构造方保证并注明。</li>
 *   <li>不定义 equals/hashCode：深度比较属对拍框架（boba-chop-diff）职责。</li>
 *   <li>扩展规则：新构造 = 新类/新枚举常量/新可空字段（字段追加仅限"未出现为 null"语义），
 *       不得改动既有字段语义或删除。</li>
 * </ul>
 */
package io.github.susongyan.bobachop.ast;
