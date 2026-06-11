package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Classtime
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.Location
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.scheduler.GeneratedSchedule
import com.jupiterp.jupiterpmobile.domain.scheduler.HardConstraints
import com.jupiterp.jupiterpmobile.domain.scheduler.RelaxationKind
import com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleGenerator
import com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleMetrics
import com.jupiterp.jupiterpmobile.domain.scheduler.SortCriterion
import com.jupiterp.jupiterpmobile.domain.scheduler.singleRelaxations
import com.jupiterp.jupiterpmobile.domain.scheduler.sortedByCriterion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleGeneratorTest {

    private fun inPerson(days: String, startHour: Float, endHour: Float) =
        ClassMeeting.InPerson(Classtime(days, startHour, endHour), Location("BLD", "101"))

    private fun section(
        courseCode: String,
        sectionCode: String,
        meetings: List<ClassMeeting>,
        openSeats: Int = 10,
        instructors: List<String> = emptyList()
    ) = Section(
        courseCode = courseCode,
        sectionCode = sectionCode,
        instructors = instructors,
        meetings = meetings,
        openSeats = openSeats,
        totalSeats = 30,
        waitlist = 0,
        holdfile = null
    )

    private fun course(
        code: String,
        sections: List<Section>,
        minCredits: Int = 3,
        maxCredits: Int? = null
    ) = Course(
        courseCode = code,
        name = "Test $code",
        minCredits = minCredits,
        maxCredits = maxCredits,
        description = null,
        genEds = null,
        conditions = null,
        sections = sections
    )

    // ---- Generation ----

    @Test
    fun excludesConflictingCombinations() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("MWF", 9f, 10f))),
            section("AAAA100", "0102", listOf(inPerson("MWF", 10f, 11f)))
        ))
        // Conflicts with A-0101 (overlaps 9:30-10:00 on MWF) but not A-0102
        val courseB = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(inPerson("MWF", 9.5f, 10f)))
        ))

        val result = ScheduleGenerator.generate(listOf(courseA, courseB), HardConstraints())

        assertEquals(1, result.schedules.size)
        val sections = result.schedules[0].selections.map { it.section.sectionCode }
        assertEquals(listOf("0102", "0101"), sections)
        assertTrue(!result.truncated)
    }

    @Test
    fun selectionsFollowRequestedCourseOrder() {
        // Fail-first ordering searches BBBB first (1 section vs 2), but the
        // output selections must come back in the caller's course order
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f))),
            section("AAAA100", "0102", listOf(inPerson("Tu", 9f, 10f)))
        ))
        val courseB = course("BBBB100", listOf(
            section("BBBB100", "0201", listOf(inPerson("W", 9f, 10f)))
        ))

        val result = ScheduleGenerator.generate(listOf(courseA, courseB), HardConstraints())

        assertEquals(2, result.schedules.size)
        result.schedules.forEach { schedule ->
            assertEquals(
                listOf("AAAA100", "BBBB100"),
                schedule.selections.map { it.course.courseCode }
            )
        }
    }

    @Test
    fun onlyOpenSeatsFiltersFullSections() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f)), openSeats = 0)
        ))

        val strict = ScheduleGenerator.generate(listOf(courseA), HardConstraints(onlyOpenSeats = true))
        assertEquals(0, strict.schedules.size)
        assertEquals(listOf("AAAA100"), strict.coursesWithNoValidSections)

        val loose = ScheduleGenerator.generate(listOf(courseA), HardConstraints(onlyOpenSeats = false))
        assertEquals(1, loose.schedules.size)
    }

    @Test
    fun timeWindowFiltersSections() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0800", listOf(inPerson("MWF", 8f, 8.83f))),
            section("AAAA100", "1000", listOf(inPerson("MWF", 10f, 10.83f)))
        ))

        val result = ScheduleGenerator.generate(
            listOf(courseA),
            HardConstraints(earliestStartMinutes = 9 * 60)
        )

        assertEquals(1, result.schedules.size)
        assertEquals("1000", result.schedules[0].selections[0].section.sectionCode)
    }

    @Test
    fun daysOffFilterSections() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "MWF", listOf(inPerson("MWF", 9f, 10f))),
            section("AAAA100", "TUTH", listOf(inPerson("TuTh", 9f, 10.25f)))
        ))

        val result = ScheduleGenerator.generate(
            listOf(courseA),
            HardConstraints(daysOff = setOf(DayOfWeek.WEDNESDAY))
        )

        assertEquals(1, result.schedules.size)
        assertEquals("TUTH", result.schedules[0].selections[0].section.sectionCode)
    }

    @Test
    fun minGapTreatsBackToBackAsConflict() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f)))
        ))
        val courseB = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(inPerson("M", 10f, 11f)))
        ))

        val noGap = ScheduleGenerator.generate(listOf(courseA, courseB), HardConstraints(minGapMinutes = 0))
        assertEquals(1, noGap.schedules.size)

        val gap15 = ScheduleGenerator.generate(listOf(courseA, courseB), HardConstraints(minGapMinutes = 15))
        assertEquals(0, gap15.schedules.size)
    }

    @Test
    fun asyncSectionsAlwaysFit() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f)))
        ))
        val courseB = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(ClassMeeting.OnlineAsync))
        ))

        val result = ScheduleGenerator.generate(
            listOf(courseA, courseB),
            HardConstraints(earliestStartMinutes = 8 * 60)
        )
        assertEquals(1, result.schedules.size)
    }

    @Test
    fun truncatesAtMaxResults() {
        // Three courses of six async sections each: 216 valid combinations
        fun asyncCourse(code: String) = course(code, (1..6).map { i ->
            section(code, "010$i", listOf(ClassMeeting.OnlineAsync))
        })

        val result = ScheduleGenerator.generate(
            listOf(asyncCourse("AAAA100"), asyncCourse("BBBB100"), asyncCourse("CCCC100")),
            HardConstraints(),
            maxResults = 100
        )

        assertEquals(100, result.schedules.size)
        assertTrue(result.truncated)
    }

    // ---- Metrics ----

    @Test
    fun computesMetrics() {
        val courseA = course("AAAA100", listOf(
            section(
                "AAAA100", "0101",
                listOf(inPerson("MW", 9f, 10f)),
                openSeats = 5,
                instructors = listOf("Rated Prof")
            )
        ), minCredits = 3)
        val courseB = course("BBBB100", listOf(
            section(
                "BBBB100", "0101",
                listOf(inPerson("M", 11f, 12f)),
                openSeats = 2,
                instructors = listOf("Unrated Prof")
            )
        ), minCredits = 4)

        val result = ScheduleGenerator.generate(
            listOf(courseA, courseB),
            HardConstraints(),
            instructorRatings = mapOf("Rated Prof" to 4.5f)
        )

        assertEquals(1, result.schedules.size)
        val metrics: ScheduleMetrics = result.schedules[0].metrics
        assertEquals(4.5f, metrics.avgInstructorRating)
        assertEquals(1, metrics.ratedSectionCount)
        assertEquals(2, metrics.sectionCount)
        assertEquals(7, metrics.minCredits)
        assertEquals(7, metrics.maxCredits)
        assertEquals(2, metrics.daysWithClasses) // Monday and Wednesday
        assertEquals(60, metrics.totalGapMinutes) // 10:00-11:00 on Monday
        assertEquals(9 * 60, metrics.earliestStartMinutes)
        assertEquals(12 * 60, metrics.latestEndMinutes)
        assertEquals(2, metrics.minOpenSeats)
    }

    @Test
    fun untimedScheduleHasNullTimeMetrics() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(ClassMeeting.OnlineAsync))
        ))

        val result = ScheduleGenerator.generate(listOf(courseA), HardConstraints())
        val metrics = result.schedules[0].metrics
        assertNull(metrics.earliestStartMinutes)
        assertNull(metrics.latestEndMinutes)
        assertEquals(0, metrics.daysWithClasses)
        assertNull(metrics.avgInstructorRating)
    }

    // ---- Relaxations ----

    @Test
    fun listsOneRelaxationPerActiveConstraint() {
        val constraints = HardConstraints(
            earliestStartMinutes = 9 * 60,
            latestEndMinutes = 17 * 60,
            daysOff = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
            onlyOpenSeats = true,
            minGapMinutes = 15
        )

        val relaxations = singleRelaxations(constraints)

        assertEquals(6, relaxations.size)
        assertEquals(2, relaxations.count { it.kind == RelaxationKind.DAY_OFF })
        val openSeatsRelaxation = relaxations.first { it.kind == RelaxationKind.OPEN_SEATS }
        assertTrue(!openSeatsRelaxation.constraints.onlyOpenSeats)
        // Each relaxation loosens exactly one thing; the rest must be intact
        assertEquals(constraints.latestEndMinutes, openSeatsRelaxation.constraints.latestEndMinutes)
        assertEquals(constraints.daysOff, openSeatsRelaxation.constraints.daysOff)
    }

    @Test
    fun noRelaxationsForUnconstrainedInput() {
        assertEquals(0, singleRelaxations(HardConstraints(onlyOpenSeats = false)).size)
    }
}

class ScheduleSorterTest {

    private fun scheduleWith(
        rating: Float?,
        gapMinutes: Int = 0,
        days: Int = 3,
        earliestStart: Int? = 9 * 60,
        latestEnd: Int? = 15 * 60,
        maxCredits: Int = 15
    ) = GeneratedSchedule(
        selections = emptyList(),
        metrics = ScheduleMetrics(
            avgInstructorRating = rating,
            ratedSectionCount = if (rating != null) 1 else 0,
            sectionCount = 5,
            minCredits = maxCredits,
            maxCredits = maxCredits,
            daysWithClasses = days,
            totalGapMinutes = gapMinutes,
            earliestStartMinutes = earliestStart,
            latestEndMinutes = latestEnd,
            minOpenSeats = 1
        )
    )

    @Test
    fun bestRatingSortsDescendingWithUnratedLast() {
        val unrated = scheduleWith(rating = null)
        val low = scheduleWith(rating = 2.5f)
        val high = scheduleWith(rating = 4.8f)

        val sorted = listOf(unrated, low, high).sortedByCriterion(SortCriterion.BEST_RATING)

        assertEquals(listOf(4.8f, 2.5f, null), sorted.map { it.metrics.avgInstructorRating })
    }

    @Test
    fun mostCompactSortsByGapMinutes() {
        val gappy = scheduleWith(rating = 5f, gapMinutes = 240)
        val tight = scheduleWith(rating = 2f, gapMinutes = 10)

        val sorted = listOf(gappy, tight).sortedByCriterion(SortCriterion.MOST_COMPACT)

        assertEquals(listOf(10, 240), sorted.map { it.metrics.totalGapMinutes })
    }

    @Test
    fun latestStartPutsUntimedFirst() {
        val nineAm = scheduleWith(rating = null, earliestStart = 9 * 60)
        val noon = scheduleWith(rating = null, earliestStart = 12 * 60)
        val untimed = scheduleWith(rating = null, earliestStart = null)

        val sorted = listOf(nineAm, noon, untimed).sortedByCriterion(SortCriterion.LATEST_START)

        assertNull(sorted[0].metrics.earliestStartMinutes)
        assertNotNull(sorted[1].metrics.earliestStartMinutes)
        assertEquals(12 * 60, sorted[1].metrics.earliestStartMinutes)
        assertEquals(9 * 60, sorted[2].metrics.earliestStartMinutes)
    }
}
