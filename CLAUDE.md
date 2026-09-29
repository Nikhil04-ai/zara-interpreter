# CLAUDE.md

Context for Claude (or any future contributor) working in this repo. Read this first, then [PLAN.md](PLAN.md) for the full roadmap and [PROGRESS.md](PROGRESS.md) for what's actually done so far.

## What this project is

ZARA is a toy scripting language interpreter, originally built as a pure-Java, zero-dependency, console-only tree-walk interpreter (Tokenizer → Parser → Instruction tree → Environment) for an Advanced OOP course project. It is being turned into a full deployable web app: a Spring Boot backend exposing the interpreter over a REST API, and a React (Vite) frontend that's a browser-based ZARA playground (editor + Run button + output panel).

## Repo layout

```
backend/                              Spring Boot app (Maven).
  src/main/java/com/zara/interpreter/  Interpreter core: Tokenizer, Parser,
                                        Instruction/Expression tree, Environment,
                                        Interpreter. Framework-agnostic -- no
                                        Spring imports in this package, so it
                                        keeps working as a standalone CLI too
                                        (java -cp target/classes com.zara.interpreter.Interpreter <file>).
  src/main/java/com/zara/api/          Spring layer: ZaraApiApplication (entry
                                        point), RunController (POST /api/run),
                                        ExecutionService (bounded executor +
                                        timeout), CorsConfig, GlobalExceptionHandler.
  src/test/java/...                    JUnit/MockMvc tests.
  Dockerfile                           Multi-stage build -> runnable jar.
frontend/        React + Vite SPA (Phase 4, not yet scaffolded). Code editor,
                 Run button, output/error panel, sample-program picker.
sample/          Example .zara programs (repo root, shared by the CLI demo and
                 the future frontend's sample picker).
PLAN.md          Phased build plan.
PROGRESS.md      Checklist of what's done / in progress / not started.
```

The Phase 0/2 restructure (flat `src/` -> Maven `backend/`) is done — see PROGRESS.md for exact status of later phases.

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

**Backend API:**
```bash
cd backend
mvn spring-boot:run
```
Then `POST http://localhost:8080/api/run` with `{"code": "show 1 + 2"}`.

**Backend as a CLI (no Spring involved, same interpreter classes):**
```bash
cd backend
mvn compile
java -cp target/classes com.zara.interpreter.Interpreter ../sample/program1.zara
```

**Backend tests** (MockMvc-based; exercises the controller/service/CORS stack without binding a real port -- see the note below on why):
```bash
cd backend
mvn test
```

**Known local-environment quirk (Windows only):** on at least one Windows dev machine, `mvn spring-boot:run` fails at Tomcat startup with `java.io.IOException: Unable to establish loopback connection` coming from `sun.nio.ch.PipeImpl`/`WEPollSelectorProvider` -- a JDK-vs-Windows-networking issue in the NIO selector's internal wakeup pipe, unrelated to this project's code, and independent of which selector provider is forced. It reproduces identically for any NIO-based embedded server (Tomcat, Netty, Jetty), so switching servlet containers won't help. It has **not** been reproduced in Docker (Linux) and should not affect Render deployment (Phase 5) -- Render builds and runs the `Dockerfile` on Linux. If you hit this locally, use `mvn test` (MockMvc) to verify the API instead of a real running server, or test via Docker if the daemon is available.

**Frontend (once scaffolded, Phase 4):**
```bash
cd frontend
npm install
npm run dev
```

## Invariants that hold now that the backend API exists

- **No direct `System.out`/`System.err` in the interpreter core.** Instructions write to a per-execution output buffer on `Environment`; `Interpreter.run()` returns it via `ExecutionResult{output, error}`. The parser's own recoverable "unexpected token" warnings go through the same path (`Parser.getWarnings()`), not a raw stream either. The API serves concurrent requests and a shared stream would interleave/corrupt output between them.
- **Every `/api/run` execution is wall-clock bounded.** `ExecutionService` runs each request on a bounded executor with `Future.get(timeoutMs)` (default 5000ms, `zara.execution.timeout-ms`). `loop N` is separately capped at 100,000 iterations, but `while` has no such cap by design. `Future.cancel(true)` only *requests* interruption -- it can't force a running thread to stop -- so `WhileInstruction` and `RepeatInstruction` both check `Thread.currentThread().isInterrupted()` every iteration and throw if set. Without that cooperative check, a timed-out request would return a timeout response to the caller while its worker thread kept burning CPU forever in the background.
- **The interpreter has no file I/O, network access, or reflection** — keep it that way. That's what makes exposing arbitrary user-submitted ZARA code over a public HTTP endpoint acceptable risk (the worst case is CPU/time exhaustion, which the timeout above handles).

## Working conventions

- Keep bug fixes and feature additions in the interpreter core minimal and targeted — this is a teaching-sized language, not a general-purpose one. Don't add abstractions (plugin systems, visitor frameworks, etc.) beyond what the current OOP structure (Instruction/Expression interfaces) already supports.
- No code comments unless they explain a non-obvious *why* (e.g. the timeout invariant above). Don't narrate what the code does.
- Update PROGRESS.md as phases complete — it's the source of truth for "what's actually working right now" across sessions.
