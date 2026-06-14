package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import kotlin.math.roundToInt

fun Float.toOneDecimalString(): String {
    val rounded = (this * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}.0" else "$rounded"
}

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

/**
 * Builds the .ics text for the current selections, or null if the active
 * semester's dates aren't in the lookup tables (callers should surface that
 * instead of exporting events on wrong dates).
 */
fun generateIcsContent(selections: List<ScheduleSelection>): String? {
    val semester = activeSemester() ?: return null
    return generateIcsContent(selections, semester)
}

internal fun generateIcsContent(
    selections: List<ScheduleSelection>,
    semester: SemesterDates
): String {
    val sb = StringBuilder()
    sb.append("BEGIN:VCALENDAR\r\n")
    sb.append("VERSION:2.0\r\n")
    sb.append("PRODID:-//Jupiterp//EN\r\n")
    sb.append("CALSCALE:GREGORIAN\r\n")
    for (selection in selections) {
        val courseCode = selection.course.courseCode
        val sectionCode = selection.section.sectionCode
        val courseName = escapeIcsText(selection.course.name)
        for (meeting in selection.section.meetings) {
            val days: List<DayOfWeek>
            val startTime: Float
            val endTime: Float
            val location: String
            when (meeting) {
                is ClassMeeting.InPerson -> {
                    days = meeting.classtime.daysList
                    startTime = meeting.classtime.start
                    endTime = meeting.classtime.end
                    location = meeting.location.display
                }
                is ClassMeeting.OnlineSync -> {
                    days = meeting.classtime.daysList
                    startTime = meeting.classtime.start
                    endTime = meeting.classtime.end
                    location = "Online Sync"
                }
                else -> continue
            }
            if (days.isEmpty()) continue
            val byDay = days.joinToString(",") { it.toIcsDayCode() }
            val firstDay = days.minByOrNull { it.column } ?: continue
            val dtStart = icsDateForDay(firstDay, semester.firstMondayInt)
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("DTSTART:${dtStart}T${formatIcsTime(startTime)}00\r\n")
            sb.append("DTEND:${dtStart}T${formatIcsTime(endTime)}00\r\n")
            sb.append("RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=${semester.endIcs}\r\n")
            sb.append("SUMMARY:$courseCode ($sectionCode) - ${escapeIcsText(location)}\r\n")
            sb.append("DESCRIPTION:$courseName\r\n")
            sb.append("END:VEVENT\r\n")
        }
    }
    sb.append("END:VCALENDAR")
    return sb.toString()
}

// Jan–Mar  → Spring of this year
// Apr–Oct  → Fall of this year
// Nov–Dec  → Spring of next year
// To add a future semester, add its year to the relevant map.
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

/**
 * Returns null when the active semester's dates aren't in the tables above —
 * falling back to another semester would silently export wrong dates.
 */
internal fun activeSemester(today: Int = currentDateInt()): SemesterDates? {
    val year = today / 10000
    val month = (today / 100) % 100
    return when {
        month < 4  -> SPRING[year]
        month < 11 -> FALL[year]
        else       -> SPRING[year + 1]
    }
}

/** True when calendar export has valid dates for the current semester. */
fun hasKnownSemesterDates(): Boolean = activeSemester() != null

/**
 * Returns the YYYYMMDD string of the first occurrence of [day] at or after [firstMondayInt].
 * firstMondayInt must be a Monday; day.column offsets by 0–6 days.
 */
internal fun icsDateForDay(day: DayOfWeek, firstMondayInt: Int): String {
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

internal fun daysInMonth(year: Int, month: Int) = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 30
}

private fun formatIcsTime(decimal: Float): String {
    // Round to whole minutes; truncating float hours drops a minute for
    // times like X:20 and X:50 (e.g. 19.3333 -> 19:19)
    val totalMinutes = (decimal * 60).roundToInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h.toString().padStart(2, '0')}${m.toString().padStart(2, '0')}"
}

private fun DayOfWeek.toIcsDayCode() = when (this) {
    DayOfWeek.MONDAY -> "MO"
    DayOfWeek.TUESDAY -> "TU"
    DayOfWeek.WEDNESDAY -> "WE"
    DayOfWeek.THURSDAY -> "TH"
    DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"
    DayOfWeek.SUNDAY -> "SU"
}