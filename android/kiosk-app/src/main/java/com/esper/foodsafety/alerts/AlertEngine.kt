package com.esper.foodsafety.alerts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlertEngine(private var rules: List<AlertRule>) {

    /** Default threshold used for stations not covered by [rules]. */
    private val defaultMaxTempF = 41f

    data class AlertState(
        val rule: AlertRule,
        val measuredTemp: Float,
    )

    private val _activeAlerts = MutableStateFlow<List<AlertState>>(emptyList())
    val activeAlerts: StateFlow<List<AlertState>> = _activeAlerts.asStateFlow()

    private val breachingRules = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Evaluate [tempF] for [station]. Returns a newly triggered [AlertState] if a fresh breach
     * just started (i.e. the rule was not already breaching), or null otherwise. Clears the alert
     * state when the temperature returns below threshold.
     */
    fun evaluate(station: String, tempF: Float): AlertState? {
        var newAlert: AlertState? = null
        // Use matching rules; fall back to a synthetic rule for unknown stations
        val relevant = rules.filter { it.station == station }.ifEmpty {
            listOf(AlertRule("rule-$station", station, defaultMaxTempF))
        }
        val current = _activeAlerts.value.toMutableList()

        for (rule in relevant) {
            if (tempF > rule.maxTempF) {
                if (breachingRules.add(rule.ruleId)) {
                    val state = AlertState(rule, tempF)
                    current.removeAll { it.rule.ruleId == rule.ruleId }
                    current.add(state)
                    newAlert = state
                }
            } else {
                if (breachingRules.remove(rule.ruleId)) {
                    current.removeAll { it.rule.ruleId == rule.ruleId }
                }
            }
        }
        _activeAlerts.value = current
        return newAlert
    }

    fun updateRule(ruleId: String, newMaxTempF: Float) {
        val updated = rules.toMutableList()
        val idx = updated.indexOfFirst { it.ruleId == ruleId }
        if (idx != -1) updated[idx] = updated[idx].copy(maxTempF = newMaxTempF)
        rules = updated
    }

    val hasActiveAlert: Boolean get() = _activeAlerts.value.isNotEmpty()

    fun firstActiveRuleId(): String? = _activeAlerts.value.firstOrNull()?.rule?.ruleId
}
