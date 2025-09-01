package com.nolly.hopperflow

import org.bukkit.block.BlockState
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryMoveItemEvent

class HopperMoveListener(private val manager: ThrottleManager) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMove(e: InventoryMoveItemEvent) {
        val initiator = e.initiator
        val holder = initiator.holder ?: return
        val loc = when (holder) {
            is BlockState -> holder.location
            is Entity -> holder.location
            else -> {
                val src = e.source.holder
                when (src) {
                    is BlockState -> src.location
                    is Entity -> src.location
                    else -> return
                }
            }
        }
        val allowed = manager.allowMove(holder, loc)
        if (!allowed) e.isCancelled = true
    }
}
