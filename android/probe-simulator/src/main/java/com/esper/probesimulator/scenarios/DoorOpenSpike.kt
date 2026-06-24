package com.esper.probesimulator.scenarios

/**
 * Brief spike from 38°F up to 45°F over 5 steps, then recovery back to 38°F.
 * Demonstrates transient alert + re-arm.
 */
object DoorOpenSpike : Scenario {
    override val name = "Door Open Spike"
    private val curve = floatArrayOf(38f, 40f, 43f, 45f, 43f, 40f, 38f, 38f, 38f, 38f)

    override fun next(step: Int): Float = curve.getOrElse(step % curve.size) { 38f }
    override val intervalMs = 2_000L
}
