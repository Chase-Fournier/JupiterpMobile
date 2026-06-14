# Plan 006: Harden the startup load against clobbering an early user mutation

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 2bc3c67..HEAD -- composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt`
> If the file changed since this plan was written, compare against the
> "Current state" excerpts before proceeding; on a mismatch, STOP. Note: if
> plan 005 has already landed, the `init`/persist excerpts will differ
> (printStackTrace replaced by error emission) — that is expected; the
> `userMutated`/`colorCounter`/`init`-apply logic this plan targets is
> unchanged by 005, so proceed using the live code for those parts.

## Status

- **Priority**: P3
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none — but **edits the same file as plan 005**; if both are planned, land 005 first to avoid a merge conflict.
- **Category**: bug
- **Planned at**: commit `2bc3c67`, 2026-06-14

## Why this matters

On launch, `ScheduleRepository`'s `init` block reads persisted data on a background
dispatcher and writes it into the schedule state flows. It guards that write with a plain
`var userMutated` flag so a user action taken before the load finishes isn't overwritten —
but the flag has no `@Volatile`/memory-visibility guarantee and the "check then apply" is
not atomic relative to a concurrent mutation on another thread. In the (narrow) window
between app start and the storage read completing, a fast user action can be silently
clobbered by the loaded data, or the background thread may not even observe that the user
mutated.

The window is small and the data loss is rare, but it is a real cross-thread hazard. This
plan closes the realistic cases with a minimal, low-risk change: make the flag `@Volatile`,
set it first in every mutation, and apply the loaded state through the state flows' atomic
`update` so it can never overwrite data the user already added.

## Current state

File: `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt`

```kotlin
// lines 37-57
private var colorCounter = 0

// Set on the first user mutation so the async init load below can't
// clobber actions taken before it completes.
private var userMutated = false

init {
    // Load saved data on init
    scope.launch {
        try {
            val appData = storage.loadAppData()
            if (!userMutated) {
                _currentSelections.value = appData.currentSchedule
                _savedSchedules.value = appData.savedSchedules
                colorCounter = appData.colorCounter
            }
        } catch (e: Exception) {
            e.printStackTrace()   // (plan 005 may have replaced this line — leave whatever is here)
        }
    }
}
```

The mutating methods set `userMutated = true` partway through their bodies, e.g.:

```kotlin
// addSection, lines 81-83
userMutated = true
_currentSelections.update { it + selection }
persistCurrentSchedule()
```

Other mutators that set `userMutated = true`: `addCourseWithoutSection` (117), `removeSection`
(127), `removeCourse` (140), `clearSchedule` (151), `setCurrentSchedule` (162), `saveSchedule`
(183), `loadSchedule` (201), `deleteSchedule` (212), `renameSchedule` (223). In several of
these the assignment is **not** the first statement.

`MutableStateFlow.update { }` is an atomic compare-and-set loop, so a check performed inside
the `update` lambda is evaluated against the current value atomically with the write.

## Commands you will need

| Purpose         | Command                                                    | Expected on success |
|-----------------|------------------------------------------------------------|---------------------|
| Full suite      | `./gradlew :composeApp:testDebugUnitTest --console=plain`  | `BUILD SUCCESSFUL`  |
| Android compile | `./gradlew :composeApp:assembleDebug --console=plain`      | `BUILD SUCCESSFUL`  |

## Scope

**In scope**:
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt`

**Out of scope** (do NOT touch):
- The `colorCounter` semantics — it is a monotonic counter persisted in `AppData` so colors
  don't get reused after removals; do NOT replace it with a value derived from current
  selections.
- The storage classes, ViewModels, or DI.
- Converting the public mutating methods to `suspend` or wrapping them in a `Mutex` — that
  would change their synchronous contract and is explicitly not wanted here.

## Git workflow

- Branch: `advisor/006-startup-load-race`
- One commit; short imperative message (e.g. `Harden startup load against early-mutation clobber`).
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Make the mutation flag volatile

At the top of `ScheduleRepository.kt`, add the import:

```kotlin
import kotlin.concurrent.Volatile
```

and annotate the flag:

```kotlin
@Volatile
private var userMutated = false
```

(`kotlin.concurrent.Volatile` is available in common code on Kotlin 2.3. On targets without a
volatile concept it is a documented no-op, which is fine.)

**Verify**: `grep -n "@Volatile" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt`
→ matches.

### Step 2: Set the flag first in every mutator

In each mutating method listed in "Current state", move `userMutated = true` so it is the
**first** statement in the method body (before any read of `_currentSelections.value`,
`colorCounter`, or any flow update). This guarantees that once a mutation begins, the
background load can observe it. Do not change any other logic in those methods.

**Verify**: visually confirm each mutator starts with `userMutated = true`. Spot-check:
`grep -n "userMutated = true" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt`
→ one occurrence per mutator (10 total).

### Step 3: Apply loaded state atomically so it can't overwrite user data

Rewrite the `init` apply block to use `update` with a guard, so loaded data is only written
when the user has not mutated **and** the flow is still at its initial empty value:

```kotlin
val appData = storage.loadAppData()
_currentSelections.update { existing ->
    if (userMutated || existing.isNotEmpty()) existing else appData.currentSchedule
}
_savedSchedules.update { existing ->
    if (userMutated || existing.isNotEmpty()) existing else appData.savedSchedules
}
if (!userMutated) {
    colorCounter = appData.colorCounter
}
```

Keep the surrounding `try`/`catch` exactly as it currently is (including any error handling
plan 005 may have introduced — do not revert it).

**Verify**: `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

### Step 4: Confirm nothing regressed

**Verify**: `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`.

## Test plan

This is a concurrency-timing hardening; a race is not deterministically reproducible in a
unit test without elaborate dispatcher injection, which is out of scope. Verification is:

- The existing suite stays green (no behavioral regression for the normal, no-race path —
  the `update`-with-guard is equivalent to the old direct assignment when nothing has
  mutated and the flow is empty).
- Compilation succeeds with the new `@Volatile` import and `update` blocks.
- Code review confirms every mutator sets the flag first and the `init` apply is guarded.

(Do NOT add a flaky `Thread.sleep`/timing-based test to "prove" the race is gone — that is
worse than no test. If a deterministic test is desired later, it requires injecting a
controllable dispatcher, tracked as a follow-up.)

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `grep -n "import kotlin.concurrent.Volatile" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt` → matches
- [ ] `grep -n "@Volatile" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt` → matches
- [ ] The `init` block applies loaded state via `_currentSelections.update { ... }` (not direct `.value =`)
- [ ] `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- `kotlin.concurrent.Volatile` does not resolve on this Kotlin version — report; do not
  substitute a JVM-only `@Volatile` (it would break the iOS/native compilation).
- Moving `userMutated = true` to the first line of a mutator changes a method's result
  because it read state before setting the flag in a way that mattered — report the method.
- Any existing test fails after the `init` rewrite — the guarded `update` should be
  behavior-preserving for the non-race path, so a failure means an unexpected interaction.

## Maintenance notes

- A fully airtight fix would confine all mutable-state access to a single thread (or a lock),
  but that conflicts with the synchronous public API and isn't worth it for this rare window.
  The residual after this plan is benign (e.g. an *empty* mutation racing the load), which is
  documented here intentionally.
- If the public mutators are ever made `suspend` (e.g. to await persistence), revisit this —
  a `Mutex` shared between `init` and the mutators would then be the clean solution and could
  replace the `@Volatile`/guard approach.
- A reviewer should confirm `colorCounter` is still only assigned on a genuine fresh load
  (`!userMutated`), preserving monotonic color assignment.
