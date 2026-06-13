package com.jupiterp.jupiterpmobile.domain.scheduler

/**
 * Sort orderings for generated schedules. Metrics are precomputed, so
 * switching criteria is pure comparator work — no regeneration.
 */
enum class SortCriterion(val label: String) {
    BEST_RATING("Top rated"),
    MOST_COMPACT("Fewest gaps"),
    FEWEST_DAYS("Fewest days"),
    LATEST_START("Latest start"),
    EARLIEST_END("Earliest finish"),
    MOST_CREDITS("Most credits")
}

fun List<GeneratedSchedule>.sortedByCriterion(criterion: SortCriterion): List<GeneratedSchedule> =
    sortedWith(criterion.comparator())

// Unrated schedules sort below any rated one rather than competing with them
private fun GeneratedSchedule.ratingOrUnrated(): Float = metrics.avgInstructorRating ?: -1f

private fun SortCriterion.comparator(): Comparator<GeneratedSchedule> = when (this) {
    SortCriterion.BEST_RATING ->
        compareByDescending<GeneratedSchedule> { it.ratingOrUnrated() }
            .thenBy { it.metrics.totalGapMinutes }
            .thenBy { it.metrics.daysWithClasses }

    SortCriterion.MOST_COMPACT ->
        compareBy<GeneratedSchedule> { it.metrics.totalGapMinutes }
            .thenBy { it.metrics.daysWithClasses }
            .thenByDescending { it.ratingOrUnrated() }

    SortCriterion.FEWEST_DAYS ->
        compareBy<GeneratedSchedule> { it.metrics.daysWithClasses }
            .thenBy { it.metrics.totalGapMinutes }
            .thenByDescending { it.ratingOrUnrated() }

    SortCriterion.LATEST_START ->
        // No timed meetings at all is the latest possible start
        compareByDescending<GeneratedSchedule> { it.metrics.earliestStartMinutes ?: Int.MAX_VALUE }
            .thenByDescending { it.ratingOrUnrated() }

    SortCriterion.EARLIEST_END ->
        compareBy<GeneratedSchedule> { it.metrics.latestEndMinutes ?: 0 }
            .thenByDescending { it.ratingOrUnrated() }

    SortCriterion.MOST_CREDITS ->
        compareByDescending<GeneratedSchedule> { it.metrics.maxCredits }
            .thenByDescending { it.metrics.minCredits }
            .thenByDescending { it.ratingOrUnrated() }
}
