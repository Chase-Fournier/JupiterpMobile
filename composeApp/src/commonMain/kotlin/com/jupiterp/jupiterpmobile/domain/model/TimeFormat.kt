package com.jupiterp.jupiterpmobile.domain.model

import kotlin.math.roundToInt

/**
 * Formats a float number of hours-since-midnight as a 12-hour clock string.
 * Rounds to whole minutes first so float precision error (e.g. 19.3333 for
 * 7:20 PM) does not truncate a minute. Minutes are omitted when zero.
 *
 * @param suffixSeparator string placed between the time and the AM/PM label
 *        ("" for "3:30pm", " " for "3:30 PM").
 */
internal fun formatTwelveHourTime(
    time: Float,
    suffixSeparator: String,
    amLabel: String,
    pmLabel: String
): String {
    val totalMinutes = (time * 60).roundToInt()
    val hours24 = totalMinutes / 60
    val minutes = totalMinutes % 60
    val period = if (hours24 >= 12) pmLabel else amLabel
    val hours12 = when {
        hours24 == 0 -> 12
        hours24 > 12 -> hours24 - 12
        else -> hours24
    }
    val hourMinute = if (minutes == 0) {
        "$hours12"
    } else {
        "$hours12:${minutes.toString().padStart(2, '0')}"
    }
    return "$hourMinute$suffixSeparator$period"
}
