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
- [x] Categorized 64-case numbered test suite (`run_tests_full.sh`, scratchpad; supersedes an earlier 36-case pass) covering 12 categories: BUGFIX (15), PRECEDENCE (4), PARENS (4), CONDITIONAL (5), LOOP (2), WHILE (3), COMPARISON (6), LOGIC (5), TOKENIZER (7), PARSER_RECOVERY (5), TYPES (4), NESTING (4) — **64/64 passing**
- [x] Fixed a real inconsistency found while writing the test suite: error messages showed whole numbers as `5.0` instead of `5` (unlike `show`, which already formatted correctly). Consolidated the duplicated formatting logic from `PrintInstruction`/`BinaryOpNode` into a shared `ValueFormat.format()` and used it everywhere a runtime value is interpolated into an error message.
- Noted, not fixed (unreachable given current grammar, not worth the code churn): `RepeatInstruction`'s negative-loop-count guard can't trigger since the parser only accepts a `NUMBER` token after `loop`; `AssignInstruction`'s null-value guard can't trigger since no expression currently evaluates to `null`.
- [x] Fixed a parser leak: the "unexpected token" recovery path wrote straight to `System.err` instead of going through the output buffer. `Parser` now collects these as warnings (`getWarnings()`) and `Interpreter.run()` folds them into the same `ExecutionResult` output — nothing is written to a raw stream anywhere in the interpreter core anymore.
- [x] **Critical bug found and fixed: indentation-based block parsing tracked only "indented or not" (binary), not depth.** A block dedenting by one level while still indented (e.g. 8 spaces back to 4, not all the way to column 0) was wrongly swallowed into the deepest active block instead of popping back to its actual enclosing block. Found via a 2-level-nesting test (`when` inside `while`): the loop's own increment statement got absorbed into a sibling `when` block, so it only ran conditionally, the loop variable never advanced, and the `while` spun forever — one test process grew to ~2GB of buffered output before being killed. Root cause and fix (indentation now measured as a numeric width per line, blocks compare depth, not just presence) are detailed in [Parser.java](src/Parser.java). This was a pre-existing bug in the original code, not something introduced by Phase 1 — it just required 2+ levels of nesting with a partial dedent to trigger, which nothing in the original samples or my first test pass exercised.
- [x] Expanded to a 62-case categorized test suite (`run_tests_full.sh`, scratchpad) adding: tokenizer edge cases (comments, blank lines, tabs, identifiers, leading-zero decimals, empty strings), parser-recovery cases (case-sensitive keywords, unexpected-statement recovery vs. fatal tokenize errors, non-literal loop counts), type-coercion errors (boolean+number, string-string arithmetic), and nested-control-flow cases including a direct regression test for the indentation bug above.

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
