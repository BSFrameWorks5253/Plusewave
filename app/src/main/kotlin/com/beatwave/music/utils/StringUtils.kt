/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.utils

import java.math.BigInteger
import java.security.MessageDigest

fun makeTimeString(duration: Long?): String {
    if (duration == null || duration < 0) return ""
    var sec = duration / 1000
    val day = sec / 86400
    sec %= 86400
    val hour = sec / 3600
    sec %= 3600
    val minute = sec / 60
    sec %= 60
    return when {
        day > 0 -> "%d:%02d:%02d:%02d".format(day, hour, minute, sec)
        hour > 0 -> "%d:%02d:%02d".format(hour, minute, sec)
        else -> "%d:%02d".format(minute, sec)
    }
}

fun formatListeningTime(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return "0s"
    val totalSeconds = durationMs / 1000
    val days = totalSeconds / 86400
    val remainderDay = totalSeconds % 86400
    val hours = remainderDay / 3600
    val remainderHour = remainderDay % 3600
    val minutes = remainderHour / 60
    val seconds = remainderHour % 60

    return when {
        days > 0 -> if (hours > 0) "${days}d ${hours}h" else "${days}d"
        hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
        minutes > 0 -> if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
        else -> "${seconds}s"
    }
}

fun md5(str: String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(str.toByteArray())).toString(16).padStart(32, '0')
}

fun joinByBullet(vararg str: String?) =
    str
        .filterNot {
            it.isNullOrEmpty()
        }.joinToString(separator = " • ")
