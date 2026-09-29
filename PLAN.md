# PLAN.md — ZARA: from CLI toy to deployed web app

This is the agreed build plan, discussed and confirmed with the project owner on 2026-09-29. Decisions locked in:

- **Language scope:** bug-fix the interpreter *and* add a small set of missing features (parentheses, `otherwise`, `while`, `!= >= <=`, `and/or/not`) — not a full language redesign.
- **Backend:** Spring Boot (Maven), Java.
- **Frontend:** React + Vite (plain SPA, not Next.js — no SSR/routing/SEO need here).
- **Deployment:** free-tier cloud — backend on Render (or Railway), frontend on Vercel.

Work proceeds phase by phase; each phase should be reviewed/tested before the next starts, since later phases depend on earlier ones (e.g. the backend's timeout requirement depends on `while` existing).

---

## Phase 0 — Repo setup

- [ ] `git init`, initial commit of the current state.
- [ ] Create GitHub repo, push (needed later for Render/Vercel auto-deploy from git).
- [ ] Restructure: move current `src/*.java` into `backend/src/main/java/...` once the Maven skeleton exists (done together with Phase 2, not before — no point moving files twice).

## Phase 1 — Fix and extend the interpreter core

Bugs found in the original code (see audit in session history / CLAUDE.md):

- [ ] **Precedence bug:** `parseExpression` in `Parser.java` treats `+ - > < ==` as one left-associative level, so `x > 5 + 1` parses as `(x > 5) + 1`. Fix: comparisons must bind looser than `+ -`, which binds looser than `* /`.
- [ ] **Parentheses:** `parsePrimary` has no `(` / `)` handling — add grouping support.
- [ ] **Dead `elseBody`:** `IfInstruction` already accepts an `elseBody` constructor param but the Parser never produces one. Add an `otherwise:` clause to `parseIf`.
- [ ] **Add `while` loop:** new `WhileInstruction` + parser support. No iteration cap (unlike `loop`) — this is why the backend timeout (Phase 2) is mandatory once this lands.
- [ ] **Add operators:** `!=`, `>=`, `<=`, and boolean `and`, `or`, `not`.
- [ ] **Output capture:** remove direct `System.out.println`/`System.err.println` calls from instructions; route output through a buffer object passed via `Environment` (or a dedicated `OutputSink`), so the CLI `main()` can print the buffer at the end and the future web layer can capture it per-request without cross-talk between concurrent executions.
- [ ] Re-run the existing `sample/program*.zara` files after each change to confirm no regressions.

## Phase 2 — Spring Boot API

- [ ] Scaffold Maven project under `backend/`, move interpreter classes into it (`com.zara.interpreter` package or similar — first time these classes get a package, since they're currently in the default package).
- [ ] `POST /api/run` — request `{ "code": "<zara source>" }`, response `{ "output": "...", "error": "..." | null }`.
- [ ] Run each submitted program on a bounded executor (`ExecutorService` + `Future.get(timeoutMs)`); on timeout, interrupt/cancel and return a clear "execution timed out" error instead of hanging the request.
- [ ] CORS configuration allowing the frontend's origin (localhost during dev, real Vercel URL after Phase 5).
- [ ] `GET /actuator/health` (Spring Actuator) for Render's health checks.
- [ ] `Dockerfile` for the backend.

## Phase 3 — Tests

- [ ] JUnit tests for Tokenizer, Parser, and Interpreter — specifically covering the Phase 1 bug fixes (precedence, parentheses, otherwise, while) so they can't silently regress.
- [ ] Integration test(s) for `/api/run` (happy path, syntax error, execution timeout).

## Phase 4 — Frontend

- [ ] Scaffold `frontend/` with Vite + React.
- [ ] Code editor component (CodeMirror — lightweight, no need for Monaco's full IDE feature set here).
- [ ] Run button → `POST` to backend, render `output`/`error` in a results panel.
- [ ] Sample-program dropdown, loading the contents of `sample/program1-4.zara` (plus any new samples demonstrating `while`/`otherwise`/parentheses).
- [ ] Loading and error states (network failure, backend down, timeout).

## Phase 5 — Deploy

- [ ] Push repo to GitHub (if not already, from Phase 0).
- [ ] Deploy backend to Render as a Docker service; confirm `/actuator/health` passes.
- [ ] Deploy frontend to Vercel; set the backend URL as a build-time env var.
- [ ] Update backend CORS allowed-origins to the real Vercel URL.
- [ ] End-to-end smoke test against the live URLs.

## Phase 6 — Polish

- [ ] Update `README.md` with the new architecture, local dev instructions, and links to the live deployed frontend/backend.
- [ ] Review PROGRESS.md, mark everything actually verified (not just written).
