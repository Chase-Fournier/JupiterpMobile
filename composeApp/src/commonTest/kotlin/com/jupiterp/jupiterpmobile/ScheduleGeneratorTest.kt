package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Classtime
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.Location
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.scheduler.CourseRequest
import com.jupiterp.jupiterpmobile.domain.scheduler.GeneratedSchedule
import com.jupiterp.jupiterpmobile.domain.scheduler.HardConstraints
import com.jupiterp.jupiterpmobile.domain.scheduler.OverriddenFilter
import com.jupiterp.jupiterpmobile.domain.scheduler.RelaxationKind
import com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleGenerator
import com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleMetrics
import com.jupiterp.jupiterpmobile.domain.scheduler.SectionPin
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

    // ---- Required / optional ----

    @Test
    fun optionalCourseIsDroppedWhenItConflicts() {
        val required = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f)))
        ))
        // The only section of the optional course overlaps the required one.
        val optional = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(inPerson("M", 9f, 10f)))
        ))

        val result = ScheduleGenerator.generate(
            listOf(
                CourseRequest(required, required = true),
                CourseRequest(optional, required = false)
            ),
            HardConstraints()
        )

        // Only the required course can be placed; the optional one is left out.
        assertEquals(1, result.schedules.size)
        assertEquals(
            listOf("AAAA100"),
            result.schedules[0].selections.map { it.course.courseCode }
        )
    }

    @Test
    fun optionalCourseGeneratesEverySubset() {
        val required = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f)))
        ))
        val optional = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(inPerson("Tu", 9f, 10f)))
        ))

        val result = ScheduleGenerator.generate(
            listOf(
                CourseRequest(required, required = true),
                CourseRequest(optional, required = false)
            ),
            HardConstraints()
        )

        // Both "include the elective" and "drop it" are valid schedules.
        assertEquals(2, result.schedules.size)
        assertEquals(setOf(1, 2), result.schedules.map { it.selections.size }.toSet())
    }

    @Test
    fun requiredCourseWithNoSectionsStillBlocksWhileOptionalDoesNot() {
        val required = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f)), openSeats = 0)
        ))
        val optionalFull = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(inPerson("Tu", 9f, 10f)), openSeats = 0)
        ))

        // Open-seats filter empties both courses. The required one makes the
        // whole run impossible; the optional one would simply be skipped.
        val blocked = ScheduleGenerator.generate(
            listOf(CourseRequest(required), CourseRequest(optionalFull, required = false)),
            HardConstraints(onlyOpenSeats = true)
        )
        assertEquals(0, blocked.schedules.size)
        assertEquals(listOf("AAAA100"), blocked.coursesWithNoValidSections)
    }

    // ---- Pins ----

    @Test
    fun pinBySectionRestrictsToThatSection() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f))),
            section("AAAA100", "0102", listOf(inPerson("Tu", 9f, 10f)))
        ))

        val result = ScheduleGenerator.generate(
            listOf(CourseRequest(courseA, pin = SectionPin.BySection("0102"))),
            HardConstraints()
        )

        assertEquals(1, result.schedules.size)
        assertEquals("0102", result.schedules[0].selections[0].section.sectionCode)
    }

    @Test
    fun pinByInstructorKeepsOnlyThatProfessorsSections() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f)), instructors = listOf("Prof X")),
            section("AAAA100", "0102", listOf(inPerson("Tu", 9f, 10f)), instructors = listOf("Prof Y"))
        ))

        val result = ScheduleGenerator.generate(
            listOf(CourseRequest(courseA, pin = SectionPin.ByInstructor("Prof Y"))),
            HardConstraints()
        )

        assertEquals(1, result.schedules.size)
        assertEquals("0102", result.schedules[0].selections[0].section.sectionCode)
    }

    @Test
    fun pinWinsOverFilterAndReportsTheOverride() {
        // The pinned section starts at 8 AM, before the earliest-start filter.
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0800", listOf(inPerson("MWF", 8f, 9f)))
        ))

        val result = ScheduleGenerator.generate(
            listOf(CourseRequest(courseA, pin = SectionPin.BySection("0800"))),
            HardConstraints(earliestStartMinutes = 9 * 60)
        )

        // Kept despite the filter, and the override is surfaced.
        assertEquals(1, result.schedules.size)
        assertEquals(1, result.pinNotices.size)
        val notice = result.pinNotices[0]
        assertEquals("AAAA100", notice.courseCode)
        assertEquals("0800", notice.sectionCode)
        assertTrue(OverriddenFilter.EARLIEST_START in notice.overriddenFilters)
    }

    // ---- Minimum credits ----

    @Test
    fun minCreditsExcludesTooLightSchedules() {
        val required = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 10f)))
        ), minCredits = 3)
        val optional = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(inPerson("Tu", 9f, 10f)))
        ), minCredits = 3)

        val result = ScheduleGenerator.generate(
            listOf(
                CourseRequest(required, required = true),
                CourseRequest(optional, required = false)
            ),
            HardConstraints(minCredits = 6)
        )

        // The 3-credit "required only" subset falls below the floor.
        assertEquals(1, result.schedules.size)
        assertEquals(6, result.schedules[0].metrics.minCredits)
    }

    @Test
    fun minCreditsListedAsRelaxation() {
        val relaxations = singleRelaxations(HardConstraints(onlyOpenSeats = false, minCredits = 12))
        assertEquals(1, relaxations.size)
        val relaxation = relaxations.first { it.kind == RelaxationKind.MIN_CREDITS }
        assertNull(relaxation.constraints.minCredits)
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
    fun gapCountsBreaksWithinAndAcrossCourses() {
        // One section with a morning and afternoon meeting on Monday (3h gap),
        // plus a second course filling part of that gap on Monday only.
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(
                inPerson("M", 9f, 10f),
                inPerson("M", 13f, 14f)
            ))
        ))
        val courseB = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(inPerson("M", 11f, 12f)))
        ))

        val result = ScheduleGenerator.generate(listOf(courseA, courseB), HardConstraints())

        assertEquals(1, result.schedules.size)
        // Monday timeline: 9-10, 11-12, 13-14 -> gaps 10:00-11:00 (60) + 12:00-13:00 (60)
        assertEquals(120, result.schedules[0].metrics.totalGapMinutes)
    }

    @Test
    fun countsShortPassingPeriods() {
        // 9:00-9:50 then 10:00-10:50 on Monday: the 10-minute hop between counts.
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("M", 9f, 9f + 50f / 60f)))
        ))
        val courseB = course("BBBB100", listOf(
            section("BBBB100", "0101", listOf(inPerson("M", 10f, 10f + 50f / 60f)))
        ))

        val result = ScheduleGenerator.generate(listOf(courseA, courseB), HardConstraints())
        assertEquals(10, result.schedules[0].metrics.totalGapMinutes)
    }

    @Test
    fun singleMeetingDayHasNoGap() {
        val courseA = course("AAAA100", listOf(
            section("AAAA100", "0101", listOf(inPerson("MWF", 9f, 10f)))
        ))
        val result = ScheduleGenerator.generate(listOf(courseA), HardConstraints())
        assertEquals(0, result.schedules[0].metrics.totalGapMinutes)
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
        maxCredits: Int = 15,
        sectionCount: Int = 5
    ) = GeneratedSchedule(
        selections = emptyList(),
        metrics = ScheduleMetrics(
            avgInstructorRating = rating,
            ratedSectionCount = if (rating != null) 1 else 0,
            sectionCount = sectionCount,
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
    fun mostClassesSortsBySectionCountDescending() {
        val twoClasses = scheduleWith(rating = 5f, sectionCount = 2)
        val fourClasses = scheduleWith(rating = 2f, sectionCount = 4)

        val sorted = listOf(twoClasses, fourClasses).sortedByCriterion(SortCriterion.MOST_CLASSES)

        assertEquals(listOf(4, 2), sorted.map { it.metrics.sectionCount })
    }

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
