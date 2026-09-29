# PROGRESS.md

Tracks actual status against [PLAN.md](PLAN.md). Update this whenever a checklist item in the plan is genuinely done and verified (built/run/tested) — not just written. Last updated: 2026-09-29.

Status key: `[ ]` not started · `[~]` in progress · `[x]` done and verified

---

## Phase 0 — Repo setup
- [x] git init + initial commit (commit `9f7388c`)
- [ ] GitHub repo created + pushed — **blocked on you**: need a repo name and public/private choice before I create it
- [ ] Restructure into `backend/` (deferred until Phase 2 Maven scaffold exists)

## Phase 1 — Interpreter fixes & extensions
- [x] Precedence bug fix (comparisons vs arithmetic) — verified: `a > 2 + 1` now parses as `a > (2 + 1)`
- [x] Parentheses support — verified: `(2 + 3) * 4` → `20`
- [x] `otherwise:` clause wired to existing `elseBody`, plus `otherwise when:` else-if chaining
- [x] `while` loop (`WhileInstruction`, no iteration cap by design — see CLAUDE.md invariant)
- [x] `!=`, `>=`, `<=`, `and`, `or`, `not` (`LogicalOpNode` short-circuits, `NotNode` negates)
- [x] Output capture — `Environment.print()` buffers per-execution, `Interpreter.run()` returns `ExecutionResult{output, error}` instead of writing to `System.out` directly
- [x] Regression check against `sample/program*.zara` — all 4 still produce identical output; built-in demo also verified
- [x] 36-case numbered test suite (`run_tests.sh`, scratchpad) covering every pre-existing bug fix (division by zero, unterminated string, bad number format, loop count guards, reserved name, undefined variable, non-boolean condition, incomplete expression, invalid character, value formatting) and every Phase 1 addition (precedence, parentheses, otherwise/else-if, while, `!= >= <=`, `and/or/not` incl. short-circuit) — **36/36 passing**
- [x] Fixed a real inconsistency found while writing the test suite: error messages showed whole numbers as `5.0` instead of `5` (unlike `show`, which already formatted correctly). Consolidated the duplicated formatting logic from `PrintInstruction`/`BinaryOpNode` into a shared `ValueFormat.format()` and used it everywhere a runtime value is interpolated into an error message.
- Noted, not fixed (unreachable given current grammar, not worth the code churn): `RepeatInstruction`'s negative-loop-count guard can't trigger since the parser only accepts a `NUMBER` token after `loop`; `AssignInstruction`'s null-value guard can't trigger since no expression currently evaluates to `null`.

## Phase 2 — Spring Boot API
- [ ] Maven scaffold + package structure
- [ ] `POST /api/run` endpoint
- [ ] Execution timeout via bounded executor
- [ ] CORS config
- [ ] Actuator health endpoint
- [ ] Dockerfile

## Phase 3 — Tests
- [ ] Tokenizer/Parser/Interpreter unit tests (precedence, parens, otherwise, while)
- [ ] `/api/run` integration tests

## Phase 4 — Frontend
- [ ] Vite + React scaffold
- [ ] Code editor component
- [ ] Run button + output/error panel
- [ ] Sample-program picker
- [ ] Loading/error states

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
