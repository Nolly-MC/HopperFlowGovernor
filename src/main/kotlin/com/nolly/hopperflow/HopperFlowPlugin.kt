package com.nolly.hopperflow

import org.bstats.bukkit.Metrics
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin

class HopperFlowPlugin : JavaPlugin() {
    lateinit var configModel: PluginConfig; private set
    lateinit var manager: ThrottleManager; private set
    private var tickTaskId: Int = -1
    private var metrics: Metrics? = null

    override fun onEnable() {
        saveDefaultConfig()
        configModel = PluginConfig.from(config)

        if (configModel.metricsEnabled) {
            try {
                metrics = Metrics(this, 27129)
            } catch (_: Throwable) {
            }
        }

        manager = ThrottleManager(this, configModel)
        server.pluginManager.registerEvents(HopperMoveListener(manager), this)
        server.pluginManager.registerEvents(ChunkLifecycleListener(manager), this)

        val cmd = HopperFlowCommand(this, manager)
        getCommand("hopperflow")?.apply { setExecutor(cmd); tabCompleter = cmd }

        tickTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, { manager.onTick() }, 1L, 1L)
        logger.info("HopperFlow enabled")
    }

    override fun onDisable() {
        if (tickTaskId != -1) Bukkit.getScheduler().cancelTask(tickTaskId)
        HandlerList.unregisterAll(this)
        manager.shutdown()
    }

    fun reloadAll() {
        reloadConfig()
        configModel = PluginConfig.from(config)
        manager.applyConfig(configModel)
        metrics = null
        if (configModel.metricsEnabled) {
            try {
                metrics = Metrics(this, 27129)
            } catch (_: Throwable) {
            }
        }
    }
}
