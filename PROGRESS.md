# PROGRESS.md

Tracks actual status against [PLAN.md](PLAN.md). Update this whenever a checklist item in the plan is genuinely done and verified (built/run/tested) — not just written. Last updated: 2026-09-29.

Status key: `[ ]` not started · `[~]` in progress · `[x]` done and verified

---

## Phase 0 — Repo setup
- [ ] git init + initial commit
- [ ] GitHub repo created + pushed
- [ ] Restructure into `backend/` (deferred until Phase 2 Maven scaffold exists)

## Phase 1 — Interpreter fixes & extensions
- [ ] Precedence bug fix (comparisons vs arithmetic)
- [ ] Parentheses support
- [ ] `otherwise:` clause wired to existing `elseBody`
- [ ] `while` loop
- [ ] `!=`, `>=`, `<=`, `and`, `or`, `not`
- [ ] Output capture (remove direct `System.out`/`System.err` from instructions)
- [ ] Regression check against `sample/program*.zara`

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
