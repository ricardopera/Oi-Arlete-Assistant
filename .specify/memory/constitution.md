<!--
Sync Impact Report

- Version change: 1.0.0 → 1.0.0 (content language updated to English)
- Modified principles: Titles/wording translated; semantics unchanged
- Added sections: none
- Removed sections: none
- Templates requiring updates:
  - ✅ .specify/templates/plan-template.md (still points to `/.specify/memory/constitution.md`)
  - ✅ .specify/templates/spec-template.md (no changes required)
  - ✅ .specify/templates/tasks-template.md (no changes required)
  - ✅ .specify/templates/agent-file-template.md (no changes required)
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): define original adoption date.
-->

# OiArlete Constitution

## Core Principles

### I. Module Simplicity (≤ 3)

- MUST start with at most 3 Gradle modules/projects: `:app` and up to 2 libraries (e.g., `:core`, `:data`).
- MUST defer new modules until there is proven need (build time, real reuse, dependency isolation, or ownership).
- Any exception MUST be documented in the plan’s "Complexity Tracking" with an objective rationale and a re‑evaluation deadline.

Rationale: Fewer modules reduce cognitive load, build time, and coordination cost, accelerating delivery.

### II. No Wasteful Abstraction (Android Native First)

- MUST use Android native APIs directly whenever possible: Intents, TextToSpeech, Activity Result APIs, WorkManager, `HttpURLConnection`/OkHttp, Room, etc.
- MUST avoid generic layers or wrappers (e.g., repositories or bureaucratic use‑cases) without measurable gains in testing, reuse, or isolation.
- MAY introduce abstractions only when: (a) the external contract is stable, and (b) there are ≥2 distinct consumers or clear portability requirements.

Rationale: Premature abstractions hide real integration problems and create unnecessary maintenance.

### III. Contract and Integration Tests Before Implementation

- MUST write contract and integration tests that fail before any implementation (Red → Green → Refactor).
- Contracts cover observable behaviors: intents, TTS, and HTTP calls (schemas, status codes, relevant side effects).
- Mocks/doubles are a last resort; prefer instrumented tests on emulator/device. When necessary, use MockWebServer only to scope cases not available in a real test environment.

Rationale: Validating contracts and integrations first ensures user value and reduces regressions.

### IV. Integration‑First

- MUST validate real integrations whenever possible:
  - Intents: `startActivityForResult`/Activity Result APIs, permissions, and callbacks.
  - TTS: `TextToSpeech` with configured language, fallback, and correct lifecycle.
  - HTTP: real calls against a test environment; if unavailable, use a local server/MockWebServer on a dedicated port.
- SHOULD automate smoke tests in CI with an Android emulator for critical paths (intents, basic TTS, HTTP ping).

Rationale: Integration issues are the main source of rework; prioritizing the real world surfaces risks earlier.

## Additional Requirements (Android)

- Platform/Stack:
  - Kotlin + Android SDK; UI with Jetpack Compose when applicable.
  - Minimal external dependencies; prefer official libraries (`androidx`, Google) over third‑party.
- Initial Structure:
  - Modules: `:app`, `:core` (utilities/contracts), `:data` (network/storage) — only if needed.
  - Avoid artificial layers; services may use native APIs directly.
- Network/HTTP:
  - Prefer `HttpURLConnection` or OkHttp directly — no extra wrappers.
  - API contracts documented from contract tests; schemas versioned when applicable.
- Logging/Observability:
  - Structured logging via Logcat with clear tags; avoid PII in logs.
  - Critical errors must include minimal reproducible context.

## Development Flow & Quality Gates

- Constitution Check is a mandatory GATE in implementation plans (plan.md):
  - ≤ 3 modules at the start, unless justified in Complexity Tracking.
  - Anti‑abstraction: no superfluous layers; justify any wrapper.
  - Contract/integration tests written before implementation (must initially fail).
  - Integration‑First: validate intents, TTS, and HTTP on emulator/real environment when possible.
- Pull Requests MUST include:
  - Links/paths to contract/integration tests and execution evidence (logs/screenshots when applicable).
  - Complexity justification for exceptions (modules >3, new layers, etc.).
- CI:
  - Full build + instrumented tests on Android emulator at least on PRs to main.
  - Mandatory integration smoke tests on critical paths.

## Governance

- Supremacy: This Constitution governs design, structure, and tests; conflicting practices are superseded by it.
- Amendment Process:
  - Changes occur via PR modifying this file.
  - Each PR must classify the change as MAJOR/MINOR/PATCH and include rationale and, if applicable, a migration plan.
  - Approval: at least 1 maintainer and consensus from active project contributors.
- Versioning (SemVer applied to the Constitution):
  - MAJOR: Removal/redefinition incompatible with principles or governance.
  - MINOR: Addition of a new principle/section or material expansion.
  - PATCH: Clarifications and non‑semantic adjustments.
- Compliance:
  - Reviewers MUST check the “Constitution Check” section in `plan.md` and test evidence.
  - Violations without justification must be rejected or re‑planned to simplify.
  - Quarterly review of this Constitution to ensure relevance and simplicity.

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE) | **Last Amended**: 2025-09-22
