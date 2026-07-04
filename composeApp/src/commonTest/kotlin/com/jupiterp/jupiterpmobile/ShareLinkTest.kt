package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.jupiterpmobile.deeplink.CourseSectionPair
import com.jupiterp.jupiterpmobile.deeplink.ShareLink
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the shared-schedule link codec. Mirrors the web app's
 * ShareLink.test.ts so both clients keep decoding the same links identically.
 */
class ShareLinkTest {

    private fun pair(courseCode: String, sectionCode: String) =
        CourseSectionPair(courseCode, sectionCode)

    private fun course(courseCode: String, sectionCodes: List<String>) = Course(
        courseCode = courseCode,
        name = "$courseCode Name",
        minCredits = 3,
        maxCredits = null,
        description = "desc",
        genEds = null,
        conditions = null,
        sections = sectionCodes.map { sectionCode ->
            Section(
                courseCode = courseCode,
                sectionCode = sectionCode,
                instructors = listOf("Prof X"),
                meetings = emptyList(),
                openSeats = 0,
                totalSeats = 0,
                waitlist = 0,
                holdfile = null
            )
        }
    )

    // --- encodeSchedule ---

    @Test
    fun encodeEmitsCurrentSchemaVersionPrefix() {
        val token = ShareLink.encodeSchedule(listOf(pair("CMSC131", "0101")))
        assertTrue(token.startsWith("2~"))
    }

    @Test
    fun encodeReturnsEmptyStringForNoPairs() {
        assertEquals("", ShareLink.encodeSchedule(emptyList()))
    }

    @Test
    fun encodeUsesOnlyUrlUnreservedCharacters() {
        val token = ShareLink.encodeSchedule(
            listOf(pair("CMSC351H", "9901"), pair("MATH140", "0501"))
        )
        assertTrue(Regex("^[A-Za-z0-9\\-._~]+$").matches(token))
    }

    @Test
    fun encodePacksStandardCodesShorterThanLiteralForm() {
        val pairs = listOf(
            pair("CMSC131", "0101"),
            pair("MATH140", "0501"),
            pair("ENGL101", "0301"),
            pair("HIST200", "0201")
        )
        val literalLength =
            "1~${pairs.joinToString(".") { "${it.courseCode}-${it.sectionCode}" }}".length
        assertTrue(ShareLink.encodeSchedule(pairs).length < literalLength)
    }

    @Test
    fun encodeMatchesWebImplementationTokens() {
        // Vectors computed with the web planner's ShareLink.ts, so a drift in
        // either port shows up as a broken link between the two clients.
        assertEquals(
            "2~CMSC4eBHR.MATH4xyuD.ENGL3aBav",
            ShareLink.encodeSchedule(
                listOf(pair("CMSC131", "0101"), pair("MATH140", "0501"), pair("ENGL101", "0301"))
            )
        )
        assertEquals(
            "2~CMSCCSk0L",
            ShareLink.encodeSchedule(listOf(pair("CMSC351H", "9901")))
        )
    }

    @Test
    fun decodesWebImplementationTokens() {
        assertEquals(
            listOf(pair("CMSC131", "0101"), pair("MATH140", "0501"), pair("ENGL101", "0301")),
            ShareLink.decodeSchedule("2~CMSC4eBHR.MATH4xyuD.ENGL3aBav")
        )
        assertEquals(
            listOf(pair("CMSC351H", "9901")),
            ShareLink.decodeSchedule("2~CMSCCSk0L")
        )
    }

    // --- encode/decode round-trip (v2) ---

    @Test
    fun roundTripsPackableCodesExactly() {
        val cases = listOf(
            pair("CMSC131", "0101"),
            pair("MATH140", "0501"),
            pair("CMSC351H", "9901"), // 1-letter suffix
            pair("BMGT110", "0000"), // all-zero section
            pair("PHYS161", "9999") // max section
        )
        for (case in cases) {
            val token = ShareLink.encodeSchedule(listOf(case))
            assertEquals(listOf(case), ShareLink.decodeSchedule(token), "case: $case")
        }
    }

    @Test
    fun roundTripsMultiCourseScheduleInOrder() {
        val pairs = listOf(
            pair("CMSC131", "0101"),
            pair("MATH140", "0501"),
            pair("ENGL101", "0301")
        )
        assertEquals(pairs, ShareLink.decodeSchedule(ShareLink.encodeSchedule(pairs)))
    }

    @Test
    fun fallsBackToLiteralSegmentForNonStandardCodesAndRoundTrips() {
        // 4-digit number, and a lettered section: neither is packable.
        val pairs = listOf(pair("AASP1000", "FC01"), pair("CMSC131", "0101"))
        val token = ShareLink.encodeSchedule(pairs)
        assertTrue(token.contains("AASP1000-FC01"))
        assertEquals(pairs, ShareLink.decodeSchedule(token))
    }

    // --- decodeSchedule ---

    @Test
    fun decodesLegacyV1LiteralLinks() {
        assertEquals(
            listOf(pair("CMSC131", "0101"), pair("MATH140", "0501")),
            ShareLink.decodeSchedule("1~CMSC131-0101.MATH140-0501")
        )
    }

    @Test
    fun rejectsUnknownSchemaVersion() {
        assertEquals(emptyList(), ShareLink.decodeSchedule("9~CMSC131-0101"))
    }

    @Test
    fun returnsEmptyForEmptyOrMalformedInput() {
        assertEquals(emptyList(), ShareLink.decodeSchedule(""))
        assertEquals(emptyList(), ShareLink.decodeSchedule("CMSC131-0101"))
        assertEquals(emptyList(), ShareLink.decodeSchedule("1~"))
        assertEquals(emptyList(), ShareLink.decodeSchedule("2~"))
    }

    @Test
    fun skipsUnparseableV1SegmentsButKeepsValidOnes() {
        assertEquals(
            listOf(pair("CMSC131", "0101"), pair("MATH140", "0501")),
            ShareLink.decodeSchedule("1~CMSC131-0101.garbage.MATH140-0501")
        )
    }

    // --- extractShareToken ---

    @Test
    fun extractsTokenFromWebLink() {
        assertEquals(
            "2~CMSC4Aq8z.MATH8nP2k.ENGL1xR0w",
            ShareLink.extractShareToken("https://jupiterp.com/?s=2~CMSC4Aq8z.MATH8nP2k.ENGL1xR0w")
        )
    }

    @Test
    fun extractsTokenFromCustomSchemeLink() {
        assertEquals(
            "2~CMSC4Aq8z",
            ShareLink.extractShareToken("jupiterp://open?s=2~CMSC4Aq8z")
        )
    }

    @Test
    fun extractsTokenAmongOtherParamsAndFragment() {
        assertEquals(
            "2~CMSC4Aq8z",
            ShareLink.extractShareToken("https://www.jupiterp.com/?utm_source=x&s=2~CMSC4Aq8z&y=1#top")
        )
    }

    @Test
    fun extractsPercentEncodedToken() {
        // `~` never needs escaping, but some apps re-encode URLs anyway.
        assertEquals(
            "2~CMSC4Aq8z",
            ShareLink.extractShareToken("https://jupiterp.com/?s=2%7ECMSC4Aq8z")
        )
    }

    @Test
    fun extractReturnsNullWithoutShareParam() {
        assertNull(ShareLink.extractShareToken("https://jupiterp.com/"))
        assertNull(ShareLink.extractShareToken("https://jupiterp.com/?other=1"))
        assertNull(ShareLink.extractShareToken("https://jupiterp.com/?s="))
        assertNull(ShareLink.extractShareToken("https://jupiterp.com/?season=fall"))
    }

    // --- buildSharedSelections ---

    private val coursesByCode = mapOf(
        "CMSC131" to course("CMSC131", listOf("0101", "0201")),
        "MATH140" to course("MATH140", listOf("0501"))
    )

    @Test
    fun reconstructsSelectionsInOrderWithSequentialColors() {
        val built = ShareLink.buildSharedSelections(
            listOf(pair("CMSC131", "0101"), pair("MATH140", "0501")),
            coursesByCode
        )
        assertEquals(listOf("CMSC131", "MATH140"), built.map { it.course.courseCode })
        assertEquals(listOf("0101", "0501"), built.map { it.section.sectionCode })
        assertEquals(listOf(0, 1), built.map { it.colorIndex })
    }

    @Test
    fun skipsPairsWhoseCourseOrSectionNoLongerExists() {
        val built = ShareLink.buildSharedSelections(
            listOf(
                pair("CMSC131", "9999"), // missing section
                pair("BMGT110", "0101"), // missing course
                pair("MATH140", "0501") // valid
            ),
            coursesByCode
        )
        assertEquals(1, built.size)
        assertEquals("MATH140", built[0].course.courseCode)
        assertEquals(0, built[0].colorIndex)
    }

    // --- uniqueScheduleName ---

    @Test
    fun uniqueNameUsesBaseWhenAvailable() {
        assertEquals(
            "Shared schedule",
            ScheduleRepository.uniqueScheduleName("Shared schedule", listOf("My schedule"))
        )
    }

    @Test
    fun uniqueNameNumbersFromTwoWhenBaseTaken() {
        assertEquals(
            "Shared schedule 2",
            ScheduleRepository.uniqueScheduleName("Shared schedule", listOf("Shared schedule"))
        )
        assertEquals(
            "Shared schedule 3",
            ScheduleRepository.uniqueScheduleName(
                "Shared schedule",
                listOf("Shared schedule", "Shared schedule 2")
            )
        )
    }
}
