package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.data.model.formatTimeFromFloat
import com.jupiterp.jupiterpmobile.data.model.parseMeetingString
import com.jupiterp.jupiterpmobile.data.model.parseTimeToFloat
import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Classtime
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.Location
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.model.TimeSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeParsingTest {

    @Test
    fun parsesMorningTime() {
        assertEquals(11.0f, parseTimeToFloat("11:00am"))
    }

    @Test
    fun parsesAfternoonTime() {
        assertEquals(15.5f, parseTimeToFloat("3:30pm"))
    }

    @Test
    fun parsesNoonAndMidnight() {
        assertEquals(12.0f, parseTimeToFloat("12:00pm"))
        assertEquals(0.25f, parseTimeToFloat("12:15am"))
    }

    @Test
    fun rejectsInvalidTimes() {
        assertNull(parseTimeToFloat("11:00"))
        assertNull(parseTimeToFloat("abcpm"))
        assertNull(parseTimeToFloat(""))
    }

    @Test
    fun roundTripsTwentyAndFiftyMinuteTimes() {
        // 7:20 PM stored as float hours carries precision error; formatting
        // must round, not truncate (regression test for the off-by-one minute)
        val sevenTwentyPm = parseTimeToFloat("7:20pm")!!
        assertEquals("7:20pm", formatTimeFromFloat(sevenTwentyPm))

        val nineFiftyAm = parseTimeToFloat("9:50am")!!
        assertEquals("9:50am", formatTimeFromFloat(nineFiftyAm))
    }

    @Test
    fun classtimeFormatsRoundedMinutes() {
        val classtime = Classtime(days = "MWF", start = 19f + 20f / 60f, end = 19f + 50f / 60f)
        assertEquals("7:20 PM", classtime.startFormatted)
        assertEquals("7:50 PM", classtime.endFormatted)
    }
}

class MeetingParsingTest {

    @Test
    fun parsesInPersonMeeting() {
        val meeting = parseMeetingString("TuTh-11:00am-12:15pm-CSI-1115")
        val inPerson = assertIs<ClassMeeting.InPerson>(meeting)
        assertEquals("TuTh", inPerson.classtime.days)
        assertEquals(11.0f, inPerson.classtime.start)
        assertEquals(12.25f, inPerson.classtime.end)
        assertEquals("CSI", inPerson.location.building)
        assertEquals("1115", inPerson.location.room)
    }

    @Test
    fun parsesOnlineSyncMeeting() {
        val meeting = parseMeetingString("MWF-9:00am-9:50am-OnlineSync")
        val sync = assertIs<ClassMeeting.OnlineSync>(meeting)
        assertEquals("MWF", sync.classtime.days)
    }

    @Test
    fun parsesAsyncAndUnspecified() {
        assertIs<ClassMeeting.OnlineAsync>(parseMeetingString("OnlineAsync"))
        assertIs<ClassMeeting.TBA>(parseMeetingString("Unspecified"))
    }

    @Test
    fun unparseableStringsBecomeUnknown() {
        assertIs<ClassMeeting.Unknown>(parseMeetingString("garbage"))
        assertIs<ClassMeeting.Unknown>(parseMeetingString("MWF-bad-times-CSI"))
    }

    @Test
    fun parsesDayStrings() {
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            Classtime("MWF", 9f, 10f).daysList
        )
        assertEquals(
            listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            Classtime("TuTh", 9f, 10f).daysList
        )
        assertEquals(
            listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Classtime("SaSu", 9f, 10f).daysList
        )
        assertEquals(
            listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            ),
            Classtime("MTuWThF", 9f, 10f).daysList
        )
    }
}

class TimeSlotTest {

    @Test
    fun overlapRequiresSameDay() {
        val a = TimeSlot(DayOfWeek.MONDAY, 9f, 10f)
        val b = TimeSlot(DayOfWeek.TUESDAY, 9f, 10f)
        assertTrue(!a.overlaps(b))
    }

    @Test
    fun detectsPartialOverlap() {
        val a = TimeSlot(DayOfWeek.MONDAY, 9f, 10.5f)
        val b = TimeSlot(DayOfWeek.MONDAY, 10f, 11f)
        assertTrue(a.overlaps(b))
        assertTrue(b.overlaps(a))
    }

    @Test
    fun backToBackIsNotOverlap() {
        val a = TimeSlot(DayOfWeek.MONDAY, 9f, 10f)
        val b = TimeSlot(DayOfWeek.MONDAY, 10f, 11f)
        assertTrue(!a.overlaps(b))
    }
}

class ScheduleComputationTest {

    private fun course(code: String, minCredits: Int = 3, maxCredits: Int? = null) = Course(
        courseCode = code,
        name = "Test course",
        minCredits = minCredits,
        maxCredits = maxCredits,
        description = null,
        genEds = null,
        conditions = null,
        sections = null
    )

    private fun section(code: String, meetings: List<ClassMeeting>) = Section(
        courseCode = "TEST100",
        sectionCode = code,
        instructors = emptyList(),
        meetings = meetings,
        openSeats = 10,
        totalSeats = 30,
        waitlist = 0,
        holdfile = null
    )

    @Test
    fun onlineSyncWeekdayMeetingsProduceScheduleBlocks() {
        val selection = ScheduleSelection(
            course = course("TEST100"),
            section = section(
                "0101",
                listOf(ClassMeeting.OnlineSync(Classtime("MWF", 9f, 9f + 50f / 60f)))
            ),
            colorIndex = 0
        )
        val blocks = ScheduleRepository.getScheduleBlocks(listOf(selection))
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it.location == null })
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            blocks.map { it.day }.toSet()
        )
    }

    @Test
    fun inPersonWeekendMeetingsGoToOtherItems() {
        val selection = ScheduleSelection(
            course = course("TEST100"),
            section = section(
                "0101",
                listOf(
                    ClassMeeting.InPerson(
                        Classtime("Sa", 10f, 12f),
                        Location("CSI", "1115")
                    )
                )
            ),
            colorIndex = 0
        )
        assertEquals(0, ScheduleRepository.getScheduleBlocks(listOf(selection)).size)
        assertEquals(1, ScheduleRepository.getOtherItems(listOf(selection)).size)
    }

    @Test
    fun totalCreditsSumsMinAndMax() {
        val selections = listOf(
            ScheduleSelection(course("A", 3), section("0101", emptyList()), 0),
            ScheduleSelection(course("B", 1, 4), section("0101", emptyList()), 1)
        )
        assertEquals(4..7, ScheduleRepository.getTotalCredits(selections))
    }
}

class UtilTest {

    @Test
    fun oneDecimalString() {
        assertEquals("4.2", 4.25f.toOneDecimalString())
        assertEquals("4.0", 4.0f.toOneDecimalString())
        assertEquals("3.9", 3.99f.toOneDecimalString())
    }

    @Test
    fun daysInMonthHandlesLeapYears() {
        assertEquals(29, daysInMonth(2024, 2))
        assertEquals(28, daysInMonth(2025, 2))
        assertEquals(28, daysInMonth(2100, 2)) // divisible by 100 but not 400
        assertEquals(29, daysInMonth(2000, 2)) // divisible by 400
        assertEquals(31, daysInMonth(2025, 12))
        assertEquals(30, daysInMonth(2025, 9))
    }
}
