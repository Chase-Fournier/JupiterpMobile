package com.jupiterp.data.model

import com.jupiterp.domain.model.*

/**
 * Mappers to convert API response models to domain models
 *
 * Meeting string formats from API:
 * - In-person: "Days-StartTime-EndTime-Building-Room" (e.g., "TuTh-11:00am-12:15pm-CSI-1115")
 * - Online sync: "Days-StartTime-EndTime-OnlineSync"
 * - Online async: "OnlineAsync"
 * - Unspecified: "Unspecified"
 */

fun CourseResponse.toDomain(): Course = Course(
    courseCode = courseCode,
    name = name,
    minCredits = minCredits,
    maxCredits = maxCredits,
    description = description,
    genEds = genEds,
    conditions = conditions,
    sections = sections?.map { it.toDomain() }
)

fun SectionResponse.toDomain(): Section = Section(
    courseCode = courseCode,
    sectionCode = secCode,
    instructors = instructors,
    meetings = meetings.map { parseMeetingString(it) },
    openSeats = openSeats,
    totalSeats = totalSeats,
    waitlist = waitlist,
    holdfile = holdfile
)

/**
 * Parse a meeting string from the API into a ClassMeeting domain object
 *
 * Examples:
 * - "TuTh-11:00am-12:15pm-CSI-1115" -> InPerson
 * - "MWF-9:00am-9:50am-OnlineSync" -> OnlineSync
 * - "OnlineAsync" -> OnlineAsync
 * - "Unspecified" -> TBA
 */
fun parseMeetingString(meeting: String): ClassMeeting {
    val trimmed = meeting.trim()

    return when {
        trimmed.equals("OnlineAsync", ignoreCase = true) -> ClassMeeting.OnlineAsync
        trimmed.equals("Unspecified", ignoreCase = true) -> ClassMeeting.TBA
        trimmed.contains("-") -> parseMeetingWithTime(trimmed)
        else -> ClassMeeting.Unknown
    }
}

/**
 * Parse a meeting string that contains time information
 * Format: "Days-StartTime-EndTime-Building-Room" or "Days-StartTime-EndTime-OnlineSync"
 */
private fun parseMeetingWithTime(meeting: String): ClassMeeting {
    val parts = meeting.split("-")

    // Need at least: Days, StartTime, EndTime, and either OnlineSync or Building-Room
    if (parts.size < 4) return ClassMeeting.Unknown

    val days = parts[0]
    val startTimeStr = parts[1]
    val endTimeStr = parts[2]

    val startTime = parseTimeToFloat(startTimeStr) ?: return ClassMeeting.Unknown
    val endTime = parseTimeToFloat(endTimeStr) ?: return ClassMeeting.Unknown

    val classtime = Classtime(
        days = days,
        start = startTime,
        end = endTime
    )

    // Check if it's online sync
    if (parts[3].equals("OnlineSync", ignoreCase = true)) {
        return ClassMeeting.OnlineSync(classtime = classtime)
    }

    // Otherwise it's in-person: Building-Room (parts 3 and 4)
    val building = parts[3]
    val room = if (parts.size >= 5) parts[4] else null

    return ClassMeeting.InPerson(
        classtime = classtime,
        location = Location(
            building = building,
            room = room
        )
    )
}

/**
 * Parse a time string like "11:00am" or "3:30pm" to a float
 * Returns the time as decimal hours (e.g., 11.0 for 11:00am, 15.5 for 3:30pm)
 */
fun parseTimeToFloat(timeStr: String): Float? {
    val normalized = timeStr.lowercase().trim()

    val isPm = normalized.endsWith("pm")
    val isAm = normalized.endsWith("am")

    if (!isPm && !isAm) return null

    val timeWithoutSuffix = normalized.dropLast(2)
    val parts = timeWithoutSuffix.split(":")

    if (parts.isEmpty()) return null

    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0

    var result = hours.toFloat() + (minutes.toFloat() / 60f)

    // Convert to 24-hour format
    if (isPm && hours != 12) {
        result += 12f
    } else if (isAm && hours == 12) {
        result = minutes.toFloat() / 60f // 12:XX am is 0:XX
    }

    return result
}

/**
 * Format a float time back to a display string
 */
fun formatTimeFromFloat(time: Float): String {
    val hours24 = time.toInt()
    val minutes = ((time - hours24) * 60).toInt()

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

fun InstructorResponse.toDomain(): Instructor = Instructor(
    name = name,
    slug = slug,
    averageRating = averageRating
)

fun DepartmentResponse.toDomain(): Department = Department(
    code = deptCode,
    name = name
)