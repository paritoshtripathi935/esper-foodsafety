package com.esper.foodsafety.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SyncHealth {
    data class State(
        val lastSuccessMs: Long? = null,
        val lastFailureMs: Long? = null,
        val lastError: String? = null,
        val pendingCount: Long = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun recordSuccess(synced: Int, pending: Long) {
        _state.value = _state.value.copy(
            lastSuccessMs = System.currentTimeMillis(),
            pendingCount = pending,
        )
    }

    fun recordFailure(error: String, pending: Long) {
        _state.value = _state.value.copy(
            lastFailureMs = System.currentTimeMillis(),
            lastError = error,
            pendingCount = pending,
        )
    }

    fun isHealthy(): Boolean {
        val s = _state.value
        val last = s.lastSuccessMs ?: return false
        return System.currentTimeMillis() - last < 120_000L  // healthy if synced in last 2 min
    }
}
