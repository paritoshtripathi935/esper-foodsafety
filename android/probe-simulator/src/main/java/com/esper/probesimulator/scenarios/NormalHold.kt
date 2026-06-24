package com.esper.probesimulator.scenarios

/** Steady ~38°F with minor ±0.2°F noise. */
object NormalHold : Scenario {
    override val name = "Normal Hold"
    override fun next(step: Int): Float = 38.0f + ((step % 3) - 1) * 0.2f
    override val intervalMs = 2_000L
}
