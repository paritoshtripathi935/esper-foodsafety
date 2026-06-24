package com.esper.foodsafety.alerts

data class AlertRule(
    val ruleId: String,
    val station: String,
    val maxTempF: Float,
)

val DEFAULT_ALERT_RULES = listOf(
    AlertRule("rule-walk-in-cooler-1", "walk-in-cooler-1", 41f),
    AlertRule("rule-walk-in-cooler-2", "walk-in-cooler-2", 41f),
    AlertRule("rule-prep-table-1",     "prep-table-1",     41f),
    AlertRule("rule-prep-table-2",     "prep-table-2",     41f),
)
