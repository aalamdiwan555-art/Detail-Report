package com.ultra.autodetector.util

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Adds small bounded timing/position variation to an explicitly requested
 * gesture. It is not used to discover targets or evade security controls.
 */
object HumanizationEngine {
    @Volatile private var lastClickAt = 0L

    fun getMicroDelay(): Long = Random.nextLong(1L, 101L)

    fun applyJitter(targetX: Float, targetY: Float): Pair<Float, Float> {
        val range = Constants.JITTER_RANGE_PX
        return targetX + Random.nextInt(-range, range + 1),
            targetY + Random.nextInt(-range, range + 1)
    }

    fun isCooldownPassed(now: Long = System.currentTimeMillis()): Boolean =
        now - lastClickAt >= Constants.COOLDOWN_INTERVAL_MS

    fun recordClick(now: Long = System.currentTimeMillis()) {
        lastClickAt = now
    }

    fun resetCooldown() {
        lastClickAt = 0L
    }

    fun clampCoordinate(value: Float, maxValue: Float): Float =
        min(max(value, 0f), maxValue)
}