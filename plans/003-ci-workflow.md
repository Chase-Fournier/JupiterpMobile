# Plan 003: Add a CI workflow that runs the unit test suite on every push and PR

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `ls .github/workflows 2>/dev/null`
> If a workflow file already exists there, STOP and report — this plan assumes
> there is no CI yet and would otherwise clobber existing automation.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none (most valuable once plan 002 has added tests, but not required)
- **Category**: dx
- **Planned at**: commit `2bc3c67`, 2026-06-14

## Why this matters

The repo has a working test suite (`commonTest`) but nothing runs it automatically.
Regressions only surface if the solo developer remembers to run tests locally before a
commit. A minimal CI job that runs the existing tests on every push and pull request
turns the test suite into an actual safety net and is the prerequisite for trusting the
larger refactors deferred elsewhere (the 1500-line UI files).

This workflow runs the **Android unit-test task**, which on the JVM executes all of
`commonTest`. iOS test targets need a macOS runner and a simulator; they are intentionally
out of scope here to keep CI fast and free on Linux.

## Current state

- There is no `.github/` directory. Confirm with `ls -la .github 2>/dev/null` (expected:
  nothing / "No such file or directory").
- The build is Gradle with a wrapper (`./gradlew`, `gradlew.bat` present at repo root).
- Toolchain per `README.md:88-92`: **JDK 21**. The Gradle wrapper pins the Gradle version,
  so CI should not install Gradle separately — it must use `./gradlew`.
- Verified-working test command (run locally at `2bc3c67`, `BUILD SUCCESSFUL`):
  `./gradlew :composeApp:testDebugUnitTest`.

## Commands you will need

| Purpose                | Command                                                    | Expected on success |
|------------------------|------------------------------------------------------------|---------------------|
| Local test (same as CI)| `./gradlew :composeApp:testDebugUnitTest --console=plain`  | `BUILD SUCCESSFUL`  |
| YAML sanity check      | `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('ok')"` | prints `ok` |

(If `python3`/`yaml` is unavailable, skip the YAML check and instead re-read the file
carefully for indentation; do not add new dependencies to satisfy the check.)

## Scope

**In scope** (create only):
- `.github/workflows/ci.yml`

**Out of scope** (do NOT touch):
- Any Gradle file, source, or `gradle/wrapper/` content.
- Adding lint/format tooling (ktlint/detekt) or an `.editorconfig` — those are separate
  follow-ups; this plan is the test gate only.
- iOS/macOS CI jobs.

## Git workflow

- Branch: `advisor/003-ci-workflow`
- One commit; short imperative message (e.g. `Add CI workflow running unit tests`).
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Create the workflow file

Create `.github/workflows/ci.yml` with exactly this content:

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run unit tests
        run: ./gradlew :composeApp:testDebugUnitTest --console=plain --stacktrace

      - name: Upload test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-report
          path: composeApp/build/reports/tests/
          if-no-files-found: ignore
```

**Verify**: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('ok')"`
→ prints `ok` (or, if Python/yaml is unavailable, visually confirm 2-space indentation
and that the file matches the block above exactly).

### Step 2: Prove the command CI will run actually passes locally

This is the key correctness check — CI runs the same command, so a green local run is the
best available signal that CI will be green.

**Verify**: `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`.

## Test plan

No application tests are added by this plan. The "test" is that the workflow invokes a
command already proven to pass:

- `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL` locally.
- The workflow YAML parses without error.

(Full end-to-end verification — the job running on GitHub — happens when the operator
pushes the branch; that is outside the executor's environment and must not be forced.)

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `.github/workflows/ci.yml` exists
- [ ] YAML parses (`python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"` exits 0), or indentation verified by hand if Python/yaml unavailable
- [ ] `grep -n "testDebugUnitTest" .github/workflows/ci.yml` → matches
- [ ] `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`
- [ ] No files outside `.github/workflows/ci.yml` are modified (`git status`)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- A workflow file already exists under `.github/workflows/` (drift check) — do not
  overwrite or merge without instruction.
- The local `testDebugUnitTest` run fails — CI would fail too; report the failure rather
  than papering over it in the workflow (e.g. do NOT add `|| true` or `continue-on-error`).

## Maintenance notes

- When iOS-specific logic gains tests, add a second job on `macos-latest` running
  `./gradlew :composeApp:iosSimulatorArm64Test` (separate, can be allowed to be slower).
- Natural follow-ups once this is green: add a ktlint/detekt check job and an
  `assembleDebug` build job. Keep them as separate jobs so a style failure doesn't mask a
  test failure.
- A reviewer should confirm the workflow triggers on PRs against `main` and that the JDK
  version matches the toolchain in `README.md`.
