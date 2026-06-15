package com.jupiterp.jupiterpmobile.domain.scheduler

import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import kotlin.jvm.JvmName
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

    private data class CourseCandidates(
        val candidates: List<Candidate>,
        val optional: Boolean
    )

    /** Convenience overload: every course required, no pins. */
    @JvmName("generateFromCourses")
    fun generate(
        courses: List<Course>,
        constraints: HardConstraints,
        instructorRatings: Map<String, Float> = emptyMap(),
        maxResults: Int = DEFAULT_MAX_RESULTS
    ): GenerationResult = generate(
        requests = courses.map { CourseRequest(it) },
        constraints = constraints,
        instructorRatings = instructorRatings,
        maxResults = maxResults
    )

    fun generate(
        requests: List<CourseRequest>,
        constraints: HardConstraints,
        instructorRatings: Map<String, Float> = emptyMap(),
        maxResults: Int = DEFAULT_MAX_RESULTS
    ): GenerationResult {
        val pinNotices = mutableListOf<PinNotice>()

        // Pre-filter: drop sections that violate per-section constraints. This
        // is where most of the search space dies. Pinned courses skip the
        // filter (the pin wins) but report what each pinned section overrode.
        val perCourse: List<Pair<CourseRequest, List<Candidate>>> = requests.map { request ->
            val candidates = pinnedSections(request)
                .map { section -> Candidate(request.course, section, minuteSlots(section)) }
            val kept = if (request.pin == SectionPin.None) {
                candidates.filter { candidatePasses(it, constraints) }
            } else {
                candidates.onEach { candidate ->
                    val overridden = overriddenFilters(candidate, constraints)
                    if (overridden.isNotEmpty()) {
                        pinNotices += PinNotice(
                            request.course.courseCode,
                            candidate.section.sectionCode,
                            overridden
                        )
                    }
                }
            }
            request to kept
        }

        // Only required courses make generation impossible when they have no
        // valid sections; optional courses with none are simply dropped.
        val coursesWithoutSections = perCourse
            .filter { (request, candidates) -> request.required && candidates.isEmpty() }
            .map { (request, _) -> request.course.courseCode }
        if (requests.isEmpty() || coursesWithoutSections.isNotEmpty()) {
            return GenerationResult(
                schedules = emptyList(),
                truncated = false,
                coursesWithNoValidSections = coursesWithoutSections,
                pinNotices = pinNotices.distinct()
            )
        }

        // Fail-first ordering: courses with the fewest candidates go first so
        // dead branches are pruned as early as possible
        val ordered = perCourse
            .filter { (_, candidates) -> candidates.isNotEmpty() }
            .map { (request, candidates) -> CourseCandidates(candidates, optional = !request.required) }
            .sortedBy { it.candidates.size }

        val found = mutableListOf<List<Candidate>>()
        val chosen = ArrayList<Candidate>(ordered.size)
        val placedSlots = mutableListOf<MinuteSlot>()
        var nodes = 0
        var truncated = false
        val minCredits = constraints.minCredits

        fun dfs(courseIndex: Int) {
            if (truncated) return
            if (courseIndex == ordered.size) {
                // Skip the empty schedule (everything optional dropped) and any
                // schedule below the credit floor.
                val creditsOk = minCredits == null || chosen.sumOf { it.course.minCredits } >= minCredits
                if (chosen.isNotEmpty() && creditsOk) {
                    found.add(chosen.toList())
                    if (found.size >= maxResults) truncated = true
                }
                return
            }
            val courseCandidates = ordered[courseIndex]
            for (candidate in courseCandidates.candidates) {
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
            // Optional courses can be left out. Explore the skip branch after
            // the include branches so fuller schedules are found (and kept on
            // truncation) first.
            if (courseCandidates.optional) {
                dfs(courseIndex + 1)
            }
        }
        dfs(0)

        // Present selections in the caller's course order, not search order
        val orderIndex = requests.withIndex().associate { (i, request) -> request.course.courseCode to i }
        val schedules = found.map { combo ->
            val selections = combo
                .sortedBy { orderIndex[it.course.courseCode] }
                .mapIndexed { i, candidate ->
                    ScheduleSelection(candidate.course, candidate.section, colorIndex = i)
                }
            GeneratedSchedule(selections, computeMetrics(combo, instructorRatings))
        }
        return GenerationResult(schedules, truncated, emptyList(), pinNotices.distinct())
    }

    private fun pinnedSections(request: CourseRequest): List<Section> {
        val sections = request.course.sections.orEmpty()
        return when (val pin = request.pin) {
            SectionPin.None -> sections
            is SectionPin.BySection -> sections.filter { it.sectionCode == pin.sectionCode }
            is SectionPin.ByInstructor -> sections.filter { pin.name in it.instructors }
        }
    }

    /** Per-section filters a pinned candidate violates but is kept in spite of. */
    private fun overriddenFilters(
        candidate: Candidate,
        constraints: HardConstraints
    ): List<OverriddenFilter> {
        val result = mutableListOf<OverriddenFilter>()
        if (constraints.onlyOpenSeats && candidate.section.openSeats <= 0) {
            result += OverriddenFilter.OPEN_SEATS
        }
        val earliest = constraints.earliestStartMinutes
        if (earliest != null && candidate.slots.any { it.start < earliest }) {
            result += OverriddenFilter.EARLIEST_START
        }
        val latest = constraints.latestEndMinutes
        if (latest != null && candidate.slots.any { it.end > latest }) {
            result += OverriddenFilter.LATEST_END
        }
        if (candidate.slots.any { it.day in constraints.daysOff }) {
            result += OverriddenFilter.DAY_OFF
        }
        return result
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
