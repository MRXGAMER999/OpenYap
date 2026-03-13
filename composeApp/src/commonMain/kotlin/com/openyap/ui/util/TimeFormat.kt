package com.openyap.ui.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun Instant.toRelativeString(): String {
    val now = Clock.System.now()
    val diff = now - this
    val minutes = diff.inWholeMinutes
    val hours = diff.inWholeHours
    val days = diff.inWholeDays
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 2 -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> {
            val local = this.toLocalDateTime(TimeZone.currentSystemDefault())
            val month = local.month.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() }
            "$month ${local.day} · ${local.hour}:${
                local.minute.toString().padStart(2, '0')
            }"
        }
    }
}
