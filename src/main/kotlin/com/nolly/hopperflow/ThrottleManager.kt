package com.nolly.hopperflow

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Dispenser
import org.bukkit.block.Dropper
import org.bukkit.block.Hopper
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.entity.minecart.HopperMinecart
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class ThrottleManager(private val plugin: Plugin, private var cfg: PluginConfig) {
    private val perChunkBuckets = ConcurrentHashMap<ChunkKey, EnumMap<InitiatorType, TokenBucket>>()
    private val stats = ConcurrentHashMap<ChunkKey, ChunkStats>()
    private val hotspots = ConcurrentHashMap<ChunkKey, MutableMap<String, Int>>()
    private val globalBucket = TokenBucket(cfg.maxGlobalRate, max(1.0, cfg.maxGlobalRate))
    private val exemptMetaKey = "hopperflow.exempt"
    private val exemptPdcKey = NamespacedKey(plugin, "exempt")
    private var tickCounter: Long = 0

    private val wgHook: Any? = run {
        val installed = Bukkit.getPluginManager().getPlugin("WorldGuard") != null
        if (!installed) null else WG.tryInit(plugin)
    }

    fun applyConfig(newCfg: PluginConfig) {
        cfg = newCfg
        globalBucket.configure(cfg.maxGlobalRate, max(1.0, cfg.maxGlobalRate))
        perChunkBuckets.forEach { (_, map) ->
            for (t in InitiatorType.entries) {
                val pair = cfg.effectiveLimits(t)
                map[t]?.configure(pair.first, pair.second)
            }
        }
        stats.values.forEach { it.reconfigure(cfg.statsWindowSeconds) }
    }

    fun onTick() {
        tickCounter++
        perChunkBuckets.values.forEach { m -> m.values.forEach { it.refill(1) } }
        globalBucket.refill(1)
        if (tickCounter % (20L * 30L) == 0L) {
            val cutoffTicks = cfg.cleanupAfterMinutes * 60L * 20L
            val threshold = tickCounter - cutoffTicks
            stats.entries.removeIf { it.value.lastTick() < threshold }
            perChunkBuckets.keys.removeIf { key -> !stats.containsKey(key) }
            hotspots.keys.removeIf { key -> !stats.containsKey(key) }
        }
    }

    fun shutdown() {
        perChunkBuckets.clear(); stats.clear(); hotspots.clear()
    }

    fun resetChunk(key: ChunkKey) {
        perChunkBuckets.remove(key)
        stats.remove(key)
        hotspots.remove(key)
    }

    fun allowMove(holder: Any, loc: Location): Boolean {
        val type = when (holder) {
            is Hopper -> InitiatorType.HOPPER_BLOCK
            is HopperMinecart -> InitiatorType.HOPPER_MINECART
            is Dropper -> InitiatorType.DROPPER
            is Dispenser -> InitiatorType.DISPENSER
            else -> InitiatorType.OTHER
        }

        if (!isTypeIncluded(type)) {
            record(loc, true, false, type)
            return true
        }

        val w = loc.world!!.name.lowercase()
        if (cfg.exemptWorlds.contains(w) || isExemptByRegion(loc) || isExemptByName(holder, type) || isExemptByFlag(
                holder
            )
        ) {
            record(loc, move = true, throttled = false, type = type)
            return true
        }

        if (!globalBucket.tryTake()) {
            record(loc, false, true, type)
            bumpHotspot(loc, type)
            notifyIfNeeded(ChunkKey.of(loc), loc)
            return false
        }

        val key = ChunkKey.of(loc)
        val map = perChunkBuckets.computeIfAbsent(key) { EnumMap(InitiatorType::class.java) }
        val (rate, burst) = cfg.effectiveLimits(type)
        val bucket = map.computeIfAbsent(type) { TokenBucket(rate, burst) }
        val allowed = bucket.tryTake()

        record(loc, allowed, !allowed, type)
        if (!allowed) {
            bumpHotspot(loc, type)
            notifyIfNeeded(key, loc)
        }
        return allowed
    }

    private fun isTypeIncluded(t: InitiatorType): Boolean = when (t) {
        InitiatorType.HOPPER_BLOCK -> cfg.includeHopperBlocks
        InitiatorType.HOPPER_MINECART -> cfg.includeHopperMinecarts
        InitiatorType.DROPPER -> cfg.includeDroppers
        InitiatorType.DISPENSER -> cfg.includeDispensers
        else -> false
    }

    private fun isExemptByRegion(loc: Location): Boolean {
        if (wgHook == null || cfg.exemptRegions.isEmpty()) return false
        return try {
            WG.isInAnyExemptRegion(wgHook, loc, cfg.exemptRegions)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isExemptByName(holder: Any, type: InitiatorType): Boolean {
        val prefix = cfg.prefixFor(type)
        if (prefix.isEmpty()) return false
        val name = when (holder) {
            is Hopper -> holder.customName
            is HopperMinecart -> holder.customName
            is Dropper -> holder.customName
            is Dispenser -> holder.customName
            else -> null
        }
        return name?.startsWith(prefix) == true
    }

    private fun isExemptByFlag(holder: Any): Boolean {
        if (holder is Hopper) {
            val block = holder.block
            if (block.hasMetadata(exemptMetaKey)) {
                val v = block.getMetadata(exemptMetaKey).firstOrNull { it.owningPlugin == plugin }?.asBoolean()
                if (v == true) return true
            }
            val state = block.state
            if (state is TileState) {
                val pdc = state.persistentDataContainer
                if (pdc.has(exemptPdcKey)) {
                    val b = pdc.getOrDefault(exemptPdcKey, PersistentDataType.BYTE, 0.toByte())
                    if (b.toInt() != 0) return true
                }
            }
        }
        return false
    }

    private fun record(loc: Location, move: Boolean, throttled: Boolean, type: InitiatorType) {
        val key = ChunkKey.of(loc)
        val nowSec = System.currentTimeMillis() / 1000L
        val s = stats.computeIfAbsent(key) { ChunkStats(cfg.statsWindowSeconds) }
        s.touchTick(tickCounter)
        if (move) s.recordMove(nowSec, type)
        if (throttled) s.recordThrottle(nowSec, type)
    }

    private fun bumpHotspot(loc: Location, type: InitiatorType) {
        val key = ChunkKey.of(loc)
        val map = hotspots.computeIfAbsent(key) { ConcurrentHashMap() }
        val id = "${loc.blockX},${loc.blockY},${loc.blockZ}|${type.name}"
        map.merge(id, 1) { a, b -> a + b }
    }

    private fun notifyIfNeeded(key: ChunkKey, at: Location) {
        if (!cfg.notifyPlayers) return
        val r2 = (cfg.notifyRadius * cfg.notifyRadius).toDouble()
        val cx = key.x * 16 + 8
        val cz = key.z * 16 + 8
        val world = at.world ?: return
        for (p: Player in world.players) {
            val dx = p.location.x - cx
            val dz = p.location.z - cz
            if (dx * dx + dz * dz <= r2) {
                if (p.hasPermission("hopperflow.notify")) {
                    p.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent("Hoppers throttled here (chunk ${key.x},${key.z})").apply {
                            color = ChatColor.RED
                        })
                }
                plugin.logger.info("\u001B[31mHoppers throttled near player ${p.name} at ${world.name}:${at.blockX},${at.blockY},${at.blockZ} (chunk ${key.x},${key.z})\u001B[0m")
            }
        }
    }

    fun top(seconds: Int, limit: Int): List<Pair<ChunkKey, Int>> {
        val sec = seconds.coerceAtLeast(5)
        return stats.entries.map { it.key to it.value.sumThrottles(sec) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.take(limit)
    }

    fun inspect(key: ChunkKey, seconds: Int): InspectData {
        val s = stats[key]
        val throttled = s?.sumThrottles(seconds) ?: 0
        val moved = s?.sumMoves(seconds) ?: 0
        val (rate, burst) = cfg.effectiveLimits(InitiatorType.HOPPER_BLOCK)
        return InspectData(moved, throttled, 0.0, burst, rate, burst)
    }

    fun detail(key: ChunkKey, seconds: Int): DetailData {
        val s = stats[key]
        val moves = s?.sumMovesByType(seconds) ?: emptyMap()
        val throttles = s?.sumThrottlesByType(seconds) ?: emptyMap()
        return DetailData(moves, throttles)
    }

    fun where(key: ChunkKey, limit: Int): List<LocationHit> {
        val map = hotspots[key] ?: return emptyList()
        return map.entries.sortedByDescending { it.value }.take(limit).map { (k, count) ->
            val parts = k.split('|')
            val xyz = parts[0].split(',')
            val type = InitiatorType.valueOf(parts[1])
            LocationHit(
                x = xyz[0].toInt(), y = xyz[1].toInt(), z = xyz[2].toInt(), type = type, throttledCount = count
            )
        }
    }

    data class InspectData(
        val moved: Int,
        val throttled: Int,
        val tokensNow: Double,
        val burst: Double,
        val ratePerSec: Double,
        val burstConfigured: Double
    )

    data class DetailData(
        val movesByType: Map<InitiatorType, Int>, val throttlesByType: Map<InitiatorType, Int>
    )

    data class LocationHit(val x: Int, val y: Int, val z: Int, val type: InitiatorType, val throttledCount: Int)
}
