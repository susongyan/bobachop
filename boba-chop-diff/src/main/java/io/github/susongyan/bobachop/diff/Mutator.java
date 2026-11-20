package io.github.susongyan.bobachop.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.susongyan.bobachop.dialect.DialectProfile;
import io.github.susongyan.bobachop.lexer.Lexer;
import io.github.susongyan.bobachop.lexer.Token;
import io.github.susongyan.bobachop.lexer.TokenType;

/**
 * 对拍体系 v2 F3——变异器：对合法语句做 token 级变异，制造敌意/残缺/跨界输入。
 *
 * <p><b>token 级而非字符级</b>：变异以完整 token 为单位（删/复制/邻位交换/替换/截断），
 * 保证变异产物仍是可词法扫描的文本（字符串/注释等原子 token 不会被劈开），变异空间
 * 聚焦在<b>语法结构</b>上——F3 关心的是"残缺输入的三支异常封闭性"与"被接受输入的
 * 元性质"，不是词法健壮性（词法层有独立黄金门禁）。产物以单空格拼接（保 token 边界；
 * 邻接可能重组出新 token——如 PG 的 {@code E 'x'}，恰属变异空间）。</p>
 *
 * <p><b>替换池偏结构词</b>：括号/逗号/分号/子句锚词/参数标记——标识符→标识符的变异
 * 无信息量，结构词变异才制造"接受面边界上的输入"。种子可复现（Random 注入）。</p>
 */
public final class Mutator {

    /** 替换池：结构 token 优先（制造接受面边界输入）。 */
    private static final String[] POOL = {
        "(", ")", ",", ";", "?", "*", "=", "SELECT", "FROM", "WHERE", "AND", "OR", "NOT",
        "ON", "USING", "JOIN", "LEFT", "GROUP", "ORDER", "BY", "HAVING", "LIMIT", "OFFSET",
        "FETCH", "VALUES", "INSERT", "UPDATE", "DELETE", "SET", "AS", "IN", "EXISTS",
        "CASE", "WHEN", "THEN", "ELSE", "END", "0", "'x'", "abc"};

    private static final int OPS = 5;

    private final Random random;

    public Mutator(final Random random) {
        this.random = random;
    }

    /** 对合法语句做 {@code operations} 次 token 级变异，返回变异后文本（token 间单空格）。 */
    public String mutate(final String sql, final DialectProfile profile, final int operations) {
        final List<Token> tokens = Lexer.lex(sql, profile.lexerConfig());
        final List<String> texts = new ArrayList<String>(tokens.size());
        for (final Token token : tokens) {
            if (TokenType.EOF != token.type()) {
                texts.add(token.text());
            }
        }
        for (int i = 0; i < operations && !texts.isEmpty(); i++) {
            apply(texts);
        }
        final StringBuilder out = new StringBuilder(sql.length() + 16);
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(texts.get(i));
        }
        return out.toString();
    }

    private void apply(final List<String> texts) {
        final int op = random.nextInt(OPS);
        final int i = random.nextInt(texts.size());
        if (0 == op) {
            texts.remove(i);
        } else if (1 == op) {
            texts.add(i, texts.get(i));
        } else if (2 == op && texts.size() > 1) {
            final int j = texts.size() - 1 == i ? i - 1 : i + 1;
            texts.set(i, texts.set(j, texts.get(i)));
        } else if (3 == op) {
            texts.set(i, POOL[random.nextInt(POOL.length)]);
        } else {
            texts.subList(i, texts.size()).clear();
        }
    }
}
