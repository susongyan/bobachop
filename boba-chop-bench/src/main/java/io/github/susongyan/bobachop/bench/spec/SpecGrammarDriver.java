package io.github.susongyan.bobachop.bench.spec;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * 规格语法执行器驱动（F5 陪练侧统一入口）：词法 + 语法全走查，零语法错误即接受。
 *
 * <p>每次调用新建 lexer/parser——与 {@code BobaChop.parse} 同为逐次调用口径，陪练不作
 * 对象池化（陪练成绩宁可保守，比值只会更严）。词法错误与语法错误同样计拒；错误监听
 * 收集计数而非打印，任一错误即整条拒绝（规格语法不产出错误树，与内核三支分类学
 * 同构的"拒"面）。</p>
 */
public final class SpecGrammarDriver {

    /** 单条 SQL 是否被规格语法接受（词法 + 语法零错误走完 root）。 */
    public static boolean accepts(final String sql) {
        final ErrorCount listener = new ErrorCount();
        final BobaChopSpecLexer lexer = new BobaChopSpecLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final BobaChopSpecParser parser = new BobaChopSpecParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        parser.root();
        return 0 == listener.errors;
    }

    private static final class ErrorCount extends BaseErrorListener {
        private int errors;

        @Override
        public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
                                final int line, final int charPositionInLine, final String msg,
                                final RecognitionException cause) {
            errors++;
        }
    }

    private SpecGrammarDriver() {
    }
}
