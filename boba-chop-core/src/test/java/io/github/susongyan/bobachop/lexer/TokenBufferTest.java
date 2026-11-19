package io.github.susongyan.bobachop.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@link TokenBuffer} 无回溯游标语义：peek 只读任意远、next 单向、EOF 幂等。
 */
class TokenBufferTest {

    @Test
    void nextWalksTokensInOrderUntilEof() {
        final TokenBuffer buffer = new TokenBuffer("SELECT id FROM t");
        assertSame(Kw.SELECT, buffer.next().kw());
        assertEquals(TokenType.IDENTIFIER, buffer.next().type());
        assertSame(Kw.FROM, buffer.next().kw());
        assertEquals(TokenType.IDENTIFIER, buffer.next().type());
        assertEquals(TokenType.EOF, buffer.next().type());
    }

    @Test
    void repeatedNextAtEofReturnsEofForever() {
        final TokenBuffer buffer = new TokenBuffer("a");
        buffer.next();
        for (int i = 0; i < 3; i++) {
            assertEquals(TokenType.EOF, buffer.next().type());
            assertEquals(1, buffer.consumed());
        }
        assertTrue(buffer.eof());
    }

    @Test
    void peekDoesNotAdvanceCursor() {
        final TokenBuffer buffer = new TokenBuffer("SELECT 1");
        assertEquals(Kw.SELECT, buffer.peek(0).kw());
        assertEquals(Kw.SELECT, buffer.peek(0).kw());
        assertEquals(0, buffer.consumed());
        // next 消费后 peek 视角同步前移
        buffer.next();
        assertEquals(TokenType.NUMBER, buffer.peek(0).type());
    }

    @Test
    void peekFarBeyondEndReturnsEofSentinel() {
        final TokenBuffer buffer = new TokenBuffer("a b");
        final Token eof = buffer.peek(100);
        assertEquals(TokenType.EOF, eof.type());
        // 任意远恒同一 EOF 哨兵，且不影响游标
        assertSame(eof, buffer.peek(1000));
        assertEquals(TokenType.IDENTIFIER, buffer.next().type());
    }

    @Test
    void peekNegativeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBuffer("a").peek(-1));
    }

    @Test
    void eofSentinelGuardsStatementTailLookahead() {
        // 语法器在语句尾安全 peek(1) 的看门条件
        final TokenBuffer buffer = new TokenBuffer("SELECT 1");
        buffer.next();
        buffer.next();
        assertEquals(TokenType.EOF, buffer.peek(0).type());
        assertEquals(TokenType.EOF, buffer.peek(1).type());
    }

    @Test
    void constructorRequiresEofSentinel() {
        final List<Token> noEof = Lexer.lex("a");
        assertThrows(IllegalArgumentException.class, () -> new TokenBuffer(noEof.subList(0, noEof.size() - 1)));
        assertFalse(new TokenBuffer("a").tokens().isEmpty());
    }
}
