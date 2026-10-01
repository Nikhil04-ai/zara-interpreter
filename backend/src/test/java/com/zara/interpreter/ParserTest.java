package com.zara.interpreter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Tests Parser's contract directly: what it builds (instruction types/counts),
// what it throws on malformed input, and what it recovers from (warnings).
// Deep nested-block *behavior* (the indentation-depth bug class) is verified
// through actual execution in InterpreterTest instead, since Instruction
// subclasses expose no introspection into their bodies -- running the program
// is the more meaningful signal there anyway.
class ParserTest {

    private Parser parserFor(String source) {
        Parser parser = new Parser(new Tokenizer(source).tokenize());
        parser.setSource(source);
        return parser;
    }

    @Test
    @DisplayName("Each top-level statement kind parses to its matching Instruction type")
    void topLevelStatementTypes() {
        String source = "set x = 1\n"
                + "show x\n"
                + "when x == 1:\n"
                + "    show \"one\"\n"
                + "loop 2:\n"
                + "    show \"hi\"\n"
                + "while x > 0:\n"
                + "    set x = x - 1\n";

        List<Instruction> instructions = parserFor(source).parse();

        assertThat(instructions).hasSize(5);
        assertThat(instructions.get(0)).isInstanceOf(AssignInstruction.class);
        assertThat(instructions.get(1)).isInstanceOf(PrintInstruction.class);
        assertThat(instructions.get(2)).isInstanceOf(IfInstruction.class);
        assertThat(instructions.get(3)).isInstanceOf(RepeatInstruction.class);
        assertThat(instructions.get(4)).isInstanceOf(WhileInstruction.class);
    }

    @Test
    @DisplayName("'otherwise:' attaches to the same when, it does not become a separate top-level statement")
    void otherwiseAttachesToSameIf() {
        String source = "when 1 > 2:\n"
                + "    show \"a\"\n"
                + "otherwise:\n"
                + "    show \"b\"\n";

        List<Instruction> instructions = parserFor(source).parse();

        assertThat(instructions).hasSize(1);
        assertThat(instructions.get(0)).isInstanceOf(IfInstruction.class);
    }

    @Test
    @DisplayName("An 'otherwise when ...' chain also collapses into a single top-level if")
    void otherwiseWhenChainIsOneStatement() {
        String source = "when 1 > 2:\n"
                + "    show \"a\"\n"
                + "otherwise when 2 > 3:\n"
                + "    show \"b\"\n"
                + "otherwise:\n"
                + "    show \"c\"\n";

        List<Instruction> instructions = parserFor(source).parse();

        assertThat(instructions).hasSize(1);
        assertThat(instructions.get(0)).isInstanceOf(IfInstruction.class);
    }

    @Test
    @DisplayName("A missing colon after a when-condition is a syntax error")
    void missingColonIsSyntaxError() {
        assertThatThrownBy(() -> parserFor("when 1 > 0\n    show 1\n").parse())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Syntax error");
    }

    @Test
    @DisplayName("A dangling operator at end of expression is rejected, not silently truncated")
    void danglingOperatorIsIncompleteExpression() {
        assertThatThrownBy(() -> parserFor("set x = 5 +\n").parse())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Incomplete expression at line 1");
    }

    @Test
    @DisplayName("'loop' requires a literal number, not an expression or variable")
    void loopRequiresLiteralNumber() {
        assertThatThrownBy(() -> parserFor("set n = 3\nloop n:\n    show \"hi\"\n").parse())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expected NUMBER");
    }

    @Test
    @DisplayName("An unrecognized statement is skipped and recorded as a warning, not fatal")
    void unexpectedStatementRecoversWithWarning() {
        Parser parser = parserFor("FOO BAR\nshow 1\n");

        List<Instruction> instructions = parser.parse();

        assertThat(parser.getWarnings()).hasSize(1);
        assertThat(parser.getWarnings().get(0)).contains("FOO").contains("line 1");
        assertThat(instructions).hasSize(1);
        assertThat(instructions.get(0)).isInstanceOf(PrintInstruction.class);
    }

    @Test
    @DisplayName("A program with no recognizable statements produces no warnings when it's simply empty")
    void emptyProgramHasNoWarnings() {
        Parser parser = parserFor("# just a comment\n\n");
        List<Instruction> instructions = parser.parse();

        assertThat(instructions).isEmpty();
        assertThat(parser.getWarnings()).isEmpty();
    }
}
