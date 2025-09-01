package com.nolly.hopperflow

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import kotlin.math.max

data class PerTypeLimit(val rate: Double?, val burst: Double?)

data class PluginConfig(
    // global defaults
    var ratePerChunkPerSec: Double,
    var burstPerChunk: Double,
    var maxGlobalRate: Double,

    // includes
    var includeHopperBlocks: Boolean,
    var includeHopperMinecarts: Boolean,
    var includeDroppers: Boolean,
    var includeDispensers: Boolean,

    // exemptions
    var exemptWorlds: Set<String>,
    var exemptRegions: Set<String>,
    var exemptNamePrefixes: Map<InitiatorType, String>,

    // per-type overrides
    var perTypeLimits: Map<InitiatorType, PerTypeLimit>,

    // ux + maintenance
    var notifyPlayers: Boolean,
    var notifyRadius: Int,
    var statsWindowSeconds: Int,
    var cleanupAfterMinutes: Int,

    // telemetry
    var metricsEnabled: Boolean,
) {
    fun effectiveLimits(type: InitiatorType): Pair<Double, Double> {
        val lim = perTypeLimits[type]
        val rate = lim?.rate ?: ratePerChunkPerSec
        val burst = lim?.burst ?: burstPerChunk
        return rate to burst
    }

    fun prefixFor(type: InitiatorType): String = exemptNamePrefixes[type] ?: ""

    companion object {
        fun from(cfg: FileConfiguration): PluginConfig {
            val rateDefault = cfg.getDouble("rate_per_chunk_per_sec", 80.0)
            val burstDefault = cfg.getDouble("burst_per_chunk", 120.0)
            val maxGlobal = cfg.getDouble("max_global_rate", 5000.0)

            val inc = cfg.getConfigurationSection("include")
            val includeHopperBlocks = inc?.getBoolean("hopper_blocks", true) ?: true
            val includeHopperMinecarts = inc?.getBoolean("hopper_minecarts", false) ?: false
            val includeDroppers = inc?.getBoolean("droppers", false) ?: false
            val includeDispensers = inc?.getBoolean("dispensers", false) ?: false

            val perTypeSec = cfg.getConfigurationSection("per_type_limits")
            val perType = mutableMapOf<InitiatorType, PerTypeLimit>()
            fun readLimit(key: String, t: InitiatorType) {
                val s = perTypeSec?.getConfigurationSection(key) ?: return
                val rate = s.getDoubleOrNull("rate")
                val burst = s.getDoubleOrNull("burst")
                if (rate != null || burst != null) perType[t] = PerTypeLimit(rate, burst)
            }
            readLimit("hopper_block", InitiatorType.HOPPER_BLOCK)
            readLimit("hopper_minecart", InitiatorType.HOPPER_MINECART)
            readLimit("dropper", InitiatorType.DROPPER)
            readLimit("dispenser", InitiatorType.DISPENSER)

            val worlds = cfg.getStringList("exempt_worlds").map { it.lowercase() }.toSet()
            val regions = cfg.getStringList("exempt_regions").toSet()

            val prefixSec = cfg.getConfigurationSection("exempt_name_prefixes")
            val prefixes = mutableMapOf<InitiatorType, String>()
            fun putPrefix(k: String, t: InitiatorType) {
                val v = prefixSec?.getString(k)?.trim().orEmpty()
                if (v.isNotEmpty()) prefixes[t] = v
            }
            putPrefix("hopper_block", InitiatorType.HOPPER_BLOCK)
            putPrefix("hopper_minecart", InitiatorType.HOPPER_MINECART)
            putPrefix("dropper", InitiatorType.DROPPER)
            putPrefix("dispenser", InitiatorType.DISPENSER)

            val notifyPlayers = cfg.getBoolean("notify_players_near_throttle", true)
            val notifyRadius = cfg.getInt("notify_radius", 16)
            val windowSecs = max(10, cfg.getInt("stats_window_seconds", 60))
            val cleanupMins = max(1, cfg.getInt("cleanup_after_minutes", 15))

            val metricsSection = cfg.getConfigurationSection("metrics")
            val metricsEnabled = metricsSection?.getBoolean("enabled", true) ?: true

            return PluginConfig(
                ratePerChunkPerSec = rateDefault,
                burstPerChunk = burstDefault,
                maxGlobalRate = maxGlobal,

                includeHopperBlocks = includeHopperBlocks,
                includeHopperMinecarts = includeHopperMinecarts,
                includeDroppers = includeDroppers,
                includeDispensers = includeDispensers,

                exemptWorlds = worlds,
                exemptRegions = regions,
                exemptNamePrefixes = prefixes,

                perTypeLimits = perType,

                notifyPlayers = notifyPlayers,
                notifyRadius = notifyRadius,
                statsWindowSeconds = windowSecs,
                cleanupAfterMinutes = cleanupMins,

                metricsEnabled = metricsEnabled
            )
        }
    }
}

private fun ConfigurationSection.getDoubleOrNull(path: String): Double? {
    if (!contains(path)) return null
    return when (val v = get(path)) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}
