package com.esper.foodsafety.timers

data class TimerState(
    val timerId: String,
    val batchLabel: String,
    val holdMinutes: Int,
    val remainingSeconds: Long,
    val status: TimerStatus,
    val station: String = "walk-in-cooler-1",
)

enum class TimerStatus { RUNNING, BREACHED, COMPLETE }
