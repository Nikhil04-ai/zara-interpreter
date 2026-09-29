# PROGRESS.md

Tracks actual status against [PLAN.md](PLAN.md). Update this whenever a checklist item in the plan is genuinely done and verified (built/run/tested) — not just written. Last updated: 2026-09-29.

Status key: `[ ]` not started · `[~]` in progress · `[x]` done and verified

---

## Phase 0 — Repo setup
- [x] git init + initial commit (commit `9f7388c`)
- [~] GitHub repo created + pushed — decided: private repo named `zara-interpreter`, user publishing manually via GitHub Desktop (already installed); not blocking further work
- [x] Restructure into `backend/` — done as part of Phase 2 (see below), all interpreter classes moved to `backend/src/main/java/com/zara/interpreter/` with package declarations added

## Phase 1 — Interpreter fixes & extensions
- [x] Precedence bug fix (comparisons vs arithmetic) — verified: `a > 2 + 1` now parses as `a > (2 + 1)`
- [x] Parentheses support — verified: `(2 + 3) * 4` → `20`
- [x] `otherwise:` clause wired to existing `elseBody`, plus `otherwise when:` else-if chaining
- [x] `while` loop (`WhileInstruction`, no iteration cap by design — see CLAUDE.md invariant)
- [x] `!=`, `>=`, `<=`, `and`, `or`, `not` (`LogicalOpNode` short-circuits, `NotNode` negates)
- [x] Output capture — `Environment.print()` buffers per-execution, `Interpreter.run()` returns `ExecutionResult{output, error}` instead of writing to `System.out` directly
- [x] Regression check against `sample/program*.zara` — all 4 still produce identical output; built-in demo also verified
- [x] Categorized 64-case numbered test suite (`run_tests_full.sh`, scratchpad; supersedes an earlier 36-case pass) covering 12 categories: BUGFIX (15), PRECEDENCE (4), PARENS (4), CONDITIONAL (5), LOOP (2), WHILE (3), COMPARISON (6), LOGIC (5), TOKENIZER (7), PARSER_RECOVERY (5), TYPES (4), NESTING (4) — **64/64 passing**
- [x] Fixed a real inconsistency found while writing the test suite: error messages showed whole numbers as `5.0` instead of `5` (unlike `show`, which already formatted correctly). Consolidated the duplicated formatting logic from `PrintInstruction`/`BinaryOpNode` into a shared `ValueFormat.format()` and used it everywhere a runtime value is interpolated into an error message.
- Noted, not fixed (unreachable given current grammar, not worth the code churn): `RepeatInstruction`'s negative-loop-count guard can't trigger since the parser only accepts a `NUMBER` token after `loop`; `AssignInstruction`'s null-value guard can't trigger since no expression currently evaluates to `null`.
- [x] Fixed a parser leak: the "unexpected token" recovery path wrote straight to `System.err` instead of going through the output buffer. `Parser` now collects these as warnings (`getWarnings()`) and `Interpreter.run()` folds them into the same `ExecutionResult` output — nothing is written to a raw stream anywhere in the interpreter core anymore.
- [x] **Critical bug found and fixed: indentation-based block parsing tracked only "indented or not" (binary), not depth.** A block dedenting by one level while still indented (e.g. 8 spaces back to 4, not all the way to column 0) was wrongly swallowed into the deepest active block instead of popping back to its actual enclosing block. Found via a 2-level-nesting test (`when` inside `while`): the loop's own increment statement got absorbed into a sibling `when` block, so it only ran conditionally, the loop variable never advanced, and the `while` spun forever — one test process grew to ~2GB of buffered output before being killed. Root cause and fix (indentation now measured as a numeric width per line, blocks compare depth, not just presence) are detailed in [Parser.java](backend/src/main/java/com/zara/interpreter/Parser.java). This was a pre-existing bug in the original code, not something introduced by Phase 1 — it just required 2+ levels of nesting with a partial dedent to trigger, which nothing in the original samples or my first test pass exercised.
- [x] Expanded to a 62-case categorized test suite (`run_tests_full.sh`, scratchpad) adding: tokenizer edge cases (comments, blank lines, tabs, identifiers, leading-zero decimals, empty strings), parser-recovery cases (case-sensitive keywords, unexpected-statement recovery vs. fatal tokenize errors, non-literal loop counts), type-coercion errors (boolean+number, string-string arithmetic), and nested-control-flow cases including a direct regression test for the indentation bug above.

## Phase 2 — Spring Boot API
- [x] Maven scaffold + package structure — `backend/pom.xml` (Spring Boot 3.3.4 parent, Java 21), interpreter moved to `com.zara.interpreter`, new Spring layer in `com.zara.api`. Interpreter package has zero Spring imports, so `Interpreter.main()` still works as a plain CLI from `target/classes`.
- [x] `POST /api/run` endpoint — `RunController` -> `ExecutionService` -> `RunRequest{code}` / `RunResponse{output, error}`
- [x] Execution timeout via bounded executor — `ExecutionService` uses `ExecutorService` + `Future.get(timeoutMs)` (default 5000ms, `zara.execution.timeout-ms`); on timeout calls `future.cancel(true)`. Made this actually effective by adding cooperative interruption checks (`Thread.currentThread().isInterrupted()`) inside `WhileInstruction` and `RepeatInstruction`'s loops -- without that, `cancel(true)` would only request interruption while the worker thread kept spinning forever.
- [x] CORS config — `CorsConfig` allows `http://localhost:*` and `https://*.vercel.app` on `/api/**`; will narrow to the exact deployed origin in Phase 5
- [x] Actuator health endpoint — `/actuator/health` exposed via `spring-boot-starter-actuator` + `application.properties`
- [x] Dockerfile — multi-stage (`maven:3.9-eclipse-temurin-21` build -> `eclipse-temurin:21-jre-alpine` runtime); not yet run locally (Docker Desktop installed but daemon not started -- didn't start it unprompted)
- [x] Verified via 5 MockMvc integration tests (`RunControllerTest`, all passing): happy path, runtime error in `error` field, partial output preserved on mid-execution failure, **infinite `while` actually stopped by the timeout instead of hanging** (proves the interruption-check fix works end-to-end), malformed JSON still returns the same `{output,error}` shape
- Known local-environment issue (documented in CLAUDE.md, not a code bug): `mvn spring-boot:run` fails to bind a real port on this Windows machine due to a JDK/Windows NIO selector bug (`Unable to establish loopback connection` in `sun.nio.ch.PipeImpl`), reproducible with any embedded servlet container and independent of `SelectorProvider` choice. Verified via `mvn test` (MockMvc, no real socket needed) instead. Not expected to affect Render's Linux-based Docker deployment in Phase 5.
- [x] **Full API re-test pass (2026-09-29): expanded to 23 backend tests across two classes, found and fixed 2 more real bugs.**
  - `RunControllerTest` (17 tests, 300ms timeout): happy path, error field, partial output, timeout, malformed JSON, empty/missing `code` field, missing body, wrong HTTP method, 3 CORS tests (allowed localhost origin, allowed `*.vercel.app` origin, rejected unrelated origin -- confirms CORS is actually origin-checked, not just present), actuator health, 3 full-language-feature-through-JSON smoke tests, repeated-timeouts-then-still-responsive.
  - `RunControllerSafetyTest` (3 tests, 3000ms timeout, separate Spring context since it needs a realistic timeout window): a legitimate 50,000-iteration loop completes correctly instead of being killed early, deeply nested parentheses return a clean error instead of crashing the server, 8 concurrent requests each get back their own correct output (no cross-contamination) -- exercises real concurrency (each request submits to `ExecutionService`'s own executor, not just sequential MockMvc calls).
  - **Bug found via CLI probe (20,000 nested parens): `StackOverflowError` escaped `Interpreter.run()` completely uncaught** (`catch (RuntimeException e)` doesn't catch `Error` subtypes), crashing the CLI with a raw trace and, via the API's `ExecutorService`, would have surfaced as an opaque wrapped exception. Fixed at the root in [Interpreter.java](backend/src/main/java/com/zara/interpreter/Interpreter.java): added `catch (StackOverflowError e)` returning a clean `"Program is nested too deeply to evaluate"` message, benefiting both the CLI and the API.
  - **Bug found via direct status-code probe: `GlobalExceptionHandler`'s catch-all mapped every exception to `400 Bad Request` and echoed the raw exception message.** This meant `GET /api/run` returned `400` instead of the correct `405 Method Not Allowed`, and a missing request body leaked an internal Java method signature (`public com.zara.api.RunResponse com.zara.api.RunController.run(...)`) into the client-facing error message. Fixed in [GlobalExceptionHandler.java](backend/src/main/java/com/zara/api/GlobalExceptionHandler.java): `HttpRequestMethodNotSupportedException` and `HttpMessageNotReadableException` now get their own handlers with correct status codes and clean, generic messages; the remaining catch-all now returns `500` (an unanticipated exception is a server fault, not a client one) with a generic message, logging the real exception server-side only.
  - Re-verified after both fixes: all 20 API tests pass, `mvn package` builds a clean runnable jar, all 4 `sample/*.zara` + built-in demo still match exactly via the new `target/classes` location.

## Phase 3 — Tests
- [~] `/api/run` integration tests — done early as part of Phase 2 verification (`RunControllerTest`, 5/5 passing)
- [ ] Tokenizer/Parser/Interpreter unit tests (precedence, parens, otherwise, while) — the 64-case shell suite from Phase 1 covers this behaviorally; still need proper JUnit versions for CI

## Phase 4 — Frontend
- [x] Vite + React scaffold — `frontend/` (React 19, Vite 8), builds clean (`npm run build`, `npm run lint` both pass with zero issues)
- [x] Code editor component — `@uiw/react-codemirror` (plain text mode, no custom ZARA language grammar -- out of scope for MVP; line numbers/bracket-matching still work via CodeMirror's `basicSetup`)
- [x] Run button + output/error panel — [App.jsx](frontend/src/App.jsx): success output in a `<pre>`, interpreter errors in a red banner, network/transport failures in a distinct orange banner (via [api.js](frontend/src/api.js), which only throws on transport failure -- a ZARA program error is a normal response, not an exception)
- [x] Sample-program picker — dropdown loading `sample/program1-4.zara` (copied into `frontend/public/samples/`, served as static files)
- [x] Loading/error states — Run button shows "Running…" and disables during a request; `Ctrl+Enter` also triggers a run
- [x] **Verified live in the browser** (Claude Code's browser pane, via `.claude/launch.json` running `npm run dev`). Since the real Spring Boot backend can't bind a port on this Windows machine (same JDK/loopback issue as Phase 2), used a throwaway Node mock server matching the real `{output, error}` shape to exercise the actual fetch/render cycle end-to-end. Confirmed: sample picker loads and populates the editor; Run produces correct output; a simulated interpreter error renders the red banner with partial-output handling; killing the mock backend and re-running renders the orange network-error banner with a clear message; restarting the backend and re-running (via `Ctrl+Enter`) recovers correctly; responsive layout correctly switches between side-by-side (wide) and stacked (narrow) panes.
- One incidental finding during manual testing: reused a stale element reference across two browser actions, which caused a click to land on the sample `<select>` instead of the editor (typing "s..." jump-selected "Strings") -- a testing-methodology mistake, not an app bug; recovered by re-querying the page and retrying. Noted here only because it's a good reminder for future browser-driven testing in this repo: re-fetch element references after any action that could re-render the page.
- Not yet done: full integration against the *real* Spring Boot backend (blocked by the same local Windows quirk as Phase 2) -- planned for Phase 5 once deployed, or sooner if Docker/another machine becomes available.

## Phase 5 — Deploy
- [ ] Backend deployed to Render, health check passing
- [ ] Frontend deployed to Vercel
- [ ] CORS updated to real frontend origin
- [ ] End-to-end smoke test on live URLs

## Phase 6 — Polish
- [ ] README rewritten with architecture + live links
- [ ] Full plan re-verified against this checklist

---

## Notes / decisions log

- 2026-09-29: Plan discussed and agreed. Stack locked: Spring Boot backend, React+Vite frontend, Render+Vercel hosting. Language scope: bug-fix + small additions (not a full rewrite). CLAUDE.md, PLAN.md, PROGRESS.md created before any code changes.
- 2026-09-29: Phase 0 (local) and Phase 1 completed and verified. Local git repo initialized and committed (`9f7388c`). GitHub push deliberately not done yet — needs the repo name/visibility decision.
