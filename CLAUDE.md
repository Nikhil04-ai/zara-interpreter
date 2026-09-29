# CLAUDE.md

Context for Claude (or any future contributor) working in this repo. Read this first, then [PLAN.md](PLAN.md) for the full roadmap and [PROGRESS.md](PROGRESS.md) for what's actually done so far.

## What this project is

ZARA is a toy scripting language interpreter, originally built as a pure-Java, zero-dependency, console-only tree-walk interpreter (Tokenizer → Parser → Instruction tree → Environment) for an Advanced OOP course project. It is being turned into a full deployable web app: a Spring Boot backend exposing the interpreter over a REST API, and a React (Vite) frontend that's a browser-based ZARA playground (editor + Run button + output panel).

## Target repo layout

```
backend/        Spring Boot app (Maven). Contains the interpreter core (Tokenizer,
                 Parser, Instruction/Expression tree, Environment, Interpreter) plus
                 a REST controller wrapping it.
frontend/        React + Vite SPA. Code editor, Run button, output/error panel,
                 sample-program picker.
sample/          Example .zara programs, used by both the CLI demo and the
                 frontend's sample picker.
PLAN.md          Phased build plan.
PROGRESS.md      Checklist of what's done / in progress / not started.
```

Until Phase 0 of the plan runs, the repo is still in its original flat `src/` layout — check PROGRESS.md before assuming the restructure has happened.

## ZARA language syntax

```
set x = 10
set y = 3
set result = x + y * 2
show result

set score = 85
when score > 50:
    show "Passed"
otherwise:
    show "Failed"

loop 4:
    show "hello"

while x > 0:
    set x = x - 1
```

Operators: `+ - * /`, comparisons `> < == != >= <=`, boolean `and or not`, parentheses for grouping. Strings and numbers (doubles) are the only runtime types, plus booleans from comparisons.

Note: `otherwise:`, `while`, `!=`, `>=`, `<=`, `and/or/not`, and parentheses are *planned* additions — see PLAN.md Phase 1 for what exists vs. what's being added, and PROGRESS.md for current status.

## Build & run

**Backend (once restructured to Maven, Phase 2):**
```bash
cd backend
mvn spring-boot:run
```

**Backend (current flat layout, pre-restructure):**
```bash
mkdir out
javac src/*.java -d out
java -cp out Interpreter sample/program1.zara
```

**Frontend (once scaffolded, Phase 4):**
```bash
cd frontend
npm install
npm run dev
```

## Invariants that must hold once the backend API exists

- **No direct `System.out`/`System.err` in the interpreter core.** Instructions must write to a per-execution output buffer (passed through `Environment` or similar), never to the shared process stdout — the API serves concurrent requests and a shared stream would interleave/corrupt output between them.
- **Every `/api/run` execution must be wall-clock bounded.** `loop N` is capped at 100,000 iterations, but `while` has no such cap — an infinite `while` must be stopped by a server-side execution timeout (not by the language itself), or a single bad request can hang a worker thread indefinitely.
- **The interpreter has no file I/O, network access, or reflection** — keep it that way. That's what makes exposing arbitrary user-submitted ZARA code over a public HTTP endpoint acceptable risk (the worst case is CPU/time exhaustion, which the timeout above handles).

## Working conventions

- Keep bug fixes and feature additions in the interpreter core minimal and targeted — this is a teaching-sized language, not a general-purpose one. Don't add abstractions (plugin systems, visitor frameworks, etc.) beyond what the current OOP structure (Instruction/Expression interfaces) already supports.
- No code comments unless they explain a non-obvious *why* (e.g. the timeout invariant above). Don't narrate what the code does.
- Update PROGRESS.md as phases complete — it's the source of truth for "what's actually working right now" across sessions.
