package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun addToCalendar(selections: List<ScheduleSelection>, onResult: (Boolean) -> Unit)

/** Returns today's date as an integer YYYYMMDD (e.g. 20261115 for Nov 15 2026). */
expect fun currentDateInt(): Int
