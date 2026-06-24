package com.esper.foodsafety.timers

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class TimerManager(
    private val onTimerEvent: (batchLabel: String, holdMinutes: Int, value: String) -> Unit,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _timers = MutableStateFlow<List<TimerState>>(emptyList())
    val timers: StateFlow<List<TimerState>> = _timers.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun start(batchLabel: String, holdMinutes: Int, station: String = "walk-in-cooler-1"): String {
        val timerId = UUID.randomUUID().toString()
        val totalSeconds = holdMinutes * 60L

        val initial = TimerState(timerId, batchLabel, holdMinutes, totalSeconds, TimerStatus.RUNNING, station)
        _timers.value = _timers.value + initial

        onTimerEvent(batchLabel, holdMinutes, "start")

        jobs[timerId] = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                update(timerId) { it.copy(remainingSeconds = remaining) }
            }
            update(timerId) { it.copy(status = TimerStatus.BREACHED, remainingSeconds = 0) }
            onTimerEvent(batchLabel, holdMinutes, "breach")
        }
        return timerId
    }

    fun complete(timerId: String) {
        jobs[timerId]?.cancel()
        jobs.remove(timerId)
        update(timerId) { it.copy(status = TimerStatus.COMPLETE) }
        val t = _timers.value.find { it.timerId == timerId } ?: return
        onTimerEvent(t.batchLabel, t.holdMinutes, "complete")
    }

    fun cancel(timerId: String) = complete(timerId)

    private fun update(timerId: String, block: (TimerState) -> TimerState) {
        _timers.value = _timers.value.map { if (it.timerId == timerId) block(it) else it }
    }

    fun clear() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.coroutineContext.cancelChildren()
    }
}
