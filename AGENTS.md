# PayFixationCalculator — Codex Instructions

## Purpose
Android/Kotlin/Jetpack Compose application for Central Government employee pay-fixation journeys across 4th, 5th, 6th and 7th CPC.

## Current development context
- Primary development branch: `v2-development`
- Recent known checkpoint: `abd94e5` — `FIX: Pass Starting Date Into 6th CPC Screen`
- The project uses multiple CPC journey screens and carries dates, increments, events and pay values between CPC transitions.
- Do not assume an older prototype repository is the current architecture.

## CPC journey
Treat the CPC journey as a continuous state transition:

4th CPC
  -> 5th CPC
  -> 6th CPC
  -> 7th CPC

Important principle:
- A CPC conversion must preserve the employee's accumulated service/pay journey.
- Existing increments and qualifying events must not disappear merely because a CPC conversion occurs.
- Dates passed from one screen to the next must remain the correct effective dates.

## Important screens/files
Known important screens include:
- `FifthToSixthCpcScreen.kt`
- `SixthToSeventhCpcScreen.kt`
- `HomeScreen.kt`

Use the existing implementation as the source of truth before introducing new architecture.

## Known 6th CPC context
- `HomeScreen.kt` passes the selected starting date into the 6th-to-7th CPC screen.
- The 6th CPC screen contains starting-date / 6th CPC pay-band / grade-pay / pay-in-pay-band inputs.
- `FifthToSixthCpcScreen.kt` is an important reference for chronological event/increment handling and CPC-boundary logic.
- Date propagation between screens is a critical area. Do not replace or bypass existing state flow without tracing it first.

## Critical business rules
- Do not remove an increment when converting from one CPC to another.
- Preserve the correct effective date of the final event/increment when moving to the next CPC.
- Support the 01 July 2015 increment case.
- Promotion and MACP events must retain their effective dates.
- DNI-related calculations must remain chronological.
- Do not silently change pay-fixation rules while fixing UI/state-flow problems.
- If a rule is ambiguous, stop and report the ambiguity rather than inventing a rule.

## Scope control
- Work only on the requested bug/feature.
- Do not refactor unrelated files.
- Do not redesign the CPC architecture unless explicitly requested.
- Do not modify `.idea/` files unless explicitly requested.
- Before editing, identify the smallest set of affected files.
- Prefer extending existing calculation/state functions over duplicating logic.

## Investigation protocol
For a non-trivial task:
1. Inspect the relevant screen and its immediate ViewModel/state/model/calculation dependencies.
2. Trace the data flow only as far as needed to answer the task.
3. State the suspected cause before making changes.
4. For large changes, provide an implementation plan before editing.
5. Implement the smallest coherent fix.
6. Run the narrowest relevant tests/build checks.
7. Review the final diff for unintended changes.

Do NOT re-read the entire repository unless the task genuinely requires it.

## Validation
When changing CPC/date/increment logic, test at least the relevant cases from:
- normal annual increment
- promotion before DNI
- promotion after DNI
- MACP event
- 01 July 2015 increment where applicable
- CPC conversion with accumulated increments
- continuation into the next CPC
- final date propagation into the next screen

Use existing project test/build commands when available. If no automated test exists for the affected logic, report exactly what was manually verified.

## Git discipline
- Never reset, revert, amend, or rewrite existing commits unless explicitly instructed.
- Never create a new branch unless explicitly instructed.
- Before a risky change, check `git status`.
- Keep unrelated local changes untouched.
- Do not commit unrelated `.idea` or generated files.
- After implementation, report:
  - changed files
  - concise explanation of the fix
  - validation performed
  - final `git diff --stat`
  - current `git status`

## Communication
Be concise and technical.
For investigation-only tasks: do not modify files.
For implementation tasks: do not expand scope.
If a test/build failure is unrelated to the change, report it instead of making unrelated fixes.

## Documentation
Use deeper project documentation when available:
- `docs/ARCHITECTURE.md` — architecture/data flow
- `docs/CPC_RULES.md` — business/pay-fixation rules
- `docs/TEST_CASES.md` — regression scenarios

Do not automatically read all documentation for every task. Read only the document relevant to the current task.
