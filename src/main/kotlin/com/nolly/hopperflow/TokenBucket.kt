package com.nolly.hopperflow

class TokenBucket(private var ratePerSec: Double, private var burst: Double) {
    private var tokens: Double = burst

    fun configure(newRatePerSec: Double, newBurst: Double) {
        ratePerSec = newRatePerSec
        burst = newBurst
        tokens = tokens.coerceAtMost(burst)
    }

    fun refill(ticks: Int) {
        val add = ratePerSec / 20.0 * ticks
        tokens = (tokens + add).coerceAtMost(burst)
    }

    fun tryTake(): Boolean {
        return if (tokens >= 1.0) {
            tokens -= 1.0
            true
        } else false
    }
}
