package com.jupiterp.jupiterpmobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun shareIcs(content: String, filename: String)

/** Returns today's date as an integer YYYYMMDD (e.g. 20261115 for Nov 15 2026). */
expect fun currentDateInt(): Int