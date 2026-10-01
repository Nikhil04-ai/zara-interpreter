package com.zara.interpreter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenizerTest {

    private List<Token> tokenize(String source) {
        return new Tokenizer(source).tokenize();
    }

    private List<TokenType> typesOf(List<Token> tokens) {
        return tokens.stream().map(Token::getType).toList();
    }

    @Test
    @DisplayName("Keywords tokenize to their dedicated types, not IDENTIFIER")
    void keywords() {
        List<Token> tokens = tokenize("set show when otherwise loop while and or not");
        assertThat(typesOf(tokens)).containsExactly(
                TokenType.SET, TokenType.SHOW, TokenType.WHEN, TokenType.OTHERWISE,
                TokenType.LOOP, TokenType.WHILE, TokenType.AND, TokenType.OR, TokenType.NOT,
                TokenType.EOF);
    }

    @Test
    @DisplayName("An identifier starting with a keyword-like prefix is read as one whole word, not split")
    void keywordLikePrefixIsNotMisread() {
        List<Token> tokens = tokenize("loopValue");
        assertThat(tokens.get(0).getType()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(tokens.get(0).getValue()).isEqualTo("loopValue");
    }

    @Test
    @DisplayName("Underscore identifiers are read correctly")
    void underscoreIdentifiers() {
        List<Token> tokens = tokenize("my_var_1");
        assertThat(tokens.get(0).getType()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(tokens.get(0).getValue()).isEqualTo("my_var_1");
    }

    @Test
    @DisplayName("Numbers, including leading-zero decimals, tokenize with their exact text")
    void numbers() {
        List<Token> tokens = tokenize("10 3.5 0.25");
        assertThat(tokens.get(0).getValue()).isEqualTo("10");
        assertThat(tokens.get(1).getValue()).isEqualTo("3.5");
        assertThat(tokens.get(2).getValue()).isEqualTo("0.25");
    }

    @Test
    @DisplayName("A number with two decimal points is rejected")
    void invalidNumberFormatThrows() {
        assertThatThrownBy(() -> tokenize("1.2.3"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid number format at line 1");
    }

    @Test
    @DisplayName("Strings tokenize with their contents, quotes stripped")
    void strings() {
        List<Token> tokens = tokenize("\"hello world\"");
        assertThat(tokens.get(0).getType()).isEqualTo(TokenType.STRING);
        assertThat(tokens.get(0).getValue()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("A string missing its closing quote is rejected")
    void unterminatedStringThrows() {
        assertThatThrownBy(() -> tokenize("\"hello"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unterminated string at line 1");
    }

    @Test
    @DisplayName("Comments run to end of line and are not tokenized")
    void commentsAreStripped() {
        List<Token> tokens = tokenize("show 5 # this is a comment\nshow 6");
        assertThat(tokens.stream().map(Token::getValue))
                .doesNotContain("#", "this", "is", "a", "comment");
        assertThat(typesOf(tokens)).containsExactly(
                TokenType.SHOW, TokenType.NUMBER, TokenType.NEWLINE,
                TokenType.SHOW, TokenType.NUMBER, TokenType.EOF);
    }

    @Test
    @DisplayName("All comparison, arithmetic, and grouping operators tokenize to distinct types")
    void operators() {
        List<Token> tokens = tokenize("+ - * / > < >= <= == != ( )");
        assertThat(typesOf(tokens)).containsExactly(
                TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH,
                TokenType.GREATER, TokenType.LESS, TokenType.GE, TokenType.LE,
                TokenType.EQEQ, TokenType.NOTEQ, TokenType.LPAREN, TokenType.RPAREN,
                TokenType.EOF);
    }

    @Test
    @DisplayName("'=' and '==' are tokenized distinctly, not greedily merged wrong")
    void equalsVersusEqEq() {
        List<Token> tokens = tokenize("= ==");
        assertThat(typesOf(tokens)).containsExactly(TokenType.EQUALS, TokenType.EQEQ, TokenType.EOF);
    }

    @Test
    @DisplayName("An unrecognized character is rejected with its line number")
    void invalidCharacterThrows() {
        assertThatThrownBy(() -> tokenize("set x = 5 @ 3"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid character '@' at line 1");
    }

    @Test
    @DisplayName("A bare '!' (not followed by '=') is rejected rather than silently accepted")
    void bareExclamationThrows() {
        assertThatThrownBy(() -> tokenize("set x = 1 ! 2"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("!=");
    }

    @Test
    @DisplayName("Windows-style \\r\\n line endings don't break tokenizing")
    void windowsLineEndingsAreIgnored() {
        List<Token> tokens = tokenize("set x = 1\r\nshow x");
        assertThat(typesOf(tokens)).containsExactly(
                TokenType.SET, TokenType.IDENTIFIER, TokenType.EQUALS, TokenType.NUMBER,
                TokenType.NEWLINE, TokenType.SHOW, TokenType.IDENTIFIER, TokenType.EOF);
    }

    @Test
    @DisplayName("Every token (except EOF) carries the correct 1-based source line number")
    void lineNumbersAreTracked() {
        List<Token> tokens = tokenize("set x = 1\nshow x");
        assertThat(tokens.get(0).getLine()).isEqualTo(1); // set
        assertThat(tokens.get(5).getLine()).isEqualTo(2); // show (after NEWLINE at index 4)
    }
}
