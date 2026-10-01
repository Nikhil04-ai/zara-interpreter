package com.zara.interpreter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// End-to-end interpreter behavior tests, run directly (no subprocess/CLI),
// so they're fast and CI-friendly. Ports the manually-verified cases from the
// Phase 1/2 testing sessions into a permanent, repo-tracked suite -- the
// shell-script versions only ever lived in a scratchpad, never in git or CI.
// Expected `output` strings are reconstructed with exact trailing newlines
// (every env.print() line appends its own '\n'); the original shell
// comparisons used bash's $(...), which silently strips trailing newlines,
// so those expected values aren't directly reusable here as-is.
class InterpreterTest {

    private ExecutionResult run(String code) {
        return new Interpreter().run(code);
    }

    @Nested
    @DisplayName("Pre-existing bug fixes (regression)")
    class BugFixes {

        @Test
        void divisionByZero() {
            ExecutionResult r = run("set x = 5 / 0");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Division by zero");
        }

        @Test
        void unterminatedString() {
            ExecutionResult r = run("show \"hello");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Unterminated string at line 1");
        }

        @Test
        void invalidNumberFormat() {
            ExecutionResult r = run("set x = 1.2.3");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Invalid number format at line 1");
        }

        @Test
        void loopCountTooLarge() {
            ExecutionResult r = run("loop 100001:\n    show \"hi\"\n");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Loop count too large");
        }

        @Test
        void loopCountMustBeInteger() {
            ExecutionResult r = run("loop 2.5:\n    show \"hi\"\n");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Loop count must be an integer at line 1");
        }

        @Test
        void reservedVariableNameNull() {
            ExecutionResult r = run("set null = 5");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Invalid variable name: null");
        }

        @Test
        void undefinedVariable() {
            ExecutionResult r = run("show x");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo(
                    "Variable not defined: \"x\". Did you forget to write   set x = ...   first?");
        }

        @Test
        void nonBooleanConditionFormattedWithoutTrailingZero() {
            ExecutionResult r = run("when 5:\n    show \"no\"\n");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Condition must evaluate to boolean but got: 5");
        }

        @Test
        void incompleteExpression() {
            ExecutionResult r = run("set x = 5 +");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Incomplete expression at line 1");
        }

        @Test
        void invalidCharacter() {
            ExecutionResult r = run("set x = 5 @ 3");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Invalid character '@' at line 1");
        }

        @Test
        void stringPlusNumberFormatsWithoutTrailingZero() {
            ExecutionResult r = run("show \"Score: \" + 85");
            assertThat(r.getOutput()).isEqualTo("Score: 85\n");
            assertThat(r.getError()).isNull();
        }

        @Test
        void wholeNumberDivisionPrintsWithoutDecimal() {
            ExecutionResult r = run("show 20 / 4");
            assertThat(r.getOutput()).isEqualTo("5\n");
        }

        @Test
        void nonWholeNumberDivisionPrintsDecimal() {
            ExecutionResult r = run("show 10 / 4");
            assertThat(r.getOutput()).isEqualTo("2.5\n");
        }

        @Test
        void unaryMinus() {
            ExecutionResult r = run("show -5 + 3");
            assertThat(r.getOutput()).isEqualTo("-2\n");
        }

        @Test
        void doubleUnaryMinus() {
            ExecutionResult r = run("show - -5");
            assertThat(r.getOutput()).isEqualTo("5\n");
        }
    }

    @Nested
    @DisplayName("Expression precedence")
    class Precedence {

        @Test
        void comparisonBindsLooserThanPlus() {
            ExecutionResult r = run("when 1 + 2 > 2:\n    show \"precedence correct\"\n");
            assertThat(r.getOutput()).isEqualTo("precedence correct\n");
        }

        @Test
        void multiplicationBindsTighterThanPlus() {
            ExecutionResult r = run("show 2 + 3 * 4");
            assertThat(r.getOutput()).isEqualTo("14\n");
        }

        @Test
        void mixedArithmeticAndComparisonChain() {
            ExecutionResult r = run("set a = 10\nwhen a - 5 > 3 + 1:\n    show \"yes\"\notherwise:\n    show \"no\"\n");
            assertThat(r.getOutput()).isEqualTo("yes\n");
        }

        @Test
        void andOrNotLayerCorrectlyAroundComparisons() {
            ExecutionResult r = run("when 1 + 1 == 2 and 3 * 2 == 6:\n    show \"layered ok\"\n");
            assertThat(r.getOutput()).isEqualTo("layered ok\n");
        }
    }

    @Nested
    @DisplayName("Parentheses")
    class Parens {

        @Test
        void basicGroupingOverridesPrecedence() {
            ExecutionResult r = run("show (2 + 3) * 4");
            assertThat(r.getOutput()).isEqualTo("20\n");
        }

        @Test
        void nestedGrouping() {
            ExecutionResult r = run("show ((1 + 2) * (3 + 4))");
            assertThat(r.getOutput()).isEqualTo("21\n");
        }

        @Test
        void groupingInsideCondition() {
            ExecutionResult r = run("when (1 + 1) == 2:\n    show \"grouped condition ok\"\n");
            assertThat(r.getOutput()).isEqualTo("grouped condition ok\n");
        }

        @Test
        void deeplyNestedFiveLevels() {
            ExecutionResult r = run("show (((((1 + 2)))))");
            assertThat(r.getOutput()).isEqualTo("3\n");
        }
    }

    @Nested
    @DisplayName("otherwise / otherwise when")
    class Conditional {

        @Test
        void elseBranchRunsWhenConditionFalse() {
            ExecutionResult r = run("set score = 30\nwhen score > 50:\n    show \"pass\"\notherwise:\n    show \"fail\"\n");
            assertThat(r.getOutput()).isEqualTo("fail\n");
        }

        @Test
        void thenBranchRunsElseSkipped() {
            ExecutionResult r = run("set score = 90\nwhen score > 50:\n    show \"pass\"\notherwise:\n    show \"fail\"\n");
            assertThat(r.getOutput()).isEqualTo("pass\n");
        }

        @Test
        void elseIfMiddleBranchTaken() {
            String code = "set grade = 70\nwhen grade > 90:\n    show \"A\"\notherwise when grade > 60:\n    show \"B\"\notherwise:\n    show \"C\"\n";
            assertThat(run(code).getOutput()).isEqualTo("B\n");
        }

        @Test
        void chainFallsThroughToFinalOtherwise() {
            String code = "set grade = 10\nwhen grade > 90:\n    show \"A\"\notherwise when grade > 60:\n    show \"B\"\notherwise:\n    show \"C\"\n";
            assertThat(run(code).getOutput()).isEqualTo("C\n");
        }

        @Test
        void fourBranchChainThirdBranchTaken() {
            String code = "set n = 3\nwhen n == 1:\n    show \"one\"\notherwise when n == 2:\n    show \"two\"\notherwise when n == 3:\n    show \"three\"\notherwise:\n    show \"many\"\n";
            assertThat(run(code).getOutput()).isEqualTo("three\n");
        }
    }

    @Nested
    @DisplayName("loop (fixed count)")
    class Loop {

        @Test
        void zeroCountRunsBodyZeroTimes() {
            ExecutionResult r = run("loop 0:\n    show \"never\"\nshow \"after\"\n");
            assertThat(r.getOutput()).isEqualTo("after\n");
        }

        @Test
        void accumulatesSumCorrectly() {
            String code = "set sum = 0\nset i = 0\nloop 1000:\n    set sum = sum + i\n    set i = i + 1\nshow sum\n";
            assertThat(run(code).getOutput()).isEqualTo("499500\n");
        }
    }

    @Nested
    @DisplayName("while")
    class While {

        @Test
        void countsUpCorrectly() {
            ExecutionResult r = run("set i = 0\nwhile i < 3:\n    show i\n    set i = i + 1\n");
            assertThat(r.getOutput()).isEqualTo("0\n1\n2\n");
        }

        @Test
        void bodyNeverRunsWhenConditionStartsFalse() {
            ExecutionResult r = run("set i = 5\nwhile i < 3:\n    show i\nshow \"after\"\n");
            assertThat(r.getOutput()).isEqualTo("after\n");
        }

        @Test
        void complexConditionCombiningAnd() {
            String code = "set i = 0\nset j = 10\nwhile i < 5 and j > 0:\n    set i = i + 1\n    set j = j - 1\nshow i\nshow j\n";
            assertThat(run(code).getOutput()).isEqualTo("5\n5\n");
        }
    }

    @Nested
    @DisplayName("Comparison operators")
    class Comparison {

        @Test
        void notEqualsTrueCase() {
            assertThat(run("when 5 != 4:\n    show \"neq ok\"\n").getOutput()).isEqualTo("neq ok\n");
        }

        @Test
        void notEqualsFalseCaseTakesElseBranch() {
            String code = "when 5 != 5:\n    show \"should not print\"\notherwise:\n    show \"neq false ok\"\n";
            assertThat(run(code).getOutput()).isEqualTo("neq false ok\n");
        }

        @Test
        void greaterOrEqualBoundary() {
            assertThat(run("when 5 >= 5:\n    show \"ge ok\"\n").getOutput()).isEqualTo("ge ok\n");
        }

        @Test
        void lessOrEqualBoundary() {
            assertThat(run("when 4 <= 4:\n    show \"le ok\"\n").getOutput()).isEqualTo("le ok\n");
        }

        @Test
        void stringEqualityIsExactNotCoercedToNumber() {
            String code = "set x = \"5\"\nwhen x == 5:\n    show \"eq\"\notherwise:\n    show \"neq\"\n";
            assertThat(run(code).getOutput()).isEqualTo("neq\n");
        }

        @Test
        void stringEqualityMatchesIdenticalStrings() {
            String code = "set a = \"hello\"\nset b = \"hello\"\nwhen a == b:\n    show \"match\"\n";
            assertThat(run(code).getOutput()).isEqualTo("match\n");
        }
    }

    @Nested
    @DisplayName("and / or / not")
    class Logic {

        @Test
        void andBothTrue() {
            assertThat(run("when (5 > 2) and (3 > 1):\n    show \"and ok\"\n").getOutput()).isEqualTo("and ok\n");
        }

        @Test
        void andShortCircuitsSkipsEvaluatingRight() {
            String code = "when (1 > 5) and (1 / 0 > 0):\n    show \"should not print\"\notherwise:\n    show \"and short-circuit ok\"\n";
            ExecutionResult r = run(code);
            assertThat(r.getOutput()).isEqualTo("and short-circuit ok\n");
            assertThat(r.getError()).isNull();
        }

        @Test
        void orShortCircuitsSkipsEvaluatingRight() {
            String code = "when (5 > 1) or (1 / 0 > 0):\n    show \"or short-circuit ok\"\n";
            ExecutionResult r = run(code);
            assertThat(r.getOutput()).isEqualTo("or short-circuit ok\n");
            assertThat(r.getError()).isNull();
        }

        @Test
        void notNegatesABoolean() {
            assertThat(run("when not (1 > 5):\n    show \"not ok\"\n").getOutput()).isEqualTo("not ok\n");
        }

        @Test
        void andRejectsANonBooleanOperand() {
            ExecutionResult r = run("when 5 and (1 > 0):\n    show \"should not print\"\n");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Expected a boolean for 'and' but got: 5");
        }
    }

    @Nested
    @DisplayName("Tokenizer edge cases, through full execution")
    class TokenizerEdgeCases {

        @Test
        void inlineCommentAfterCodeIsStripped() {
            assertThat(run("show 5 # this is a comment\nshow 6\n").getOutput()).isEqualTo("5\n6\n");
        }

        @Test
        void multipleConsecutiveBlankLinesAreIgnored() {
            assertThat(run("set x = 1\n\n\nshow x\n").getOutput()).isEqualTo("1\n");
        }

        @Test
        void tabIndentedBlockWorksSameAsSpaceIndented() {
            assertThat(run("when 1 == 1:\n\tshow \"tab indent ok\"\n").getOutput()).isEqualTo("tab indent ok\n");
        }

        @Test
        void identifierStartingWithKeywordLikePrefixIsNotMisread() {
            assertThat(run("set loopValue = 5\nshow loopValue\n").getOutput()).isEqualTo("5\n");
        }

        @Test
        void underscoreIdentifiersWork() {
            assertThat(run("set my_var_1 = 7\nshow my_var_1\n").getOutput()).isEqualTo("7\n");
        }

        @Test
        void leadingZeroDecimalParsesCorrectly() {
            assertThat(run("show 0.5 + 0.25").getOutput()).isEqualTo("0.75\n");
        }

        @Test
        void emptyStringPrintsAsASingleBlankLine() {
            assertThat(run("show \"\"").getOutput()).isEqualTo("\n");
        }
    }

    @Nested
    @DisplayName("Parser recovery")
    class ParserRecovery {

        @Test
        void uppercaseKeywordSurfacesAsWarningBeforeOutput() {
            ExecutionResult r = run("SET x = 5\nshow x\n");
            assertThat(r.getOutput()).isEqualTo("Warning: Unexpected token at line 1: 'SET'\n");
            assertThat(r.getError()).isEqualTo(
                    "Variable not defined: \"x\". Did you forget to write   set x = ...   first?");
        }

        @Test
        void recoversAfterBadStatementWarningsSurfaceBeforeOutput() {
            ExecutionResult r = run("show \"before\"\nFOO BAR BAZ\nshow \"after\"\n");
            assertThat(r.getOutput()).isEqualTo("Warning: Unexpected token at line 2: 'FOO'\nbefore\nafter\n");
            assertThat(r.getError()).isNull();
        }

        @Test
        void invalidCharacterAbortsWholeRunNoPartialRecovery() {
            ExecutionResult r = run("show \"before\"\n@@@\nshow \"after\"\n");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Invalid character '@' at line 2");
        }

        @Test
        void loopCountMustBeLiteralNumberNotVariable() {
            ExecutionResult r = run("set n = 3\nloop n:\n    show \"hi\"\n");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Syntax error at line 2: expected NUMBER but found 'n' (IDENTIFIER)");
        }

        @Test
        void emptyProgramProducesNoOutputAndNoError() {
            ExecutionResult r = run("# just a comment\n\n");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isNull();
        }
    }

    @Nested
    @DisplayName("Type coercion / cross-type errors")
    class Types {

        @Test
        void addingBooleanToNumberIsACleanTypeError() {
            ExecutionResult r = run("show (1 > 0) + 5");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Expected a number but got: \"true\" (Boolean)");
        }

        @Test
        void subtractingStringsIsACleanTypeError() {
            ExecutionResult r = run("show \"a\" - \"b\"");
            assertThat(r.getOutput()).isEmpty();
            assertThat(r.getError()).isEqualTo("Expected a number but got: \"a\" (String)");
        }

        @Test
        void chainedStringConcatenationAcrossThreeValues() {
            assertThat(run("show \"a\" + \"b\" + \"c\"").getOutput()).isEqualTo("abc\n");
        }

        @Test
        void reassigningAVariableOverwritesTheOldValue() {
            assertThat(run("set x = 1\nshow x\nset x = 2\nshow x\n").getOutput()).isEqualTo("1\n2\n");
        }
    }

    @Nested
    @DisplayName("Nested control flow")
    class Nesting {

        @Test
        void nestedLoopTimesLoopRunsInnerBodyOuterTimesInnerTimes() {
            ExecutionResult r = run("loop 2:\n    loop 3:\n        show \"x\"\n");
            assertThat(r.getOutput()).isEqualTo("x\nx\nx\nx\nx\nx\n");
        }

        @Test
        void whileNestedInsideLoop() {
            String code = "loop 2:\n    set i = 0\n    while i < 2:\n        show i\n        set i = i + 1\n";
            assertThat(run(code).getOutput()).isEqualTo("0\n1\n0\n1\n");
        }

        @Test
        void whenNestedInsideWhile() {
            String code = "set i = 0\nwhile i < 4:\n    when i == 2:\n        show \"found two\"\n    set i = i + 1\n";
            assertThat(run(code).getOutput()).isEqualTo("found two\n");
        }

        @Test
        @DisplayName("REGRESSION: when/otherwise inside a loop, followed by a sibling statement at the loop's own depth")
        void whenOtherwiseInsideLoopFollowedBySiblingAtLoopDepth() {
            // This is the exact pattern that caused a genuine infinite loop before the
            // indentation-depth fix: the loop's own increment statement ("set i = i + 1")
            // was wrongly absorbed into the "when"/"otherwise" block instead of staying a
            // sibling of it, so it only ran conditionally and the loop variable never
            // advanced. See Parser.java's parseBlock() and PROGRESS.md Phase 1.
            String code = "set i = 0\nloop 3:\n    when i == 1:\n        show \"one\"\n    otherwise:\n        show \"other\"\n    show \"always\"\n    set i = i + 1\n";
            assertThat(run(code).getOutput()).isEqualTo("other\nalways\none\nalways\nother\nalways\n");
        }
    }

    @Nested
    @DisplayName("Safety: catastrophic input")
    class Safety {

        @Test
        @DisplayName("Deeply nested parentheses return a clean error instead of crashing with a raw StackOverflowError")
        void deeplyNestedParenthesesReturnCleanError() {
            String nested = "(".repeat(20_000) + "1" + ")".repeat(20_000);
            ExecutionResult r = run("show " + nested);
            assertThat(r.getError()).isEqualTo("Program is nested too deeply to evaluate");
        }
    }
}
