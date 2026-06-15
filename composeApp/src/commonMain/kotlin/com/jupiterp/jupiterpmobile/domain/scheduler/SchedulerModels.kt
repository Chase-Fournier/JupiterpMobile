package com.jupiterp.jupiterpmobile.domain.scheduler

import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection

/**
 * Hard constraints a generated schedule must satisfy. All times are minutes
 * since midnight (e.g. 9:30 AM = 570).
 */
data class HardConstraints(
    val earliestStartMinutes: Int? = null,
    val latestEndMinutes: Int? = null,
    val daysOff: Set<DayOfWeek> = emptySet(),
    val onlyOpenSeats: Boolean = true,
    val minGapMinutes: Int = 0,
    /** Drop schedules whose guaranteed credit total is below this floor. */
    val minCredits: Int? = null
)

/**
 * One course to schedule, plus how strictly to place it.
 *
 * - [required] courses appear in every generated schedule; optional ones are
 *   included only when they fit and may be dropped.
 * - [pin] narrows the course to a single section or to one professor's
 *   sections, and overrides the per-section filters in [HardConstraints]
 *   (the override is surfaced as a [PinNotice]).
 */
data class CourseRequest(
    val course: Course,
    val required: Boolean = true,
    val pin: SectionPin = SectionPin.None
)

sealed interface SectionPin {
    data object None : SectionPin
    data class BySection(val sectionCode: String) : SectionPin
    data class ByInstructor(val name: String) : SectionPin
}

/**
 * Raised when a pinned section was kept despite violating one or more active
 * per-section filters (the pin wins, but the user is told what it overrode).
 */
data class PinNotice(
    val courseCode: String,
    val sectionCode: String,
    val overriddenFilters: List<OverriddenFilter>
)

enum class OverriddenFilter {
    EARLIEST_START,
    LATEST_END,
    DAY_OFF,
    OPEN_SEATS
}

/**
 * Metrics computed once per generated schedule so re-sorting never requires
 * regeneration. Times are minutes since midnight; null when the schedule has
 * no timed meetings (all async/TBA).
 */
data class ScheduleMetrics(
    val avgInstructorRating: Float?,
    val ratedSectionCount: Int,
    val sectionCount: Int,
    val minCredits: Int,
    val maxCredits: Int,
    val daysWithClasses: Int,
    val totalGapMinutes: Int,
    val earliestStartMinutes: Int?,
    val latestEndMinutes: Int?,
    val minOpenSeats: Int
)

data class GeneratedSchedule(
    val selections: List<ScheduleSelection>,
    val metrics: ScheduleMetrics
)

data class GenerationResult(
    val schedules: List<GeneratedSchedule>,
    /** True when the search stopped at the result or node cap; more schedules may exist. */
    val truncated: Boolean,
    /** Course codes that had zero sections passing the constraints (generation is impossible). */
    val coursesWithNoValidSections: List<String>,
    /** Pinned sections that were forced in despite violating active filters. */
    val pinNotices: List<PinNotice> = emptyList()
)

/**
 * A single constraint loosened from a [HardConstraints], used to explain
 * zero-result generations ("allowing Friday classes would give 12 schedules").
 */
data class Relaxation(
    val kind: RelaxationKind,
    val day: DayOfWeek? = null,
    val constraints: HardConstraints
)

enum class RelaxationKind {
    EARLIEST_START,
    LATEST_END,
    DAY_OFF,
    OPEN_SEATS,
    MIN_GAP,
    MIN_CREDITS
}

/** Every constraint set that differs from [constraints] by removing exactly one restriction. */
fun singleRelaxations(constraints: HardConstraints): List<Relaxation> {
    val relaxations = mutableListOf<Relaxation>()
    if (constraints.earliestStartMinutes != null) {
        relaxations += Relaxation(
            RelaxationKind.EARLIEST_START,
            constraints = constraints.copy(earliestStartMinutes = null)
        )
    }
    if (constraints.latestEndMinutes != null) {
        relaxations += Relaxation(
            RelaxationKind.LATEST_END,
            constraints = constraints.copy(latestEndMinutes = null)
        )
    }
    for (day in constraints.daysOff) {
        relaxations += Relaxation(
            RelaxationKind.DAY_OFF,
            day = day,
            constraints = constraints.copy(daysOff = constraints.daysOff - day)
        )
    }
    if (constraints.onlyOpenSeats) {
        relaxations += Relaxation(
            RelaxationKind.OPEN_SEATS,
            constraints = constraints.copy(onlyOpenSeats = false)
        )
    }
    if (constraints.minGapMinutes > 0) {
        relaxations += Relaxation(
            RelaxationKind.MIN_GAP,
            constraints = constraints.copy(minGapMinutes = 0)
        )
    }
    if (constraints.minCredits != null) {
        relaxations += Relaxation(
            RelaxationKind.MIN_CREDITS,
            constraints = constraints.copy(minCredits = null)
        )
    }
    return relaxations
}
