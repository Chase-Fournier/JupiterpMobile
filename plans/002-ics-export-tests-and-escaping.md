# Plan 002: Make ICS export testable, escape all ICS text fields, and add characterization tests

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 2bc3c67..HEAD -- composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/Util.kt`
> If `Util.kt` changed since this plan was written, compare the "Current state"
> excerpts against the live code before proceeding; on a mismatch, treat it as
> a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none (pairs well with plan 003 CI)
- **Category**: tests (also fixes a security/correctness escaping bug)
- **Planned at**: commit `2bc3c67`, 2026-06-14

## Why this matters

Calendar export is a headline feature, and its logic is the most bug-prone in the
codebase — semester selection by month boundary, day-of-week date math that wraps
across month/year ends, and ICS text formatting — yet it has **zero test coverage**.
A wrong date silently exports recurring events to the wrong day, and the user only
finds out when their calendar is full of mistakes.

There is also a concrete escaping bug: in the ICS `SUMMARY` line, the course name is
escaped but the **location** is interpolated raw. A building or room string containing
a comma, semicolon, backslash, or newline (it comes from the remote API) breaks the
iCalendar field structure per RFC 5545 §3.3.11 — at best a malformed field, at worst an
injected calendar property.

This plan makes the date/semester logic injectable (so it can be tested without
mocking the clock), centralizes ICS text escaping and applies it to every text field,
and adds a characterization test suite that pins the current correct behavior.

## Current state

File: `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/Util.kt`

- `generateIcsContent` builds the calendar. The location is interpolated **unescaped**
  at line 58 while the name is escaped at line 29:

  ```kotlin
  // Util.kt:28-29  (name IS escaped)
  val courseName = selection.course.name
      .replace("\\", "\\\\").replace(",", "\\,").replace("\n", "\\n")
  ```
  ```kotlin
  // Util.kt:54-60  (location is NOT escaped)
  sb.append("BEGIN:VEVENT\r\n")
  sb.append("DTSTART:${dtStart}T${formatIcsTime(startTime)}00\r\n")
  sb.append("DTEND:${dtStart}T${formatIcsTime(endTime)}00\r\n")
  sb.append("RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=${semester.endIcs}\r\n")
  sb.append("SUMMARY:$courseCode ($sectionCode) - $location\r\n")   // <-- $location raw
  sb.append("DESCRIPTION:$courseName\r\n")
  ```

- The public entry point and the semester lookup (semester depends on the device clock
  via `currentDateInt()`, which is why the function is currently hard to test):

  ```kotlin
  // Util.kt:18-19
  fun generateIcsContent(selections: List<ScheduleSelection>): String? {
      val semester = activeSemester() ?: return null
  ```
  ```kotlin
  // Util.kt:89-98
  internal fun activeSemester(): SemesterDates? {
      val today = currentDateInt()
      val year = today / 10000
      val month = (today / 100) % 100
      return when {
          month < 4  -> SPRING[year]
          month < 11 -> FALL[year]
          else       -> SPRING[year + 1]
      }
  }
  ```

- The semester tables and the day-date math (note the month/year wraparound branch):

  ```kotlin
  // Util.kt:71-83
  internal data class SemesterDates(val firstMondayInt: Int, val endIcs: String)
  private val FALL = mapOf(
      2025 to SemesterDates(20250825, "20251217T235959Z"),
      2026 to SemesterDates(20260831, "20261211T235959Z"),
      2027 to SemesterDates(20270830, "20271217T235959Z"),
  )
  private val SPRING = mapOf(
      2026 to SemesterDates(20260126, "20260520T235959Z"),
      2027 to SemesterDates(20270125, "20270519T235959Z"),
      2028 to SemesterDates(20280124, "20280517T235959Z"),
  )
  ```
  ```kotlin
  // Util.kt:107-119  (currently private)
  private fun icsDateForDay(day: DayOfWeek, firstMondayInt: Int): String {
      val year = firstMondayInt / 10000
      val month = (firstMondayInt / 100) % 100
      val d = firstMondayInt % 100 + day.column
      val daysInMonth = daysInMonth(year, month)
      return if (d <= daysInMonth) {
          "$year${month.toString().padStart(2, '0')}${d.toString().padStart(2, '0')}"
      } else {
          val nextMonth = if (month < 12) month + 1 else 1
          val nextYear = if (month < 12) year else year + 1
          "$nextYear${nextMonth.toString().padStart(2, '0')}${(d - daysInMonth).toString().padStart(2, '0')}"
      }
  }
  ```

- `DayOfWeek.column` is `MONDAY=0, TUESDAY=1, WEDNESDAY=2, THURSDAY=3, FRIDAY=4,
  SATURDAY=5, SUNDAY=6` (see `domain/model/DomainModels.kt:178-185`). `formatIcsTime`
  (Util.kt:128-135) rounds float hours to `HHMM`. `daysInMonth` (Util.kt:121-126) is
  already covered by `UtilTest.daysInMonthHandlesLeapYears`.

### Test conventions to follow

Tests live in `composeApp/src/commonTest/kotlin/com/jupiterp/jupiterpmobile/` and use
`kotlin.test` (`@Test`, `assertEquals`, `assertTrue`, `assertNull`, `assertIs`). Model
the new file's structure and the domain-object builders on the existing
`ScheduleComputationTest` class in
`composeApp/src/commonTest/kotlin/com/jupiterp/jupiterpmobile/ComposeAppCommonTest.kt:146-216`,
which already defines `course(...)` and `section(...)` helpers and builds
`ScheduleSelection` / `ClassMeeting.InPerson` / `Classtime` / `Location` objects.

## Commands you will need

| Purpose         | Command                                                                                  | Expected on success |
|-----------------|------------------------------------------------------------------------------------------|---------------------|
| Run ICS tests   | `./gradlew :composeApp:testDebugUnitTest --tests "*IcsGenerationTest*" --console=plain`   | `BUILD SUCCESSFUL`  |
| Run full suite  | `./gradlew :composeApp:testDebugUnitTest --console=plain`                                 | `BUILD SUCCESSFUL`  |

(The full-suite command was verified passing at commit `2bc3c67`.)

## Scope

**In scope**:
- `composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/Util.kt` (refactor for
  testability + escaping)
- `composeApp/src/commonTest/kotlin/com/jupiterp/jupiterpmobile/IcsGenerationTest.kt` (create)

**Out of scope** (do NOT touch):
- The `FALL` / `SPRING` date tables — do not add or change semester dates; tests must use
  the values already there.
- `currentDateInt()` and its platform `actual`s — do not change how the real date is read.
- Any UI or repository code that calls `generateIcsContent`.

## Git workflow

- Branch: `advisor/002-ics-export-tests`
- Commit per logical unit is fine (e.g. one for the Util refactor+escaping, one for the
  tests); short imperative messages matching the repo log.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add a single ICS text-escaping helper and apply it to every text field

In `Util.kt`, add an internal helper (place it near the other ICS helpers, e.g. just
above `generateIcsContent`):

```kotlin
/**
 * Escapes a value for use in an iCalendar TEXT property (RFC 5545 §3.3.11):
 * backslash, semicolon, and comma are backslash-escaped; CR/LF become the
 * literal two-character sequence "\n" so a value can never break field or line
 * structure.
 */
internal fun escapeIcsText(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")
```

Then in `generateIcsContent`:
- Replace the inline name-escaping (lines 28-29) with `val courseName = escapeIcsText(selection.course.name)`.
- Escape the location before the `SUMMARY` append. The simplest correct change: where
  `location` is assigned in each `when` branch leave it raw, but at the `SUMMARY` append
  use `escapeIcsText(location)`:

  ```kotlin
  sb.append("SUMMARY:$courseCode ($sectionCode) - ${escapeIcsText(location)}\r\n")
  ```

  (Course code and section code are fixed-format identifiers, but if you prefer you may
  also wrap them in `escapeIcsText` — that is acceptable and changes nothing for valid
  codes.)

**Verify**: `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`
(existing tests still pass; no behavior change for inputs without special characters).

### Step 2: Make semester selection injectable

Change `activeSemester()` to take the date as a parameter defaulting to the real clock,
so tests can pass a fixed date. The no-arg call sites (`generateIcsContent` line 19,
`hasKnownSemesterDates` line 101) keep working unchanged because of the default:

```kotlin
internal fun activeSemester(today: Int = currentDateInt()): SemesterDates? {
    val year = today / 10000
    val month = (today / 100) % 100
    return when {
        month < 4  -> SPRING[year]
        month < 11 -> FALL[year]
        else       -> SPRING[year + 1]
    }
}
```

**Verify**: `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`.

### Step 3: Make the date helper and an ICS-building overload testable

1. Change `icsDateForDay` from `private` to `internal` (signature otherwise unchanged) so
   the wraparound math can be tested directly.
2. Split `generateIcsContent` so the body can run against a fixed semester. Keep the
   public function, and add an `internal` overload that takes the semester:

   ```kotlin
   fun generateIcsContent(selections: List<ScheduleSelection>): String? {
       val semester = activeSemester() ?: return null
       return generateIcsContent(selections, semester)
   }

   internal fun generateIcsContent(
       selections: List<ScheduleSelection>,
       semester: SemesterDates
   ): String {
       // ... the existing body, minus the first two lines that resolved `semester`,
       // and returning the built String (not String?) ...
   }
   ```

   Move the existing event-building loop into the internal overload unchanged except that
   it now receives `semester` as a parameter instead of computing it.

**Verify**: `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`.

### Step 4: Write the characterization tests

Create `composeApp/src/commonTest/kotlin/com/jupiterp/jupiterpmobile/IcsGenerationTest.kt`.
Use the domain-builder pattern from `ScheduleComputationTest`. Cover at least these cases
(expected values are derived from the tables in "Current state" — confirm against the live
tables during the drift check):

**Semester selection (`activeSemester(today)`):**
- `activeSemester(20260115)` → not null; `firstMondayInt == 20260126` (Jan → Spring 2026).
- `activeSemester(20260401)` → `firstMondayInt == 20260831` (April is `< 11` → Fall 2026 — the lower month boundary).
- `activeSemester(20261101)` → `firstMondayInt == 20270125` (Nov → Spring of next year — the upper boundary).
- `activeSemester(20261215)` → `firstMondayInt == 20270125` (Dec → Spring 2027).
- `assertNull(activeSemester(20990101))` (year absent from tables).

**Date math (`icsDateForDay`):**
- `icsDateForDay(DayOfWeek.MONDAY, 20260831) == "20260831"` (column 0, no offset).
- `icsDateForDay(DayOfWeek.TUESDAY, 20260831) == "20260901"` (Aug 31 + 1 wraps to Sep 1).
- `icsDateForDay(DayOfWeek.SUNDAY, 20260831) == "20260906"` (Aug 31 + 6 → Sep 6).
- `icsDateForDay(DayOfWeek.FRIDAY, 20260126) == "20260130"` (no wrap).
- `icsDateForDay(DayOfWeek.FRIDAY, 20271231) == "20280104"` (Dec 31 + 4 → year rollover to Jan 4 2028).

**Full ICS document (`generateIcsContent(selections, semester)`):** build one
`ScheduleSelection` for a course named e.g. `"Intro to CS"`, section `"0101"`, a single
`ClassMeeting.InPerson(Classtime("TuTh", 11f, 12.25f), Location("CSI", "1115"))`, and pass
`SemesterDates(20260831, "20261211T235959Z")`. Assert the result contains:
- `"BEGIN:VEVENT"` and `"END:VEVENT"`
- `"DTSTART:20260901T110000"` (first day = Tuesday = Aug 31 + 1)
- `"DTEND:20260901T121500"`
- `"RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261211T235959Z"`
- `"SUMMARY:"` line containing `"CSI 1115"`
- `"DESCRIPTION:Intro to CS"`

**Time rounding through the document:** a meeting `Classtime("M", 19f + 20f / 60f, 19f + 50f / 60f)`
in the document must produce `"T192000"` and `"T195000"` (rounded, not truncated to 19/49).

**Escaping:** build a meeting with `Location("CSI, Annex", "1115")` (display `"CSI, Annex 1115"`)
and a course name `"Algo, Intro"`; assert the output contains the escaped forms
`"CSI\\, Annex 1115"` (in Kotlin source: `"CSI\\, Annex 1115"`) in the SUMMARY and
`"DESCRIPTION:Algo\\, Intro"`. Add one case with a newline in the course name
(`"Line1\nLine2"`) and assert the output contains the literal `"Line1\\nLine2"` and does
**not** contain a raw `"\nLine2"` immediately after `DESCRIPTION:` (i.e. the newline did
not break the line).

**Verify**: `./gradlew :composeApp:testDebugUnitTest --tests "*IcsGenerationTest*" --console=plain`
→ `BUILD SUCCESSFUL`, all new tests pass.

## Test plan

- New file `IcsGenerationTest.kt` with the ~16 cases above: semester boundaries, date
  wraparound (month + year), full-document structure, time rounding, and field escaping
  (comma + newline).
- Structural pattern: `ScheduleComputationTest` in `ComposeAppCommonTest.kt`.
- Verification: `./gradlew :composeApp:testDebugUnitTest --console=plain` → all pass,
  including the new `IcsGenerationTest` cases, with no regression in the existing classes.

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `grep -n "escapeIcsText" composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/Util.kt` → defined once and used for both name and location
- [ ] `./gradlew :composeApp:testDebugUnitTest --tests "*IcsGenerationTest*" --console=plain` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :composeApp:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL` (whole suite green)
- [ ] `grep -c "@Test" composeApp/src/commonTest/kotlin/com/jupiterp/jupiterpmobile/IcsGenerationTest.kt` → ≥ 8
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- The live `FALL`/`SPRING` tables differ from the "Current state" excerpts (the expected
  test values above are derived from them and would be wrong) — recompute or report.
- Splitting `generateIcsContent` into two functions causes an overload-resolution error at
  a call site you did not expect (search shows callers beyond `Util.kt` and the new test).
- Any existing test in `ComposeAppCommonTest.kt` starts failing after the escaping change —
  that means escaping altered output for ordinary inputs; report the diff.
- A computed test expectation (e.g. a wraparound date) disagrees with the code's output and
  you cannot reconcile it from the excerpts — report rather than "adjusting" the assertion
  to match.

## Maintenance notes

- When new semesters are added to `FALL`/`SPRING`, add a corresponding `activeSemester`
  boundary test for that year.
- A reviewer should confirm `escapeIcsText` is applied at every `sb.append(...)` that
  interpolates a value derived from API/user data, and that the public `generateIcsContent`
  still returns `null` (not `""`) when `activeSemester()` is null.
- `formatIcsTime` and `daysInMonth` stay as-is; the new tests exercise `formatIcsTime`
  indirectly through document assertions.
