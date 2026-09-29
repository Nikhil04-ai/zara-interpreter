# ZARA Interpreter

A mini scripting language interpreter built from scratch in pure Java — no external libraries, no frameworks.

---

## About

ZARA *(Zero-ceremony Arithmetic and Reasoning Assembler)* is a toy scripting language that you can write, run, and get real output from. The interpreter is built as a classic three-stage pipeline — **Tokenizer → Parser → Evaluator** — using object-oriented design patterns throughout.

Built as a group project for the Advanced OOP course at Sitare University.

---

## Language Syntax

```
set x = 10
set y = 3
set result = x + y * 2
show result               # → 16

set score = 85
when score > 50:
    show "Passed"

loop 4:
    show "hello"
```

Supports: variable assignment, print, conditionals, loops, arithmetic (`+ - * /`), comparisons (`> < ==`), strings, and numbers.

---

## Getting Started

**Requirements:** JDK 8 or above.

```bash
# Compile
mkdir out
javac src/*.java -d out

# Run a sample program
java -cp out Interpreter samples/program1.zara

# Run built-in demo
java -cp out Interpreter
```

---

## Project Structure

```
src/
├── TokenType.java       Token categories enum
├── Token.java           Immutable token object
├── Tokenizer.java       Source text → List<Token>
├── Expression.java      Expression interface
├── Nodes.java           NumberNode, StringNode, VariableNode, BinaryOpNode
├── Environment.java     Variable store (HashMap)
├── Instructions.java    AssignInstruction, PrintInstruction, IfInstruction, RepeatInstruction
├── Parser.java          List<Token> → List<Instruction>
├── Interpreter.java     Entry point — wires all three stages
└── Extensions.java      Optional: WhileInstruction, LengthNode

samples/
├── program1.zara        Arithmetic
├── program2.zara        Strings
├── program3.zara        Conditional
└── program4.zara        Loop
```

---

## Tech

- **Language:** Java (zero dependencies)
- **Parsing:** Recursive descent
- **Evaluation:** Tree-walk interpreter
- **Runtime types:** `Double`, `String`, `Boolean`

---
