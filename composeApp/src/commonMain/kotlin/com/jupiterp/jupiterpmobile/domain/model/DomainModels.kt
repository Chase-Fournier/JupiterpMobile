package com.jupiterp.jupiterpmobile.domain.model

import com.jupiterp.jupiterpmobile.toOneDecimalString
import kotlinx.serialization.Serializable

/**
 * Domain models for the Jupiterp app
 * These are the clean models used throughout the UI
 */

@Serializable
data class Course(
    val courseCode: String,
    val name: String,
    val minCredits: Int,
    val maxCredits: Int?,
    val description: String?,
    val genEds: List<String>?, // Gen-Ed codes like "DSSP", "DVUP", etc.
    val conditions: List<String>?, // Prerequisites, corequisites, etc.
    val sections: List<Section>?
) {
    val credits: String
        get() = if (maxCredits != null && maxCredits != minCredits) {
            "$minCredits-$maxCredits"
        } else {
            minCredits.toString()
        }

}

@Serializable
data class Section(
    val courseCode: String,
    val sectionCode: String,
    val instructors: List<String>,
    val meetings: List<ClassMeeting>,
    val openSeats: Int,
    val totalSeats: Int,
    val waitlist: Int,
    val holdfile: Int?
) {
    val isFull: Boolean
        get() = openSeats <= 0

    val hasWaitlist: Boolean
        get() = waitlist > 0

    val seatsDisplay: String
        get() = "$openSeats/$totalSeats"

    val instructorsDisplay: String
        get() = if (instructors.isEmpty()) "TBA" else instructors.joinToString(", ")

    /**
     * Get all time slots for conflict detection
     */
    val timeSlots: List<TimeSlot>
        get() = meetings.flatMap { meeting ->
            when (meeting) {
                is ClassMeeting.InPerson -> meeting.classtime.daysList.map { day ->
                    TimeSlot(day, meeting.classtime.start, meeting.classtime.end)
                }
                is ClassMeeting.OnlineSync -> meeting.classtime.daysList.map { day ->
                    TimeSlot(day, meeting.classtime.start, meeting.classtime.end)
                }
                else -> emptyList()
            }
        }
}

@Serializable
sealed class ClassMeeting {
    @Serializable
    data class InPerson(
        val classtime: Classtime,
        val location: Location
    ) : ClassMeeting()

    @Serializable
    data class OnlineSync(
        val classtime: Classtime
    ) : ClassMeeting()

    @Serializable
    data object OnlineAsync : ClassMeeting()

    @Serializable
    data object TBA : ClassMeeting()

    @Serializable
    data object Unknown : ClassMeeting()
}

@Serializable
data class Classtime(
    val days: String,
    val start: Float,
    val end: Float
) {
    val startFormatted: String
        get() = formatTime(start)

    val endFormatted: String
        get() = formatTime(end)

    val timeRange: String
        get() = "$startFormatted - $endFormatted"

    val daysList: List<DayOfWeek>
        get() = parseDays(days)

    val daysDisplay: String
        get() = days

    private fun formatTime(time: Float): String {
        val hours = time.toInt()
        val minutes = ((time - hours) * 60).toInt()
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

    private fun parseDays(days: String): List<DayOfWeek> {
        val result = mutableListOf<DayOfWeek>()
        var i = 0
        while (i < days.length) {
            when {
                days.substring(i).startsWith("Tu", ignoreCase = true) -> {
                    result.add(DayOfWeek.TUESDAY)
                    i += 2
                }
                days.substring(i).startsWith("Th", ignoreCase = true) -> {
                    result.add(DayOfWeek.THURSDAY)
                    i += 2
                }
                days.substring(i).startsWith("Sa", ignoreCase = true) -> {
                    result.add(DayOfWeek.SATURDAY)
                    i += 2
                }
                days.substring(i).startsWith("Su", ignoreCase = true) -> {
                    result.add(DayOfWeek.SUNDAY)
                    i += 2
                }
                days.substring(i).startsWith("M", ignoreCase = true) -> {
                    result.add(DayOfWeek.MONDAY)
                    i += 1
                }
                days.substring(i).startsWith("W", ignoreCase = true) -> {
                    result.add(DayOfWeek.WEDNESDAY)
                    i += 1
                }
                days.substring(i).startsWith("F", ignoreCase = true) -> {
                    result.add(DayOfWeek.FRIDAY)
                    i += 1
                }
                else -> i += 1
            }
        }
        return result
    }
}

enum class DayOfWeek(val short: String, val full: String, val column: Int) {
    MONDAY("M", "Monday", 0),
    TUESDAY("Tu", "Tuesday", 1),
    WEDNESDAY("W", "Wednesday", 2),
    THURSDAY("Th", "Thursday", 3),
    FRIDAY("F", "Friday", 4),
    SATURDAY("Sa", "Saturday", 5),
    SUNDAY("Su", "Sunday", 6);

    val displayName: String get() = full
}

@Serializable
data class Location(
    val building: String,
    val room: String?
) {
    val display: String
        get() = if (room != null) "$building $room" else building
}

@Serializable
data class Instructor(
    val name: String,
    val slug: String,
    val averageRating: Float?
) {
    val ratingDisplay: String?
        get() = averageRating?.toOneDecimalString()
}

@Serializable
data class Department(
    val code: String,
    val name: String
) {
    val display: String
        get() = "$code - $name"
}

// Schedule-related models
@Serializable
data class ScheduleSelection(
    val course: Course,
    val section: Section,
    val colorIndex: Int,
    val isHovered: Boolean = false
)

@Serializable
data class StoredSchedule(
    val id: String,
    val name: String,
    val selections: List<ScheduleSelection>,
    val createdAt: Long,
    val updatedAt: Long
)

// Schedule block for rendering
data class ScheduleBlock(
    val selection: ScheduleSelection,
    val meeting: ClassMeeting.InPerson,
    val day: DayOfWeek,
    val startTime: Float,
    val endTime: Float,
    val colorIndex: Int
) {
    val duration: Float
        get() = endTime - startTime
}

// Also handle OnlineSync meetings for rendering
data class ScheduleBlockSync(
    val selection: ScheduleSelection,
    val meeting: ClassMeeting.OnlineSync,
    val day: DayOfWeek,
    val startTime: Float,
    val endTime: Float,
    val colorIndex: Int
) {
    val duration: Float
        get() = endTime - startTime
}

// Time conflict detection
data class TimeSlot(
    val day: DayOfWeek,
    val start: Float,
    val end: Float
) {
    fun overlaps(other: TimeSlot): Boolean {
        return day == other.day && start < other.end && end > other.start
    }
}

/**
 * All Gen-Ed codes at UMD
 */
object GenEdCodes {
    // Fundamental Studies
    const val FSAW = "FSAW" // Academic Writing
    const val FSAR = "FSAR" // Analytic Reasoning
    const val FSMA = "FSMA" // Math
    const val FSOC = "FSOC" // Oral Communication
    const val FSPW = "FSPW" // Professional Writing

    // Distributive Studies
    const val DSHS = "DSHS" // History and Social Sciences
    const val DSHU = "DSHU" // Humanities
    const val DSNS = "DSNS" // Natural Sciences
    const val DSNL = "DSNL" // Natural Sciences with Lab
    const val DSSP = "DSSP" // Scholarship in Practice

    // I-Series
    const val SCIS = "SCIS" // I-Series

    // Diversity
    const val DVUP = "DVUP" // Understanding Plural Societies
    const val DVCC = "DVCC" // Cultural Competence

    val all = listOf(
        FSAW, FSAR, FSMA, FSOC, FSPW,
        DSHS, DSHU, DSNS, DSNL, DSSP,
        SCIS, DVUP, DVCC
    )

    val names = mapOf(
        FSAW to "Academic Writing",
        FSAR to "Analytic Reasoning",
        FSMA to "Math",
        FSOC to "Oral Communication",
        FSPW to "Professional Writing",
        DSHS to "History & Social Sciences",
        DSHU to "Humanities",
        DSNS to "Natural Sciences",
        DSNL to "Natural Sciences Lab",
        DSSP to "Scholarship in Practice",
        SCIS to "I-Series",
        DVUP to "Understanding Plural Societies",
        DVCC to "Cultural Competence"
    )
}