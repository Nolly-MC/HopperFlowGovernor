package com.nolly.hopperflow

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

enum class InitiatorType { HOPPER_BLOCK, HOPPER_MINECART, DROPPER, DISPENSER, OTHER }

class ChunkStats(windowSecondsInit: Int) {
    private var windowSeconds: Int = max(10, windowSecondsInit)

    private var moves = Array(InitiatorType.entries.size) { IntArray(windowSeconds) }
    private var throttles = Array(InitiatorType.entries.size) { IntArray(windowSeconds) }
    private val lastTouchedTick = AtomicLong(0)
    private var lastSec: Long = -1

    fun reconfigure(newWindowSeconds: Int) {
        val w = max(10, newWindowSeconds)
        if (w == windowSeconds) return
        windowSeconds = w
        moves = Array(InitiatorType.entries.size) { IntArray(windowSeconds) }
        throttles = Array(InitiatorType.entries.size) { IntArray(windowSeconds) }
        lastSec = -1
    }

    fun touchTick(tick: Long) {
        lastTouchedTick.set(tick)
    }

    fun recordMove(nowSec: Long, t: InitiatorType) {
        rotate(nowSec); moves[t.ordinal][(nowSec % windowSeconds).toInt()]++
    }

    fun recordThrottle(nowSec: Long, t: InitiatorType) {
        rotate(nowSec); throttles[t.ordinal][(nowSec % windowSeconds).toInt()]++
    }

    private fun rotate(nowSec: Long) {
        if (lastSec == -1L) {
            lastSec = nowSec; return
        }
        if (nowSec == lastSec) return
        var s = lastSec + 1
        while (s <= nowSec) {
            val idx = (s % windowSeconds).toInt()
            for (t in InitiatorType.entries.indices) {
                moves[t][idx] = 0; throttles[t][idx] = 0
            }
            s++
        }
        lastSec = nowSec
    }

    fun sumMoves(seconds: Int): Int = sumArray(moves, seconds)
    fun sumThrottles(seconds: Int): Int = sumArray(throttles, seconds)

    fun sumMovesByType(seconds: Int): Map<InitiatorType, Int> = sumByType(moves, seconds)
    fun sumThrottlesByType(seconds: Int): Map<InitiatorType, Int> = sumByType(throttles, seconds)

    private fun sumArray(arr: Array<IntArray>, seconds: Int): Int {
        val span = seconds.coerceAtMost(windowSeconds)
        val end = lastSec
        if (end == -1L) return 0
        var total = 0
        for (i in 0 until span) {
            val sec = end - i
            val idx = (sec % windowSeconds).toInt()
            for (t in InitiatorType.entries.indices) total += arr[t][idx]
        }
        return total
    }

    private fun sumByType(arr: Array<IntArray>, seconds: Int): Map<InitiatorType, Int> {
        val span = seconds.coerceAtMost(windowSeconds)
        val end = lastSec
        if (end == -1L) return InitiatorType.entries.associateWith { 0 }
        val out = IntArray(InitiatorType.entries.size)
        for (i in 0 until span) {
            val sec = end - i
            val idx = (sec % windowSeconds).toInt()
            for (t in InitiatorType.entries.indices) out[t] += arr[t][idx]
        }
        return InitiatorType.entries.associateWith { out[it.ordinal] }
    }

    fun lastTick(): Long = lastTouchedTick.get()
}
