package com.esper.probesimulator.scenarios

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface Scenario {
    val name: String
    val intervalMs: Long
    fun next(step: Int): Float
}

val ALL_SCENARIOS: List<Scenario> = listOf(NormalHold, CoolerFailure, DoorOpenSpike)

class ScenarioRunner(
    private val onTemp: (Float) -> Unit,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var step = 0

    private val _currentScenario = MutableStateFlow<Scenario?>(null)
    val currentScenario: StateFlow<Scenario?> = _currentScenario.asStateFlow()

    private val _currentTemp = MutableStateFlow(38.0f)
    val currentTemp: StateFlow<Float> = _currentTemp.asStateFlow()

    fun start(scenario: Scenario) {
        job?.cancel()
        step = 0
        _currentScenario.value = scenario
        job = scope.launch {
            while (isActive) {
                val temp = scenario.next(step++)
                _currentTemp.value = temp
                onTemp(temp)
                delay(scenario.intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _currentScenario.value = null
    }

    fun clear() {
        stop()
        scope.coroutineContext.cancelChildren()
    }
}
