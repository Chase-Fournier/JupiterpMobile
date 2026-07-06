package com.jupiterp.jupiterpmobile.deeplink

import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection

/**
 * A decoded (courseCode, sectionCode) pair from a shared schedule link.
 */
data class CourseSectionPair(
    val courseCode: String,
    val sectionCode: String
)

/**
 * Encodes and decodes a schedule as a short, shareable URL token, matching
 * the Jupiterp web format (`https://jupiterp.com/?s=2~CMSC4Aq8z.MATH8nP2k`).
 *
 * This is a direct port of the web app's `ShareLink.ts`, so links generated
 * by either client decode identically on both. A schedule is fully
 * reconstructable from an ordered list of (courseCode, sectionCode) pairs;
 * all other section data is re-fetched live from the API when the link is
 * opened. These functions are pure so they can be unit tested directly.
 */
object ShareLink {
    /** Query parameter that carries a shared schedule, e.g. `?s=2~CMSC4Aq8z`. */
    const val SHARE_PARAM = "s"

    /**
     * Schema version emitted by [encodeSchedule]. [decodeSchedule] understands
     * every version listed below, so links shared under an older format keep
     * working after a bump.
     *
     * - `1` — readable literal pairs: `1~CMSC131-0101.MATH140-0501`
     * - `2` — dept letters + a base62-packed number/section token (this version)
     */
    private const val SCHEMA_VERSION = "2"

    /** Separates encoded course segments from each other. */
    private const val PAIR_SEPARATOR = '.'

    /** Separates a courseCode from a sectionCode in a literal (fallback) segment. */
    private const val FIELD_SEPARATOR = '-'

    /** Separates the schema version from the payload. */
    private const val VERSION_SEPARATOR = '~'

    /**
     * Base62 alphabet (URL-unreserved, and notably free of `-`, which lets the
     * v2 decoder tell a packed segment apart from a literal `course-section` one).
     */
    private const val BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    /**
     * Width of a packed token. A course packs into 29 bits (10 number + 5 suffix +
     * 14 section), and 62^5 comfortably covers that, so every packed token is
     * exactly 5 chars — which is what lets the decoder split dept (everything
     * before) from the packed value (the last 5 chars) without a delimiter.
     */
    private const val PACKED_WIDTH = 5

    // Bit layout of a packed course: [ number:10 | suffix:5 | section:14 ].
    private const val SECTION_BITS = 14
    private const val SUFFIX_BITS = 5

    /** Matches a "standard" course code: dept letters, a 3-digit number, optional 1-letter suffix. */
    private val PACKABLE_COURSE = Regex("^([A-Za-z]+)(\\d{3})([A-Za-z]?)$")

    /** A packable section is exactly four digits, e.g. `0101`. */
    private val PACKABLE_SECTION = Regex("^\\d{4}$")

    /** Encode `n` as a fixed-width base62 string (left-padded with `0`s). */
    private fun toBase62(n: Int, width: Int): String {
        var value = n
        val out = CharArray(width)
        for (i in width - 1 downTo 0) {
            out[i] = BASE62[value % 62]
            value /= 62
        }
        return out.concatToString()
    }

    /** Decode a base62 string, or return `-1` if it contains an invalid character. */
    private fun fromBase62(s: String): Int {
        var n = 0
        for (ch in s) {
            val digit = BASE62.indexOf(ch)
            if (digit == -1) {
                return -1
            }
            n = n * 62 + digit
        }
        return n
    }

    /**
     * Encode one selection as the shortest segment that round-trips exactly:
     * `DEPT` + a 5-char packed token for standard codes, or the literal
     * `courseCode-sectionCode` for anything that doesn't fit the packable shape
     * (4-digit numbers, lettered sections, etc.).
     */
    private fun encodeSegment(courseCode: String, sectionCode: String): String {
        val course = PACKABLE_COURSE.matchEntire(courseCode)
        if (course != null && PACKABLE_SECTION.matches(sectionCode)) {
            val (dept, numberText, suffixText) = course.destructured
            val number = numberText.toInt()
            val suffix = if (suffixText.isNotEmpty()) {
                suffixText.uppercase()[0].code - 64 // A=1..Z=26
            } else {
                0
            }
            val section = sectionCode.toInt()
            val packed = (number shl (SUFFIX_BITS + SECTION_BITS)) or
                (suffix shl SECTION_BITS) or
                section
            return dept + toBase62(packed, PACKED_WIDTH)
        }
        return "$courseCode$FIELD_SEPARATOR$sectionCode"
    }

    /**
     * Encode a schedule's course selections into a compact, URL-safe token.
     *
     * The token uses only RFC 3986 "unreserved" characters, so it never needs
     * percent-encoding.
     *
     * @param pairs The (courseCode, sectionCode) pairs to encode, in order.
     * @return A token like `2~CMSC4Aq8z.MATH...`, or `""` if there is nothing
     *         to share.
     */
    fun encodeSchedule(pairs: List<CourseSectionPair>): String {
        if (pairs.isEmpty()) {
            return ""
        }
        val segments = pairs.map { encodeSegment(it.courseCode, it.sectionCode) }
        return "$SCHEMA_VERSION$VERSION_SEPARATOR${segments.joinToString(PAIR_SEPARATOR.toString())}"
    }

    /** Decode a literal `courseCode-sectionCode` segment, or `null` if malformed. */
    private fun decodeLiteralSegment(token: String): CourseSectionPair? {
        // Course codes never contain `-`, so the last `-` splits course/section.
        val split = token.lastIndexOf(FIELD_SEPARATOR)
        if (split <= 0 || split == token.length - 1) {
            return null
        }
        return CourseSectionPair(
            courseCode = token.substring(0, split),
            sectionCode = token.substring(split + 1)
        )
    }

    /** Decode a `DEPT` + packed-token segment, or `null` if malformed. */
    private fun decodePackedSegment(token: String): CourseSectionPair? {
        val dept = token.dropLast(PACKED_WIDTH)
        if (dept.isEmpty()) {
            return null
        }
        val packed = fromBase62(token.takeLast(PACKED_WIDTH))
        if (packed < 0) {
            return null
        }

        val number = (packed shr (SUFFIX_BITS + SECTION_BITS)) and 0x3ff // 10 bits
        val suffix = (packed shr SECTION_BITS) and 0x1f // 5 bits
        val section = packed and 0x3fff // 14 bits
        val suffixLetter = if (suffix in 1..26) ('@' + suffix).toString() else ""

        return CourseSectionPair(
            courseCode = dept + number.toString().padStart(3, '0') + suffixLetter,
            sectionCode = section.toString().padStart(4, '0')
        )
    }

    /** v1 payload: literal pairs only. */
    private fun decodeV1(payload: String): List<CourseSectionPair> =
        payload.split(PAIR_SEPARATOR).mapNotNull { decodeLiteralSegment(it) }

    /** v2 payload: a segment with `-` is literal; otherwise it is packed. */
    private fun decodeV2(payload: String): List<CourseSectionPair> =
        payload.split(PAIR_SEPARATOR).mapNotNull { token ->
            when {
                token.contains(FIELD_SEPARATOR) -> decodeLiteralSegment(token)
                token.length > PACKED_WIDTH -> decodePackedSegment(token)
                else -> null
            }
        }

    /**
     * Decode a share token back into the course/section pairs it represents.
     *
     * Tolerant of malformed input: an unrecognized version or any unparseable
     * segment yields `[]` (or skips just that segment) rather than throwing, so
     * a broken or truncated link degrades gracefully instead of crashing.
     * Older schema versions still decode, so previously-shared links keep
     * working.
     *
     * @param param The raw token from the `s` query parameter.
     * @return The decoded pairs (possibly empty).
     */
    fun decodeSchedule(param: String): List<CourseSectionPair> {
        if (param.isEmpty()) {
            return emptyList()
        }

        val versionEnd = param.indexOf(VERSION_SEPARATOR)
        if (versionEnd == -1) {
            return emptyList()
        }

        val version = param.substring(0, versionEnd)
        val payload = param.substring(versionEnd + 1)
        if (payload.isEmpty()) {
            return emptyList()
        }

        return when (version) {
            "1" -> decodeV1(payload)
            "2" -> decodeV2(payload)
            else -> emptyList()
        }
    }

    /**
     * Pull the share token out of a full deep link URL, e.g.
     * `https://jupiterp.com/?s=2~CMSC4Aq8z` or `jupiterp://open?s=2~CMSC4Aq8z`.
     *
     * Parsed by hand so it works identically in common code for any scheme,
     * and tolerates URLs with fragments or unrelated query parameters.
     *
     * @return The raw (percent-decoded) token, or `null` if the URL has no
     *         non-empty `s` parameter.
     */
    fun extractShareToken(url: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) {
            return null
        }
        for (param in query.split('&')) {
            val eq = param.indexOf('=')
            if (eq <= 0) {
                continue
            }
            if (percentDecode(param.substring(0, eq)) == SHARE_PARAM) {
                val value = percentDecode(param.substring(eq + 1))
                if (value.isNotEmpty()) {
                    return value
                }
            }
        }
        return null
    }

    /**
     * Minimal percent-decoder. Share tokens only use unreserved ASCII, so a
     * single-byte decode is sufficient; malformed escapes pass through as-is.
     */
    private fun percentDecode(s: String): String {
        if ('%' !in s) {
            return s
        }
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    sb.append(hex.toChar())
                    i += 3
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /**
     * Reconstruct full [ScheduleSelection]s from decoded pairs and
     * freshly-fetched course data. Pairs whose course or section no longer
     * exist are silently skipped, so a stale link still opens whatever is
     * still valid. Color indices are assigned sequentially.
     *
     * This is pure: the caller is responsible for fetching `coursesByCode`.
     */
    fun buildSharedSelections(
        pairs: List<CourseSectionPair>,
        coursesByCode: Map<String, Course>
    ): List<ScheduleSelection> {
        val result = mutableListOf<ScheduleSelection>()
        for (pair in pairs) {
            val course = coursesByCode[pair.courseCode] ?: continue
            val section = course.sections?.find { it.sectionCode == pair.sectionCode } ?: continue
            result.add(
                ScheduleSelection(
                    course = course,
                    section = section,
                    colorIndex = result.size
                )
            )
        }
        return result
    }
}
