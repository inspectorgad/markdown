@file:OptIn(ExperimentalTime::class)

package com.example.text

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Today's date without java.text.SimpleDateFormat, which iOS does not have.
//
// The app stores game dates as "yyyy-MM-dd" so they sort and compare as plain
// text, and the only date it has to work out for itself is today's - to tell
// a game still to come from one that has been played without a score.

/** Today's date where the phone is, "yyyy-MM-dd", as SimpleDateFormat gave it. */
fun todayIso(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
