package com.nolly.hopperflow

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkUnloadEvent

class ChunkLifecycleListener(private val manager: ThrottleManager) : Listener {
    @EventHandler
    fun onUnload(e: ChunkUnloadEvent) {
        manager.resetChunk(ChunkKey.of(e.chunk))
    }
}
