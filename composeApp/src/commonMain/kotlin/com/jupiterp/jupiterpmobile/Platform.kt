package com.jupiterp.jupiterpmobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform