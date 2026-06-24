package com.esper.probesimulator.scenarios

/**
 * Ramp from 38°F up past 41°F over ~40 steps (≈80s at 2s/step).
 * Stays elevated to keep the alert active for the demo.
 */
object CoolerFailure : Scenario {
    override val name = "Cooler Failure"
    private const val START = 38.0f
    private const val RAMP_RATE = 0.2f
    private const val PEAK = 47.0f

    override fun next(step: Int): Float = minOf(START + step * RAMP_RATE, PEAK)
    override val intervalMs = 2_000L
}
