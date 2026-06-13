package com.jupiterp.jupiterpmobile.domain.scheduler

import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Generates every conflict-free combination of one section per course, subject
 * to [HardConstraints]. Pure and synchronous — callers run it on a background
 * dispatcher. Output is bounded by [DEFAULT_MAX_RESULTS] and an internal node
 * cap so pathological inputs stay finite.
 */
object ScheduleGenerator {

    const val DEFAULT_MAX_RESULTS = 200

    /** Hard cap on explored search nodes so degenerate inputs can't hang the app. */
    private const val MAX_NODES = 500_000

    internal data class MinuteSlot(val day: DayOfWeek, val start: Int, val end: Int) {
        fun conflictsWith(other: MinuteSlot, minGapMinutes: Int): Boolean =
            day == other.day &&
                    start - minGapMinutes < other.end &&
                    end + minGapMinutes > other.start
    }

    private data class Candidate(
        val course: Course,
        val section: Section,
        val slots: List<MinuteSlot>
    )

    fun generate(
        courses: List<Course>,
        constraints: HardConstraints,
        instructorRatings: Map<String, Float> = emptyMap(),
        maxResults: Int = DEFAULT_MAX_RESULTS
    ): GenerationResult {
        // Pre-filter: drop sections that violate per-section constraints. This
        // is where most of the search space dies.
        val candidatesPerCourse = courses.map { course ->
            course to course.sections.orEmpty()
                .map { section -> Candidate(course, section, minuteSlots(section)) }
                .filter { candidate -> candidatePasses(candidate, constraints) }
        }

        val coursesWithoutSections = candidatesPerCourse
            .filter { (_, candidates) -> candidates.isEmpty() }
            .map { (course, _) -> course.courseCode }
        if (courses.isEmpty() || coursesWithoutSections.isNotEmpty()) {
            return GenerationResult(
                schedules = emptyList(),
                truncated = false,
                coursesWithNoValidSections = coursesWithoutSections
            )
        }

        // Fail-first ordering: courses with the fewest candidates go first so
        // dead branches are pruned as early as possible
        val ordered = candidatesPerCourse.map { it.second }.sortedBy { it.size }

        val found = mutableListOf<List<Candidate>>()
        val chosen = ArrayList<Candidate>(ordered.size)
        val placedSlots = mutableListOf<MinuteSlot>()
        var nodes = 0
        var truncated = false

        fun dfs(courseIndex: Int) {
            if (truncated) return
            if (courseIndex == ordered.size) {
                found.add(chosen.toList())
                if (found.size >= maxResults) truncated = true
                return
            }
            for (candidate in ordered[courseIndex]) {
                if (++nodes > MAX_NODES) {
                    truncated = true
                    return
                }
                val conflicts = candidate.slots.any { slot ->
                    placedSlots.any { placed ->
                        slot.conflictsWith(placed, constraints.minGapMinutes)
                    }
                }
                if (conflicts) continue
                chosen.add(candidate)
                placedSlots.addAll(candidate.slots)
                dfs(courseIndex + 1)
                chosen.removeAt(chosen.lastIndex)
                repeat(candidate.slots.size) { placedSlots.removeAt(placedSlots.lastIndex) }
                if (truncated) return
            }
        }
        dfs(0)

        // Present selections in the caller's course order, not search order
        val orderIndex = courses.withIndex().associate { (i, course) -> course.courseCode to i }
        val schedules = found.map { combo ->
            val selections = combo
                .sortedBy { orderIndex[it.course.courseCode] }
                .mapIndexed { i, candidate ->
                    ScheduleSelection(candidate.course, candidate.section, colorIndex = i)
                }
            GeneratedSchedule(selections, computeMetrics(combo, instructorRatings))
        }
        return GenerationResult(schedules, truncated, emptyList())
    }

    private fun minuteSlots(section: Section): List<MinuteSlot> =
        section.timeSlots.map { slot ->
            MinuteSlot(
                day = slot.day,
                start = (slot.start * 60).roundToInt(),
                end = (slot.end * 60).roundToInt()
            )
        }

    private fun candidatePasses(candidate: Candidate, constraints: HardConstraints): Boolean {
        if (constraints.onlyOpenSeats && candidate.section.openSeats <= 0) return false
        return candidate.slots.all { slot ->
            slot.day !in constraints.daysOff &&
                    (constraints.earliestStartMinutes == null || slot.start >= constraints.earliestStartMinutes) &&
                    (constraints.latestEndMinutes == null || slot.end <= constraints.latestEndMinutes)
        }
    }

    private fun computeMetrics(
        combo: List<Candidate>,
        ratings: Map<String, Float>
    ): ScheduleMetrics {
        val slots = combo.flatMap { it.slots }
        val slotsByDay = slots.groupBy { it.day }

        // Idle minutes between classes on each day, summed across the week.
        // Every gap counts, including short passing periods. A running max-end
        // keeps nested or multi-meeting sections from producing negative or
        // double-counted gaps.
        val totalGapMinutes = slotsByDay.values.sumOf { daySlots ->
            val sorted = daySlots.sortedBy { it.start }
            var gap = 0
            var coveredUntil = sorted.first().end
            for (slot in sorted.drop(1)) {
                if (slot.start > coveredUntil) gap += slot.start - coveredUntil
                coveredUntil = max(coveredUntil, slot.end)
            }
            gap
        }

        // A section's rating is the mean of its rated instructors; unrated
        // sections are excluded from the schedule average rather than counted
        // as zero, with ratedSectionCount exposing the coverage
        val sectionRatings = combo.mapNotNull { candidate ->
            val rated = candidate.section.instructors.mapNotNull { ratings[it] }
            if (rated.isEmpty()) null else rated.sum() / rated.size
        }

        return ScheduleMetrics(
            avgInstructorRating = if (sectionRatings.isEmpty()) null else sectionRatings.sum() / sectionRatings.size,
            ratedSectionCount = sectionRatings.size,
            sectionCount = combo.size,
            minCredits = combo.sumOf { it.course.minCredits },
            maxCredits = combo.sumOf { it.course.maxCredits ?: it.course.minCredits },
            daysWithClasses = slotsByDay.keys.size,
            totalGapMinutes = totalGapMinutes,
            earliestStartMinutes = slots.minOfOrNull { it.start },
            latestEndMinutes = slots.maxOfOrNull { it.end },
            minOpenSeats = combo.minOf { it.section.openSeats }
        )
    }
}
