package com.zara.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Tokenizer {

    private static final Map<String, TokenType> KEYWORDS = Map.of(
        "set",       TokenType.SET,
        "show",      TokenType.SHOW,
        "when",      TokenType.WHEN,
        "otherwise", TokenType.OTHERWISE,
        "loop",      TokenType.LOOP,
        "while",     TokenType.WHILE,
        "and",       TokenType.AND,
        "or",        TokenType.OR,
        "not",       TokenType.NOT
    );

    private static final Map<Character, TokenType> SINGLE_OPS = Map.of(
        '+', TokenType.PLUS,
        '-', TokenType.MINUS,
        '*', TokenType.STAR,
        '/', TokenType.SLASH,
        ':', TokenType.COLON,
        '(', TokenType.LPAREN,
        ')', TokenType.RPAREN
    );

    private final String source;
    private int pos;
    private int line;

    public Tokenizer(String source) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < source.length()) {

            char c = source.charAt(pos);

            // skip spaces
            if (c == ' ' || c == '\t') {
                pos++;
                continue;
            }

            // skip \r (Windows)
            if (c == '\r') {
                pos++;
                continue;
            }

            // newline
            if (c == '\n') {
                tokens.add(new Token(TokenType.NEWLINE, "\n", line));
                line++;
                pos++;
                continue;
            }

            // comment
            if (c == '#') {
                while (pos < source.length() && source.charAt(pos) != '\n') {
                    pos++;
                }
                continue;
            }

            // operators
            if (SINGLE_OPS.containsKey(c)) {
                tokens.add(new Token(SINGLE_OPS.get(c), String.valueOf(c), line));
                pos++;
                continue;
            }

            // '=' or '=='
            if (c == '=') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new Token(TokenType.EQEQ, "==", line));
                    pos += 2;
                } else {
                    tokens.add(new Token(TokenType.EQUALS, "=", line));
                    pos++;
                }
                continue;
            }

            // '>' or '>='
            if (c == '>') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new Token(TokenType.GE, ">=", line));
                    pos += 2;
                } else {
                    tokens.add(new Token(TokenType.GREATER, ">", line));
                    pos++;
                }
                continue;
            }

            // '<' or '<='
            if (c == '<') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new Token(TokenType.LE, "<=", line));
                    pos += 2;
                } else {
                    tokens.add(new Token(TokenType.LESS, "<", line));
                    pos++;
                }
                continue;
            }

            // '!=' (bare '!' is not a valid operator on its own — use 'not')
            if (c == '!') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new Token(TokenType.NOTEQ, "!=", line));
                    pos += 2;
                    continue;
                }
                throw new RuntimeException("Invalid character '!' at line " + line + " (did you mean '!='?)");
            }

            // string
            if (c == '"') {
                tokens.add(readString());
                continue;
            }

            // number
            if (Character.isDigit(c)) {
                tokens.add(readNumber());
                continue;
            }

            // identifier / keyword
            if (Character.isLetter(c) || c == '_') {
                tokens.add(readWord());
                continue;
            }

            // ❗ FIX 3: unknown character → throw error (not just warning)
            throw new RuntimeException("Invalid character '" + c + "' at line " + line);
        }

        tokens.add(new Token(TokenType.EOF, "", line));
        return tokens;
    }

    // ================= STRING =================
    private Token readString() {
        pos++; // skip opening "

        StringBuilder sb = new StringBuilder();

        while (pos < source.length() && source.charAt(pos) != '"') {
            sb.append(source.charAt(pos));
            pos++;
        }

        // ❗ FIX 1: check if string closed properly
        if (pos >= source.length()) {
            throw new RuntimeException("Unterminated string at line " + line);
        }

        pos++; // skip closing "

        return new Token(TokenType.STRING, sb.toString(), line);
    }

    // ================= NUMBER =================
    private Token readNumber() {
        StringBuilder sb = new StringBuilder();
        boolean hasDot = false;   // ❗ FIX 2: track decimal point

        while (pos < source.length()) {
            char c = source.charAt(pos);

            if (Character.isDigit(c)) {
                sb.append(c);
            }
            else if (c == '.') {
                // ❗ FIX 2: allow only one dot
                if (hasDot) {
                    throw new RuntimeException("Invalid number format at line " + line);
                }
                hasDot = true;
                sb.append(c);
            }
            else {
                break;
            }

            pos++;
        }

        return new Token(TokenType.NUMBER, sb.toString(), line);
    }

    // ================= WORD =================
    private Token readWord() {
        StringBuilder sb = new StringBuilder();

        while (pos < source.length() &&
               (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
            sb.append(source.charAt(pos));
            pos++;
        }

        String word = sb.toString();
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.IDENTIFIER);

        return new Token(type, word, line);
    }
}