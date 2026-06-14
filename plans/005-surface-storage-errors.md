# Plan 005: Surface schedule-persistence failures to the user instead of silently swallowing them

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 2bc3c67..HEAD -- composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/ui/screens/MainViewModel.kt composeApp/src/androidMain/kotlin/com/jupiterp/jupiterpmobile/data/storage/AndroidStorage.kt composeApp/src/appleMain/kotlin/com/jupiterp/jupiterpmobile/data/storage/IOSStorage.kt`
> If any changed since this plan was written, compare against the "Current
> state" excerpts before proceeding; on a mismatch, STOP.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED
- **Depends on**: none (but if doing plan 006 too, do **006 after 005** — both edit `ScheduleRepository.kt`)
- **Category**: bug
- **Planned at**: commit `2bc3c67`, 2026-06-14

## Why this matters

When the app fails to save a schedule, the failure is swallowed twice: the storage layer
catches the write exception and calls `printStackTrace()` (so `updateAppData` returns as if
it succeeded), and the repository's own `try/catch` would also only `printStackTrace()`. On
a phone, stderr goes nowhere — the user's schedule change is silently lost with zero
feedback, and they discover it only when their data reappears stale on next launch.

This plan makes the storage layer **propagate** write failures, has `ScheduleRepository`
turn them into a user-facing message on an error stream, and has `MainViewModel` show that
message via the snackbar mechanism it already uses for every other action. The result: a
failed save produces a visible "Couldn't save your changes" instead of invisible data loss.

## Current state

- Storage swallows write errors (so callers can't tell a save failed):
  ```kotlin
  // composeApp/src/androidMain/.../data/storage/AndroidStorage.kt:27-35
  override suspend fun saveAppData(data: AppData) {
      try {
          val jsonString = json.encodeToString(data)
          prefs.edit().putString(KEY_APP_DATA, jsonString).apply()
          _appData.value = data
      } catch (e: Exception) {
          e.printStackTrace()
      }
  }
  ```
  ```kotlin
  // composeApp/src/appleMain/.../data/storage/IOSStorage.kt:25-34
  override suspend fun saveAppData(data: AppData) {
      try {
          val jsonString = json.encodeToString(data)
          userDefaults.setObject(jsonString, forKey = KEY_APP_DATA)
          userDefaults.synchronize()
          _appData.value = data
      } catch (e: Exception) {
          e.printStackTrace()
      }
  }
  ```

- The repository's persistence is fire-and-forget on a hardcoded scope, and its catch only
  prints:
  ```kotlin
  // composeApp/src/commonMain/.../data/repository/ScheduleRepository.kt:26-41
  class ScheduleRepository(
      private val storage: LocalStorage
  ) {
      private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      ...
  ```
  ```kotlin
  // ScheduleRepository.kt:260-272 (persistCurrentSchedule; persistSavedSchedules at 277-288 is identical shape)
  private fun persistCurrentSchedule() {
      val selections = _currentSelections.value
      val counter = colorCounter
      scope.launch {
          try {
              storage.updateAppData {
                  it.copy(currentSchedule = selections, colorCounter = counter)
              }
          } catch (e: Exception) {
              e.printStackTrace()
          }
      }
  }
  ```
  (The `init` block load at lines 45-56 has the same swallowing catch.)

- `updateAppData` is the shared, mutex-guarded read-modify-write in
  `composeApp/src/commonMain/.../data/storage/LocalStorage.kt:43-51` — it does NOT catch, so
  once the platform `saveAppData` stops swallowing, the exception reaches the repository.

- `MainViewModel` already has a snackbar channel and a private `showSnackbar`:
  ```kotlin
  // composeApp/src/commonMain/.../ui/screens/MainViewModel.kt:88-89, 446-454
  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()
  ...
  private fun showSnackbar(message: String) {
      _snackbarMessage.value = message
      snackbarJob?.cancel()
      snackbarJob = viewModelScope.launch { delay(3000); _snackbarMessage.value = null }
  }
  ```
  `MainViewModel`'s constructor already receives `scheduleRepository` (line 28-31).

### Conventions to follow

- The repository already uses `MutableStateFlow` + `asStateFlow()` for its public reactive
  state — mirror that style for the new error stream but use `MutableSharedFlow` (an error is
  an event, not a state).
- Test files: `kotlin.test`, in `composeApp/src/commonTest/kotlin/com/jupiterp/jupiterpmobile/`.
  The `commonTest` source set can see `commonMain`'s coroutine dependencies and the public
  `MutexGuardedStorage` / `AppData` types. Reuse the `course(...)`/`section(...)` builder
  pattern from `ScheduleComputationTest` in `ComposeAppCommonTest.kt:146-216`.

## Commands you will need

| Purpose         | Command                                                                                       | Expected on success |
|-----------------|-----------------------------------------------------------------------------------------------|---------------------|
| Run new test    | `./gradlew :composeApp:testDebugUnitTest --tests "*ScheduleRepositoryErrorTest*" --console=plain` | `BUILD SUCCESSFUL` |
| Full suite      | `./gradlew :composeApp:testDebugUnitTest --console=plain`                                      | `BUILD SUCCESSFUL`  |
| Android compile | `./gradlew :composeApp:assembleDebug --console=plain`                                          | `BUILD SUCCESSFUL`  |

## Scope

**In scope**:
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt`
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/ui/screens/MainViewModel.kt`
- `composeApp/src/androidMain/kotlin/com/jupiterp/jupiterpmobile/data/storage/AndroidStorage.kt`
- `composeApp/src/appleMain/kotlin/com/jupiterp/jupiterpmobile/data/storage/IOSStorage.kt`
- `composeApp/src/commonTest/kotlin/com/jupiterp/jupiterpmobile/ScheduleRepositoryErrorTest.kt` (create)

**Out of scope** (do NOT touch):
- `PreferencesRepository.kt` — its `printStackTrace` calls are for the dark-mode preference
  (low stakes) and wiring them to UI requires the prefs repo in the ViewModel; leave them as a
  separate follow-up.
- The `loadAppDataSync` catch in both storage classes — returning a default `AppData()` on a
  corrupt read is acceptable behavior; do not change the read path.
- `GeneratorViewModel` and the generator's own persistence, if any.
- The Koin module (`AppModule.kt`) — the new constructor parameter has a default, so DI is
  unaffected; do not edit it.

## Git workflow

- Branch: `advisor/005-surface-storage-errors`
- Commit per logical unit is fine; short imperative messages.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Stop the storage layer from swallowing write failures

In `AndroidStorage.kt`, change `saveAppData` to let exceptions propagate (remove the
`try`/`catch` wrapper only — keep the body):

```kotlin
override suspend fun saveAppData(data: AppData) {
    val jsonString = json.encodeToString(data)
    prefs.edit().putString(KEY_APP_DATA, jsonString).apply()
    _appData.value = data
}
```

Make the identical change in `IOSStorage.kt` `saveAppData` (keep the `setObject` /
`synchronize` lines, drop the `try`/`catch`). Leave `loadAppDataSync` in both files unchanged.

**Verify**: `grep -A6 "override suspend fun saveAppData" composeApp/src/androidMain/kotlin/com/jupiterp/jupiterpmobile/data/storage/AndroidStorage.kt composeApp/src/appleMain/kotlin/com/jupiterp/jupiterpmobile/data/storage/IOSStorage.kt`
→ neither `saveAppData` contains `printStackTrace`.

### Step 2: Add an error stream to ScheduleRepository and make its scope injectable

In `ScheduleRepository.kt`:

1. Make the coroutine scope a constructor parameter with the current value as default (so
   production behavior is unchanged and tests can inject a deterministic scope):

   ```kotlin
   class ScheduleRepository(
       private val storage: LocalStorage,
       private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
   ) {
   ```
   Remove the old `private val scope = ...` line now that it's a parameter.

2. Add the error stream near the other state flows (import `kotlinx.coroutines.flow.MutableSharedFlow`
   and `kotlinx.coroutines.flow.asSharedFlow`):

   ```kotlin
   // Emits a user-facing message whenever a load/persist operation fails.
   private val _errors = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
   val errors: SharedFlow<String> = _errors.asSharedFlow()
   ```

3. Replace each `e.printStackTrace()` in this file (the `init` load catch and the two
   `persist*` catches) with an emission of a user-facing message, e.g.:
   - init load catch → `_errors.tryEmit("Couldn't load your saved schedules")`
   - `persistCurrentSchedule` catch → `_errors.tryEmit("Couldn't save your schedule changes")`
   - `persistSavedSchedules` catch → `_errors.tryEmit("Couldn't save your schedules")`

**Verify**: `grep -n "printStackTrace" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt`
→ no matches. `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

### Step 3: Show repository errors in the UI

In `MainViewModel.kt`, in the `init { ... }` block (currently lines 122-125, calling
`loadDepartments()` and `loadAllInstructors()`), add a collector that routes repository
errors into the existing snackbar:

```kotlin
viewModelScope.launch {
    scheduleRepository.errors.collect { showSnackbar(it) }
}
```

`showSnackbar` is a private method on the same class, so it is directly callable. No new
import beyond what the file already has (`viewModelScope`, `launch`, `collect` are already in use).

**Verify**: `grep -n "scheduleRepository.errors" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/ui/screens/MainViewModel.kt`
→ matches. `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

### Step 4: Add a deterministic test for the failure path

Create `composeApp/src/commonTest/kotlin/com/jupiterp/jupiterpmobile/ScheduleRepositoryErrorTest.kt`.
Use an injected `Dispatchers.Unconfined` scope so the fire-and-forget persist runs
synchronously, and a storage stub whose `saveAppData` always throws:

```kotlin
package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.jupiterpmobile.data.storage.AppData
import com.jupiterp.jupiterpmobile.data.storage.MutexGuardedStorage
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertNotNull

class ScheduleRepositoryErrorTest {

    private class ThrowingStorage : MutexGuardedStorage() {
        override suspend fun saveAppData(data: AppData) { throw RuntimeException("disk full") }
        override suspend fun loadAppData(): AppData = AppData()
        override fun getAppDataFlow(): Flow<AppData> = MutableStateFlow(AppData()).asStateFlow()
    }

    private fun course(code: String) = Course(
        courseCode = code, name = "Test", minCredits = 3, maxCredits = null,
        description = null, genEds = null, conditions = null, sections = null
    )

    private fun section() = Section(
        courseCode = "TEST100", sectionCode = "0101", instructors = emptyList(),
        meetings = emptyList(), openSeats = 10, totalSeats = 30, waitlist = 0, holdfile = null
    )

    @Test
    fun persistFailureEmitsUserFacingError() {
        val repo = ScheduleRepository(ThrowingStorage(), CoroutineScope(Dispatchers.Unconfined))
        repo.addSection(course("TEST100"), section())   // triggers persist -> save throws
        assertNotNull(repo.errors.replayCache.lastOrNull())
    }
}
```

**Verify**: `./gradlew :composeApp:testDebugUnitTest --tests "*ScheduleRepositoryErrorTest*" --console=plain`
→ `BUILD SUCCESSFUL`, the new test passes.

### Step 5: Confirm the whole suite is still green

**Verify**: `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`.

## Test plan

- New `ScheduleRepositoryErrorTest.persistFailureEmitsUserFacingError`: with a storage stub
  that throws on write and an `Unconfined` scope, adding a section must emit a non-null error
  message on `ScheduleRepository.errors`. This pins the new propagate-and-surface behavior
  that previously did not exist (the failure used to be swallowed).
- Structural pattern: the `course(...)`/`section(...)` builders mirror `ScheduleComputationTest`.
- Verification: full suite green (`./gradlew :composeApp:testDebugUnitTest`).

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `grep -rn "printStackTrace" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/repository/ScheduleRepository.kt` → no matches
- [ ] `grep -n "printStackTrace" composeApp/src/androidMain/kotlin/com/jupiterp/jupiterpmobile/data/storage/AndroidStorage.kt` → only the `loadAppDataSync` one remains (the `saveAppData` one is gone)
- [ ] `grep -n "scheduleRepository.errors" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/ui/screens/MainViewModel.kt` → matches
- [ ] `./gradlew :composeApp:testDebugUnitTest --tests "*ScheduleRepositoryErrorTest*" --console=plain` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL` (whole suite)
- [ ] `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- Removing the storage `try/catch` produces a compile error (e.g. a now-required checked
  type) — report rather than re-adding the swallow.
- The new test is flaky/non-deterministic (passes sometimes) — the `Unconfined` assumption
  may not hold on this Kotlin/coroutines version; report so the test can be reworked with a
  test dispatcher instead of guessing.
- Adding the `scope` constructor parameter breaks the Koin wiring or any other caller of
  `ScheduleRepository(...)` you find via `grep -rn "ScheduleRepository(" composeApp/src` — the
  default should prevent this, so a break means an unexpected call site.

## Maintenance notes

- `PreferencesRepository` still uses `printStackTrace` for the dark-mode preference; wiring it
  to UI is a deliberate follow-up (it would need the prefs repo exposed where the snackbar
  lives). Left out to keep this change focused.
- If a future change makes the repository scope non-injectable again, the deterministic test
  in Step 4 will break — keep the constructor default.
- A reviewer should confirm the snackbar text is user-appropriate (no raw exception text) and
  that `replay = 1` doesn't cause a stale error to flash on a fresh ViewModel subscription in
  normal use (it only replays if an error actually occurred before subscription).
