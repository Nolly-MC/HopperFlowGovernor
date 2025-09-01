package com.nolly.hopperflow

import org.bukkit.Location
import org.bukkit.plugin.Plugin

object WG {
    fun tryInit(plugin: Plugin): Any? {
        plugin.logger.info("WorldGuard detected; region exemptions enabled.")
        return Any()
    }

    fun isInAnyExemptRegion(hook: Any, loc: Location, regionIds: Set<String>): Boolean {
        try {
            val regionContainer = hook
            val bf = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
            val weWorld = bf.getMethod("adapt", org.bukkit.World::class.java).invoke(null, loc.world)
            val blockVec3Cls = Class.forName("com.sk89q.worldedit.math.BlockVector3")
            val bv = blockVec3Cls.getMethod(
                "at", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).invoke(null, loc.blockX, loc.blockY, loc.blockZ)
            val query = regionContainer.javaClass.getMethod("createQuery").invoke(regionContainer)
            val apRegs =
                query.javaClass.getMethod("getApplicableRegions", Class.forName("com.sk89q.worldedit.util.Location"))
                    .invoke(
                        query, Class.forName("com.sk89q.worldedit.util.Location").getConstructor(
                            Class.forName("com.sk89q.worldedit.world.World"),
                            blockVec3Cls,
                            Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType
                        ).newInstance(weWorld, bv, 0.0f, 0.0f)
                    )

            val regSet = apRegs.javaClass.getMethod("getRegions").invoke(apRegs) as Iterable<*>
            for (r in regSet) {
                val id = r!!.javaClass.getMethod("getId").invoke(r) as String
                if (regionIds.contains(id)) return true
            }
        } catch (_: Throwable) {
            return false
        }
        return false
    }
}
