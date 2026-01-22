package com.jupiterp.jupiterpmobile

fun Float.toOneDecimalString(): String {
    val rounded = (this * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}.0" else "$rounded"
}