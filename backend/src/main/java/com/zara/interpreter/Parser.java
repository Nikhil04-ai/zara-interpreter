package com.zara.interpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Parser {

    private final List<Token> tokens; // list of tokens from lexer
    private int current; // pointer to current token
    private Map<Integer, Integer> lineIndent; // line number -> indentation width (0 if unindented)
    private final List<String> warnings = new ArrayList<>(); // recoverable parse issues (unexpected tokens)

    // Initialize parser with token list
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.current = 0;
        this.lineIndent = Map.of();
    }

    // Non-fatal issues collected during parsing (e.g. skipped unexpected-token lines).
    // Never written to System.err directly -- the caller decides how to surface them,
    // since this parser has no idea whether it's running in a CLI or a web request.
    public List<String> getWarnings() {
        return warnings;
    }

    // Preprocess source to measure each line's indentation width, so nested blocks
    // can tell a sibling statement (same depth) apart from a dedent back to an
    // enclosing block (shallower depth) -- both are merely "indented" in isolation.
    public void setSource(String source) {
        String[] lines = source.split("\n", -1);
        Map<Integer, Integer> indent = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int width = 0;
            while (width < line.length() && (line.charAt(width) == ' ' || line.charAt(width) == '\t')) {
                width++;
            }
            if (width > 0) {
                indent.put(i + 1, width); // 1-based line numbers
            }
        }

        this.lineIndent = indent;
    }

    private int indentOf(int line) {
        return lineIndent.getOrDefault(line, 0);
    }

    private int currentIndent() {
        return indentOf(peek().getLine());
    }

    // Main parse function: builds list of instructions
    public List<Instruction> parse() {
        List<Instruction> instructions = new ArrayList<>();

        while (!isAtEnd()) {
            skipNewlines(); // ignore empty lines
            if (isAtEnd()) break;

            Instruction inst = parseInstruction(); // parse next instruction
            if (inst != null) instructions.add(inst);
        }

        return instructions;
    }

    // Decide which instruction type to parse
    private Instruction parseInstruction() {
        switch (peek().getType()) {
            case SET:   return parseAssign();
            case SHOW:  return parsePrint();
            case WHEN:  return parseIf();
            case LOOP:  return parseLoop();
            case WHILE: return parseWhile();
            default:
                warnings.add("Unexpected token at line "
                    + peek().getLine() + ": '" + peek().getValue() + "'");
                skipLine(); // skip invalid line
                return null;
        }
    }

    // Parse assignment: SET variable = expression
    private AssignInstruction parseAssign() {
        consume(TokenType.SET);
        String name = consume(TokenType.IDENTIFIER).getValue();
        consume(TokenType.EQUALS);

        Expression expr = parseExpression(); // parse right-hand expression

        skipNewlines();
        return new AssignInstruction(name, expr);
    }

    // Parse print statement: SHOW expression
    private PrintInstruction parsePrint() {
        consume(TokenType.SHOW);

        Expression expr = parseExpression();

        skipNewlines();
        return new PrintInstruction(expr);
    }

    // Parse IF condition block, with optional OTHERWISE / OTHERWISE WHEN (else-if) clause
    private IfInstruction parseIf() {
        int headerDepth = currentIndent(); // indentation of the 'when' line itself

        consume(TokenType.WHEN);

        Expression condition = parseExpression(); // condition expression

        consume(TokenType.COLON);
        skipNewlines();

        List<Instruction> thenBody = parseBlock(headerDepth); // parse indented block

        List<Instruction> elseBody = List.of();

        // Only treat OTHERWISE as belonging to *this* when if it sits at the same
        // depth as the when itself -- otherwise it belongs to some enclosing block.
        if (check(TokenType.OTHERWISE) && currentIndent() == headerDepth) {
            advance();

            if (check(TokenType.WHEN)) {
                // "otherwise when ...:" chains into another if as the else branch
                elseBody = List.of(parseIf());
            } else {
                consume(TokenType.COLON);
                skipNewlines();
                elseBody = parseBlock(headerDepth);
            }
        }

        return new IfInstruction(condition, thenBody, elseBody);
    }

    // Parse LOOP block (fixed iteration count)
    private RepeatInstruction parseLoop() {
        int headerDepth = currentIndent();

        consume(TokenType.LOOP);

        double val = Double.parseDouble(consume(TokenType.NUMBER).getValue());

        // ❗ FIX 2: Ensure loop count is integer
        if (val % 1 != 0) {
            throw new RuntimeException("Loop count must be an integer at line " + peek().getLine());
        }

        int count = (int) val;

        consume(TokenType.COLON);
        skipNewlines();

        return new RepeatInstruction(count, parseBlock(headerDepth)); // parse loop body
    }

    // Parse WHILE block (condition-driven loop)
    private WhileInstruction parseWhile() {
        int headerDepth = currentIndent();

        consume(TokenType.WHILE);

        Expression condition = parseExpression();

        consume(TokenType.COLON);
        skipNewlines();

        return new WhileInstruction(condition, parseBlock(headerDepth));
    }

    // Parse a block of instructions nested more deeply than headerDepth (the
    // indentation of the block's own opening line, e.g. the 'when'/'loop'/'while').
    // Stops as soon as a line's indentation drops back to headerDepth or shallower --
    // that line belongs to an enclosing block, not this one.
    private List<Instruction> parseBlock(int headerDepth) {
        List<Instruction> block = new ArrayList<>();

        while (!isAtEnd()) {
            while (check(TokenType.NEWLINE)) advance(); // skip blank lines
            if (isAtEnd()) break;

            if (currentIndent() <= headerDepth) break; // dedented out of this block

            Instruction inst = parseInstruction();
            if (inst != null) block.add(inst);
        }

        return block;
    }

    // ===================== Expression grammar (lowest to highest precedence) =====================
    // or  <  and  <  not  <  comparisons (> < == != >= <=)  <  + -  <  * /  <  unary -  <  primary/()

    private Expression parseExpression() {
        return parseOr();
    }

    private Expression parseOr() {
        Expression left = parseAnd();

        while (check(TokenType.OR)) {
            advance();
            Expression right = parseAnd();
            left = new LogicalOpNode(left, "or", right);
        }

        return left;
    }

    private Expression parseAnd() {
        Expression left = parseNot();

        while (check(TokenType.AND)) {
            advance();
            Expression right = parseNot();
            left = new LogicalOpNode(left, "and", right);
        }

        return left;
    }

    private Expression parseNot() {
        if (check(TokenType.NOT)) {
            advance();
            return new NotNode(parseNot());
        }
        return parseComparison();
    }

    // Parse comparisons: > < == != >= <=
    private Expression parseComparison() {
        Expression left = parseAdditive();

        while (check(TokenType.GREATER) || check(TokenType.LESS) ||
               check(TokenType.EQEQ) || check(TokenType.NOTEQ) ||
               check(TokenType.GE) || check(TokenType.LE)) {

            String op = advance().getValue();

            // ❗ FIX 3: Check incomplete expression
            if (isAtEnd() || check(TokenType.NEWLINE)) {
                throw new RuntimeException("Incomplete expression at line " + peek().getLine());
            }

            Expression right = parseAdditive();

            left = new BinaryOpNode(left, op, right);
        }

        return left;
    }

    // Parse addition and subtraction
    private Expression parseAdditive() {
        Expression left = parseTerm();

        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {

            String op = advance().getValue();

            // ❗ FIX 3: Check incomplete expression
            if (isAtEnd() || check(TokenType.NEWLINE)) {
                throw new RuntimeException("Incomplete expression at line " + peek().getLine());
            }

            Expression right = parseTerm();

            left = new BinaryOpNode(left, op, right); // build binary operation
        }

        return left;
    }

    // Parse multiplication and division
    private Expression parseTerm() {
        Expression left = parsePrimary();

        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            String op = advance().getValue();
            Expression right = parsePrimary();
            left = new BinaryOpNode(left, op, right);
        }

        return left;
    }

    // Parse basic values: number, string, variable, unary minus, parenthesized expression
    private Expression parsePrimary() {

        // ❗ FIX 1: Handle unary minus (MOST IMPORTANT)
        if (check(TokenType.MINUS)) {
            advance();
            Expression right = parsePrimary();
            return new BinaryOpNode(new NumberNode(0), "-", right);
        }

        // Parenthesized expression — allows grouping and re-enters the full grammar
        if (check(TokenType.LPAREN)) {
            advance();
            Expression expr = parseExpression();
            consume(TokenType.RPAREN);
            return expr;
        }

        Token t = advance();

        switch (t.getType()) {
            case NUMBER:
                return new NumberNode(Double.parseDouble(t.getValue()));

            case STRING:
                return new StringNode(t.getValue());

            case IDENTIFIER:
                return new VariableNode(t.getValue());

            default:
                throw new RuntimeException(
                    "Expected a value (number, string, or variable) at line "
                    + t.getLine() + " but got: '" + t.getValue() + "'");
        }
    }

    // Look at current token without consuming
    private Token peek() {
        return tokens.get(current);
    }

    // Move to next token
    private Token advance() {
        if (!isAtEnd()) current++;
        return tokens.get(current - 1);
    }

    // Check if current token matches given type
    private boolean check(TokenType type) {
        return !isAtEnd() && peek().getType() == type;
    }

    // Check if parsing is finished
    private boolean isAtEnd() {
        return current >= tokens.size() || peek().getType() == TokenType.EOF;
    }

    // Consume expected token or throw syntax error
    private Token consume(TokenType expected) {
        if (!check(expected)) {
            Token got = peek();
            throw new RuntimeException(
                "Syntax error at line " + got.getLine()
                + ": expected " + expected
                + " but found '" + got.getValue() + "' (" + got.getType() + ")");
        }
        return advance();
    }

    // Skip newline tokens
    private void skipNewlines() {
        while (check(TokenType.NEWLINE)) advance();
    }

    // Skip entire current line (error recovery)
    private void skipLine() {
        while (!isAtEnd() && !check(TokenType.NEWLINE)) advance();
        if (check(TokenType.NEWLINE)) advance();
    }
}