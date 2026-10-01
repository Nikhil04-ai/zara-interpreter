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
  src/test/java/com/zara/interpreter/  TokenizerTest, ParserTest, InterpreterTest --
                                        direct unit tests, no subprocess/CLI involved.
  src/test/java/com/zara/api/          RunControllerTest, RunControllerSafetyTest --
                                        MockMvc integration tests (see note below on why).
  Dockerfile                           Multi-stage build -> runnable jar.
  .dockerignore                        Excludes target/.
frontend/        React + Vite SPA -- a browser ZARA playground.
  src/App.jsx      Layout, run/loading/error state, Ctrl+Enter shortcut.
  src/api.js       runZara(code) -- POSTs to VITE_API_URL + /api/run, throws
                    only on transport failure (a ZARA program error is a normal
                    200 with a non-null `error` field, not an exception).
  src/samples.js    Sample-picker metadata + loader, fetches public/samples/*.zara.
  public/samples/   Copies of sample/*.zara (see below) served as static files.
  Dockerfile        Multi-stage: Node build -> nginx serving the static dist/.
                    VITE_API_URL must be passed as a build ARG (Vite bakes it
                    into the JS at build time, not read at container start).
  .dockerignore     Excludes node_modules/, dist/.
sample/          Example .zara programs (repo root; source of truth -- also
                 copied into frontend/public/samples/ for the sample picker,
                 and used directly by the backend CLI/tests).
docker-compose.yml    Runs backend + frontend together (see Build & run).
.claude/launch.json  Dev-server launch config (frontend on :5173) for this
                     session's browser-preview tooling.
PLAN.md          Phased build plan.
PROGRESS.md      Checklist of what's done / in progress / not started.
```

The Phase 0/2 restructure (flat `src/` -> Maven `backend/`) and Phase 4 (frontend scaffold) are both done — see PROGRESS.md for exact status of every phase.

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

**Backend tests** (107 total: TokenizerTest/ParserTest/InterpreterTest run the interpreter directly with no subprocess; RunControllerTest/RunControllerSafetyTest use MockMvc, exercising the controller/service/CORS stack without binding a real port -- see the note below on why):
```bash
cd backend
mvn test
```

**Known local-environment quirk (Windows only):** on at least one Windows dev machine, `mvn spring-boot:run` fails at Tomcat startup with `java.io.IOException: Unable to establish loopback connection` coming from `sun.nio.ch.PipeImpl`/`WEPollSelectorProvider` -- a JDK-vs-Windows-networking issue in the NIO selector's internal wakeup pipe, unrelated to this project's code, and independent of which selector provider is forced. It reproduces identically for any NIO-based embedded server (Tomcat, Netty, Jetty), so switching servlet containers won't help. **Confirmed resolved by running via Docker instead** (see below) -- Docker containers run Linux, so this JDK/Windows-specific bug doesn't apply there. If Docker isn't available, use `mvn test` (MockMvc) to verify the API instead of a real running server.

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```
Reads the backend URL from `VITE_API_URL` (see `.env.example`; defaults to `http://localhost:8080` via `.env.local`).

**Full stack via Docker (recommended for local end-to-end testing):**
```bash
docker compose up --build
```
Frontend at `http://localhost:3000`, backend at `http://localhost:8080`. This is the only way to test the real frontend against the real backend on a Windows dev machine (see the quirk above) -- confirmed working end-to-end (2026-10-01): sample picker, successful runs, and error rendering (incl. partial output) all verified correct through the live containers, not a mock.

## Invariants that hold now that the backend API exists

- **No direct `System.out`/`System.err` in the interpreter core.** Instructions write to a per-execution output buffer on `Environment`; `Interpreter.run()` returns it via `ExecutionResult{output, error}`. The parser's own recoverable "unexpected token" warnings go through the same path (`Parser.getWarnings()`), not a raw stream either. The API serves concurrent requests and a shared stream would interleave/corrupt output between them.
- **Every `/api/run` execution is wall-clock bounded.** `ExecutionService` runs each request on a bounded executor with `Future.get(timeoutMs)` (default 5000ms, `zara.execution.timeout-ms`). `loop N` is separately capped at 100,000 iterations, but `while` has no such cap by design. `Future.cancel(true)` only *requests* interruption -- it can't force a running thread to stop -- so `WhileInstruction` and `RepeatInstruction` both check `Thread.currentThread().isInterrupted()` every iteration and throw if set. Without that cooperative check, a timed-out request would return a timeout response to the caller while its worker thread kept burning CPU forever in the background.
- **The interpreter has no file I/O, network access, or reflection** — keep it that way. That's what makes exposing arbitrary user-submitted ZARA code over a public HTTP endpoint acceptable risk (the worst case is CPU/time exhaustion, which the timeout above handles).

## A gotcha worth not repeating

`GlobalExceptionHandler` (`com.zara.api`) is a `@RestControllerAdvice` with a catch-all `@ExceptionHandler(Exception.class)`. **An unscoped `@RestControllerAdvice` applies to the entire application context, not just your own controllers** -- it was once wrongly intercepting Spring's own `NoResourceFoundException` (thrown for any unmatched static path, e.g. `/` or `/favicon.ico`) and turning a normal 404 into a 500 in our API's error shape. The instinctive fix -- `@RestControllerAdvice(assignableTypes = RunController.class)` -- was tried and **reverted**: it also stopped `HttpRequestMethodNotSupportedException` (e.g. `GET /api/run`) from being handled, silently dropping back to Spring's bodyless default response and losing the `{output,error}` shape for that case too (caught by `mvn test`, not by inspection). The correct pattern, now in place: keep the advice unscoped, and give the *specific* exception you don't want it touching (`NoResourceFoundException`) its own `@ExceptionHandler` earlier in the class, so the generic catch-all never sees it. If you add more infrastructure-level exceptions to carve out in the future, follow that same pattern rather than trying to scope the whole advice.

## Working conventions

- Keep bug fixes and feature additions in the interpreter core minimal and targeted — this is a teaching-sized language, not a general-purpose one. Don't add abstractions (plugin systems, visitor frameworks, etc.) beyond what the current OOP structure (Instruction/Expression interfaces) already supports.
- No code comments unless they explain a non-obvious *why* (e.g. the timeout invariant above). Don't narrate what the code does.
- Update PROGRESS.md as phases complete — it's the source of truth for "what's actually working right now" across sessions.
