# Plan 007: Consolidate the duplicated 12-hour time-formatting logic into one helper

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 2bc3c67..HEAD -- composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/model/Mappers.kt composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/domain/model/DomainModels.kt`
> If either changed since this plan was written, compare against the "Current
> state" excerpts before proceeding; on a mismatch, STOP.

## Status

- **Priority**: P3
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (best done after plan 002, which adds more time-formatting tests as a safety net, but not required)
- **Category**: tech-debt
- **Planned at**: commit `2bc3c67`, 2026-06-14

## Why this matters

The same "round float-hours to whole minutes, then format as a 12-hour clock" logic is
copy-pasted in two places that have already drifted in surface detail:
`formatTimeFromFloat` (data layer, renders `"3:30pm"`) and `Classtime.formatTime` (domain
layer, renders `"3:30 PM"`). They share the rounding and 12-hour-conversion math but differ
only in the separator and AM/PM casing. Duplicated math like this is where a future timezone
or rounding fix gets applied to one copy and not the other. This plan extracts the shared
logic into a single parameterized helper that both call, **with byte-for-byte identical
output** — the existing tests pin those outputs, so any drift fails the build.

(A third formatter, `formatIcsTime` in `Util.kt`, produces 24-hour `HHMM` for the iCalendar
format. It is intentionally different and is left out of scope.)

## Current state

`composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/model/Mappers.kt:140-160`
(public; returns e.g. `"11am"`, `"3:30pm"`):

```kotlin
fun formatTimeFromFloat(time: Float): String {
    val totalMinutes = (time * 60).roundToInt()
    val hours24 = totalMinutes / 60
    val minutes = totalMinutes % 60
    val isPm = hours24 >= 12
    val hours12 = when {
        hours24 == 0 -> 12
        hours24 > 12 -> hours24 - 12
        else -> hours24
    }
    val suffix = if (isPm) "pm" else "am"
    return if (minutes == 0) {
        "$hours12$suffix"
    } else {
        "$hours12:${minutes.toString().padStart(2, '0')}$suffix"
    }
}
```

`composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/domain/model/DomainModels.kt:118-136`
(private member of `Classtime`; returns e.g. `"11 AM"`, `"3:30 PM"`):

```kotlin
private fun formatTime(time: Float): String {
    val totalMinutes = (time * 60).roundToInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val period = if (hours >= 12) "PM" else "AM"
    val displayHour = when {
        hours == 0 -> 12
        hours > 12 -> hours - 12
        else -> hours
    }
    return if (minutes == 0) {
        "$displayHour $period"
    } else {
        "$displayHour:${minutes.toString().padStart(2, '0')} $period"
    }
}
```

`formatTime` is used by `Classtime.startFormatted`/`endFormatted` (DomainModels.kt:103-107).
`formatTimeFromFloat` is **public** and referenced by tests
(`ComposeAppCommonTest.kt:3` imports it; `roundTripsTwentyAndFiftyMinuteTimes` asserts
`"7:20pm"`) and possibly UI code — keep its signature and output identical.

Existing regression guards (must keep passing unchanged):
- `TimeParsingTest.roundTripsTwentyAndFiftyMinuteTimes` → `formatTimeFromFloat` yields `"7:20pm"`, `"9:50am"`.
- `TimeParsingTest.classtimeFormatsRoundedMinutes` → `Classtime.startFormatted` yields `"7:20 PM"`.

## Commands you will need

| Purpose         | Command                                                    | Expected on success |
|-----------------|------------------------------------------------------------|---------------------|
| Full suite      | `./gradlew :composeApp:testDebugUnitTest --console=plain`  | `BUILD SUCCESSFUL`  |
| Android compile | `./gradlew :composeApp:assembleDebug --console=plain`      | `BUILD SUCCESSFUL`  |

## Scope

**In scope**:
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/domain/model/TimeFormat.kt` (create)
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/model/Mappers.kt`
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/domain/model/DomainModels.kt`

**Out of scope** (do NOT touch):
- `formatIcsTime` in `Util.kt` — it is a different (24-hour `HHMM`) format on purpose.
- The public signature or output of `formatTimeFromFloat` — keep both identical.
- Any UI component; this is an internal refactor with no behavior change.

## Git workflow

- Branch: `advisor/007-consolidate-time-formatting`
- One commit; short imperative message (e.g. `Extract shared 12-hour time formatter`).
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Create the shared helper

Create `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/domain/model/TimeFormat.kt`
(the `domain.model` package keeps it usable by both the domain and data layers without a
layering violation):

```kotlin
package com.jupiterp.jupiterpmobile.domain.model

import kotlin.math.roundToInt

/**
 * Formats a float number of hours-since-midnight as a 12-hour clock string.
 * Rounds to whole minutes first so float precision error (e.g. 19.3333 for
 * 7:20 PM) does not truncate a minute. Minutes are omitted when zero.
 *
 * @param suffixSeparator string placed between the time and the AM/PM label
 *        ("" for "3:30pm", " " for "3:30 PM").
 */
internal fun formatTwelveHourTime(
    time: Float,
    suffixSeparator: String,
    amLabel: String,
    pmLabel: String
): String {
    val totalMinutes = (time * 60).roundToInt()
    val hours24 = totalMinutes / 60
    val minutes = totalMinutes % 60
    val period = if (hours24 >= 12) pmLabel else amLabel
    val hours12 = when {
        hours24 == 0 -> 12
        hours24 > 12 -> hours24 - 12
        else -> hours24
    }
    val hourMinute = if (minutes == 0) {
        "$hours12"
    } else {
        "$hours12:${minutes.toString().padStart(2, '0')}"
    }
    return "$hourMinute$suffixSeparator$period"
}
```

**Verify**: `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

### Step 2: Delegate `formatTimeFromFloat` to the helper

In `Mappers.kt`, replace the body of `formatTimeFromFloat` (keep the public signature and the
doc comment) with a delegation that reproduces the exact `"11am"`/`"3:30pm"` output:

```kotlin
fun formatTimeFromFloat(time: Float): String =
    formatTwelveHourTime(time, suffixSeparator = "", amLabel = "am", pmLabel = "pm")
```

Add `import com.jupiterp.jupiterpmobile.domain.model.formatTwelveHourTime` if needed (same
module; `Mappers.kt` already imports other `domain.model` symbols). Remove the now-unused
`import kotlin.math.roundToInt` only if no other function in the file uses it — check first
with `grep -n "roundToInt" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/model/Mappers.kt`;
if `formatTimeFromFloat` was the only user, remove the import, otherwise leave it.

**Verify**: `./gradlew :composeApp:testDebugUnitTest --tests "*TimeParsingTest*" --console=plain`
→ `BUILD SUCCESSFUL` (the `"7:20pm"`/`"9:50am"` assertions still pass).

### Step 3: Delegate `Classtime.formatTime` to the helper

In `DomainModels.kt`, replace the body of the private `Classtime.formatTime` with a
delegation that reproduces the exact `"11 AM"`/`"3:30 PM"` output:

```kotlin
private fun formatTime(time: Float): String =
    formatTwelveHourTime(time, suffixSeparator = " ", amLabel = "AM", pmLabel = "PM")
```

(`formatTwelveHourTime` is in the same package, so no import is needed.) If `roundToInt` is
now unused in `DomainModels.kt`, remove its import — check with
`grep -n "roundToInt" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/domain/model/DomainModels.kt`
first (there may be other users; `toOneDecimalString` lives in `Util.kt`, not here).

**Verify**: `./gradlew :composeApp:testDebugUnitTest --tests "*TimeParsingTest*" --console=plain`
→ `BUILD SUCCESSFUL` (the `"7:20 PM"` assertion still passes).

### Step 4: Confirm the whole suite is green

**Verify**: `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`.

## Test plan

No new tests are required — the refactor is output-preserving and the two existing assertions
(`roundTripsTwentyAndFiftyMinuteTimes` for the `"pm"`/`"am"` form and
`classtimeFormatsRoundedMinutes` for the `" PM"` form) are precisely the regression guard.
Optionally, add one direct test of `formatTwelveHourTime` in a new or existing test class to
document the parameterization, but it is not necessary for correctness.

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/domain/model/TimeFormat.kt` exists and defines `formatTwelveHourTime`
- [ ] `grep -c "roundToInt" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/model/Mappers.kt` → the rounding math no longer appears inside `formatTimeFromFloat` (it delegates)
- [ ] `grep -n "formatTwelveHourTime" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/data/model/Mappers.kt composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/domain/model/DomainModels.kt` → both delegate to it
- [ ] `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL` (including the two existing format assertions)
- [ ] `./gradlew :composeApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- Either existing format assertion (`"7:20pm"` or `"7:20 PM"`) fails after delegation — the
  helper's output does not match a call site; do not change the test to match, fix the
  delegation parameters.
- `formatTimeFromFloat` turns out to have other callers expecting a different format than the
  test asserts (search `grep -rn "formatTimeFromFloat" composeApp/src`) — report before changing.

## Maintenance notes

- Any future change to time rounding or 12-hour conversion now lives in one place
  (`formatTwelveHourTime`); update it there and both display formats follow.
- `formatIcsTime` (24-hour ICS) was intentionally left separate. If a third 12-hour caller
  appears, route it through `formatTwelveHourTime` too.
- A reviewer should diff the rendered strings in the app (search results, schedule grid) before
  and after to confirm no visible change.
