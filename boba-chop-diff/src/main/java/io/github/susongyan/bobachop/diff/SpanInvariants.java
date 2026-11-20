package io.github.susongyan.bobachop.diff;

import java.util.ArrayList;
import java.util.List;

import io.github.susongyan.bobachop.ast.ColumnRef;
import io.github.susongyan.bobachop.ast.FunctionCall;
import io.github.susongyan.bobachop.ast.Literal;
import io.github.susongyan.bobachop.ast.Node;
import io.github.susongyan.bobachop.ast.ParameterMarker;
import io.github.susongyan.bobachop.ast.Star;
import io.github.susongyan.bobachop.ast.Statement;
import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.lexer.Lexer;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * 对拍体系 v2 F1——偏移不变量校验器：对任意解析成功的语句，断言 D5 偏移契约
 * 可直接用于子串手术（boba-pour 加密改写的内核侧保证）。
 *
 * <p><b>真值来源</b>（D7 修正版：权威来自独立性机制，不来自来源名气）：偏移真值是
 * <b>输入串本身</b>——以一次独立词法（{@link Lexer}，黄金用例单独门禁）为标尺，
 * 校验 AST 声明的每个 span，不与任何外部引擎比对。</p>
 *
 * <p>不变量六条：</p>
 * <ul>
 *   <li><b>R1 界内</b>：{@code 0 ≤ start ≤ stop < sql.length()}；</li>
 *   <li><b>R2 token 对齐</b>：span 两端落在 token 边界上（存在 i ≤ j，
 *       {@code start == tokens[i].start && stop == tokens[j].stop}）——切在 token
 *       中间的 span 对子串手术是致命伤；</li>
 *   <li><b>R3 包含</b>：子节点 span ⊆ 父节点 span；</li>
 *   <li><b>R4 兄弟不重叠</b>：同父相邻子节点 span 两两不重叠（不校验字段间源序——
 *       Limit 的 offset/rowCount 源序随方言形态翻转，见 SpanWalker）；</li>
 *   <li><b>R5 根对齐</b>：语句根 span = [首个 token 起点, 末个非分号 token 终点]；</li>
 *   <li><b>R6 叶子原文</b>：Literal/Star/ParameterMarker/ColumnRef/FunctionCall 的
 *       span 切片与节点存文一致（契约"只存原文"的可执行化）；限定名（ColumnRef/Star
 *       的 qualifier 链）段间允许空白——{@code o . id} 为合法 SQL，带点形态去空白比较
 *       （F3 变异体暴露的公式校准，无点形态仍精确相等）。</li>
 * </ul>
 *
 * <p>返回违规清单（空 = 全过）。每条违规携带规则号 + 节点类型 + span + 现场片段，
 * 供语料门禁与 F2 生成句 / F3 变异幸存句复用。</p>
 */
public final class SpanInvariants {

    private SpanInvariants() {
    }

    /** 全量校验一条语句；violations 为空即六条不变量全过。 */
    public static List<String> check(final Statement statement, final String sql, final DialectProfile profile) {
        final List<Token> tokens = ruler(sql, profile);
        final Violations out = new Violations(sql);
        checkTree(statement, null, tokens, sql, out);
        return out.list;
    }

    private static void checkTree(final Node node, final Node parent, final List<Token> tokens, final String sql,
            final Violations out) {
        checkNode(node, parent, tokens, sql, out);
        for (final Node child : SpanWalker.children(node)) {
            checkTree(child, node, tokens, sql, out);
        }
    }

    /** 独立词法标尺：去除 EOF 哨兵与尾分号（分号不入任何节点 span）。 */
    private static List<Token> ruler(final String sql, final DialectProfile profile) {
        final List<Token> tokens = new ArrayList<Token>();
        for (final Token token : Lexer.lex(sql, profile.lexerConfig())) {
            if (TokenType.EOF == token.type() || TokenType.SEMICOLON == token.type()) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static void checkNode(final Node node, final Node parent, final List<Token> tokens, final String sql, final Violations out) {
        final int start = node.start();
        final int stop = node.stop();
        // R1 界内
        if (start < 0 || stop < start || stop >= sql.length()) {
            out.add("R1", node, start, stop, "span 越界");
            return;
        }
        // R2 token 对齐：start 命中某 token 起点、stop 命中某（不早于该 token 的）token 终点
        final Integer startIdx = indexOfStart(tokens, start);
        final Integer stopIdx = indexOfStop(tokens, stop);
        if (null == startIdx || null == stopIdx || startIdx > stopIdx) {
            out.add("R2", node, start, stop, "span 未对齐 token 边界");
        }
        // R3 包含
        if (null != parent && (start < parent.start() || stop > parent.stop())) {
            out.add("R3", node, start, stop, "超出父节点 [" + parent.start() + "," + parent.stop() + "] "
                    + parent.getClass().getSimpleName());
        }
        // R4 兄弟不重叠
        for (final Node sibling : SpanWalker.children(parent)) {
            if (sibling != node && sibling.start() <= stop && start <= sibling.stop()) {
                out.add("R4", node, start, stop, "与兄弟 [" + sibling.start() + "," + sibling.stop() + "] "
                        + sibling.getClass().getSimpleName() + " 重叠");
            }
        }
        // R5 根对齐
        if (null == parent && !tokens.isEmpty()
                && (start != tokens.get(0).start() || stop != tokens.get(tokens.size() - 1).stop())) {
            out.add("R5", node, start, stop, "根 span 未覆盖 [首 token, 末 token] [" + tokens.get(0).start()
                    + "," + tokens.get(tokens.size() - 1).stop() + "]");
        }
        // R6 叶子原文
        final String slice = sql.substring(start, stop + 1);
        if (node instanceof Literal && !slice.equals(((Literal) node).text())) {
            out.add("R6", node, start, stop, "Literal 存文与切片不符: '" + ((Literal) node).text() + "'");
        } else if (node instanceof Star) {
            final Star star = (Star) node;
            // 与 ColumnRef 同理：限定名段间空白合法且存文按原文拼接（含空白），两侧去空白比较
            final String expected = null == star.qualifier() ? "*" : star.qualifier() + ".*";
            if (!dottedSlice(slice).equals(dottedSlice(expected))) {
                out.add("R6", node, start, stop, "Star 切片异常: '" + slice + "'");
            }
        } else if (node instanceof ParameterMarker && !"?".equals(slice)) {
            out.add("R6", node, start, stop, "参数标记切片异常: '" + slice + "'");
        } else if (node instanceof ColumnRef) {
            final ColumnRef ref = (ColumnRef) node;
            final String expected = null == ref.qualifier() ? ref.name() : ref.qualifier() + "." + ref.name();
            // 限定名段间允许空白（o . id 合法 SQL，且解析器按原文拼 qualifier——存文同样带空白）：
            // 带点形态两侧去空白比较（F3 变异体暴露的公式校准），无点形态仍精确相等
            final boolean mismatch = slice.contains(".")
                    ? !dottedSlice(slice).equals(dottedSlice(expected)) : !slice.equals(expected);
            if (mismatch) {
                out.add("R6", node, start, stop, "ColumnRef 切片 '" + slice + "' ≠ 存文 '" + expected + "'");
            }
        } else if (node instanceof FunctionCall && ')' != sql.charAt(stop)) {
            out.add("R6", node, start, stop, "FunctionCall 终点非 ')': '" + sql.charAt(stop) + "'");
        }
    }

    /** 限定名切片去空白（段间空白合法，如 {@code o . id}；标识符内部不可能有空白）。 */
    private static String dottedSlice(final String slice) {
        return slice.replaceAll("\\s+", "");
    }

    private static Integer indexOfStart(final List<Token> tokens, final int offset) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).start() == offset) {
                return i;
            }
        }
        return null;
    }

    private static Integer indexOfStop(final List<Token> tokens, final int offset) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).stop() == offset) {
                return i;
            }
        }
        return null;
    }

    /** 违规累积器：拼装 规则号/类型/span/现场。 */
    private static final class Violations {

        private final String sql;

        private final List<String> list = new ArrayList<String>();

        private Violations(final String sql) {
            this.sql = sql;
        }

        private void add(final String rule, final Node node, final int start, final int stop, final String detail) {
            final String context = sql.substring(Math.max(0, start - 12), Math.min(sql.length(), stop + 1 + 8));
            list.add(rule + " " + node.getClass().getSimpleName() + "[" + start + "," + stop + "] " + detail
                    + " | 现场: ..." + context.replace("\n", "\\n") + "...");
        }
    }
}
