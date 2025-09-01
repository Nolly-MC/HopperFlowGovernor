package com.nolly.hopperflow

import org.bukkit.Chunk
import org.bukkit.Location

data class ChunkKey(val world: String, val x: Int, val z: Int) {
    override fun toString(): String = "$world:$x,$z"

    companion object {
        fun of(chunk: Chunk): ChunkKey = ChunkKey(chunk.world.name, chunk.x, chunk.z)
        fun of(loc: Location): ChunkKey = ChunkKey(loc.world!!.name, loc.chunk.x, loc.chunk.z)
    }
}
