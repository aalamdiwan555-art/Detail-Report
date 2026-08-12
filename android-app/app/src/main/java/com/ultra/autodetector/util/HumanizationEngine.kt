package com.ultra.autodetector.util

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Adds small bounded timing/position variation to an explicitly requested
 * gesture. It is not used to discover targets or evade security controls.
 * 
 * Purpose:
 * - Adds human-like micro variation to avoid robotic precision
 * - Enforces cooldown between actions
 */
object HumanizationEngine {

    @Volatile 
    private var lastClickAt = 0L

    /**
     * Returns a small random delay between 1ms to 100ms
     */
    fun getMicroDelay(): Long = Random.nextLong(1L, 101L)

    /**
     * Applies small random jitter to X,Y coordinates
     * FIXED: Now correctly returns Pair
     */
    fun applyJitter(targetX: Float, targetY: Float): Pair<Float, Float> {
        val range = Constants.JITTER_RANGE_PX
        val jitterX = Random.nextInt(-range, range + 1)
        val jitterY = Random.nextInt(-range, range + 1)
        return Pair(
            targetX + jitterX,
            targetY + jitterY
        )
    }

    /**
     * Checks if cooldown period has passed since last click
     */
    fun isCooldownPassed(now: Long = System.currentTimeMillis()): Boolean {
        return now - lastClickAt >= Constants.COOLDOWN_INTERVAL_MS
    }

    /**
     * Records current time as last click time
     */
    fun recordClick(now: Long = System.currentTimeMillis()) {
        lastClickAt = now
    }

    /**
     * Resets cooldown timer
     */
    fun resetCooldown() {
        lastClickAt = 0L
    }

    /**
     * Clamps coordinate value between 0 and maxValue
     */
    fun clampCoordinate(value: Float, maxValue: Float): Float {
        return min(max(value, 0f), maxValue)
    }
}
