package com.nolly.hopperflow

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class HopperFlowCommand(
    private val plugin: HopperFlowPlugin, private val manager: ThrottleManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        return when (args[0].lowercase()) {
            "help" -> {
                if (!sender.hasPermission("hopperflow.command.help")) {
                    sender.sendMessage("${ChatColor.RED}No permission."); return true
                }
                sender.sendMessage("${ChatColor.GOLD}HopperFlow Commands:")
                sender.sendMessage("${ChatColor.YELLOW}/$label help ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Show this help message")
                sender.sendMessage("${ChatColor.YELLOW}/$label reload ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Reload config")
                sender.sendMessage("${ChatColor.YELLOW}/$label inspect ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Show summary of hopper activity in your current chunk")
                sender.sendMessage("${ChatColor.YELLOW}/$label detail ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Show detailed breakdown of hopper activity in your current chunk")
                sender.sendMessage("${ChatColor.YELLOW}/$label where [limit] ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Show top throttled hopper locations in your current chunk (default limit 10, max 50)")
                sender.sendMessage("${ChatColor.YELLOW}/$label top [limit] ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Show top throttled chunks server-wide in the last stats window (default limit 10, max 50)")
                true
            }

            "reload" -> {
                if (!sender.hasPermission("hopperflow.command.reload")) {
                    sender.sendMessage("${ChatColor.RED}No permission."); true
                } else {
                    plugin.reloadAll(); sender.sendMessage("${ChatColor.YELLOW}HopperFlow config reloaded."); true
                }
            }

            "inspect" -> {
                if (!sender.hasPermission("hopperflow.command.inspect")) {
                    sender.sendMessage("${ChatColor.RED}No permission."); true
                } else {
                    if (sender !is Player) {
                        sender.sendMessage("${ChatColor.RED}Player-only."); return true
                    }
                    val key = ChunkKey.of(sender.location)
                    val d = manager.inspect(key, plugin.configModel.statsWindowSeconds)
                    sender.sendMessage("${ChatColor.AQUA}Chunk ${key.x},${key.z} — ${key.world}")
                    sender.sendMessage("${ChatColor.GRAY}Moves: ${ChatColor.WHITE}${d.moved} ${ChatColor.GRAY} Throttled: ${ChatColor.WHITE}${d.throttled} ${ChatColor.DARK_GRAY}(last ${plugin.configModel.statsWindowSeconds}s)")
                    sender.sendMessage("${ChatColor.GRAY}Rate: ${ChatColor.WHITE}${d.ratePerSec}/s ${ChatColor.GRAY}Burst: ${ChatColor.WHITE}${d.burst}")
                    true
                }
            }

            "detail" -> {
                if (!sender.hasPermission("hopperflow.command.detail")) {
                    sender.sendMessage("${ChatColor.RED}No permission."); true
                } else {
                    if (sender !is Player) {
                        sender.sendMessage("${ChatColor.RED}Player-only."); return true
                    }
                    val key = ChunkKey.of(sender.location)
                    val d = manager.detail(key, plugin.configModel.statsWindowSeconds)
                    sender.sendMessage("${ChatColor.GOLD}Breakdown (last ${plugin.configModel.statsWindowSeconds}s) @ ${key.world} ${key.x},${key.z}")
                    for (t in InitiatorType.entries) {
                        val mv = d.movesByType[t] ?: 0
                        val th = d.throttlesByType[t] ?: 0
                        if (mv > 0 || th > 0) {
                            sender.sendMessage("${ChatColor.GRAY}- ${t.name.lowercase()}  ${ChatColor.WHITE}moves=${mv}  ${ChatColor.RED}throttled=${th}")
                        }
                    }
                    true
                }
            }

            "where" -> {
                if (!sender.hasPermission("hopperflow.command.where")) {
                    sender.sendMessage("${ChatColor.RED}No permission."); true
                } else {
                    if (sender !is Player) {
                        sender.sendMessage("${ChatColor.RED}Player-only."); return true
                    }
                    val key = ChunkKey.of(sender.location)
                    val limit = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 50) ?: 10
                    val list = manager.where(key, limit)
                    if (list.isEmpty()) {
                        sender.sendMessage("${ChatColor.YELLOW}No throttled locations recorded for this chunk.")
                    } else {
                        sender.sendMessage("${ChatColor.GOLD}Top throttled locations @ ${key.world} ${key.x},${key.z}:")
                        var i = 1
                        for (h in list) {
                            sender.sendMessage("${ChatColor.GRAY}$i. ${ChatColor.WHITE}${h.x},${h.y},${h.z} ${ChatColor.DARK_GRAY}(${h.type.name.lowercase()}) ${ChatColor.RED}throttled=${h.throttledCount}")
                            i++
                        }
                    }
                    true
                }
            }

            "top" -> {
                if (!sender.hasPermission("hopperflow.command.top")) {
                    sender.sendMessage("${ChatColor.RED}No permission."); true
                } else {
                    val limit = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 50) ?: 10
                    val list = manager.top(plugin.configModel.statsWindowSeconds, limit)
                    if (list.isEmpty()) sender.sendMessage("${ChatColor.YELLOW}No throttled chunks in the last ${plugin.configModel.statsWindowSeconds}s.")
                    else {
                        sender.sendMessage("${ChatColor.GOLD}Top throttled chunks (${plugin.configModel.statsWindowSeconds}s):")
                        var i = 1
                        for ((key, count) in list) {
                            sender.sendMessage("${ChatColor.GRAY}$i. ${ChatColor.WHITE}${key.world} ${key.x},${key.z} ${ChatColor.DARK_GRAY}- ${ChatColor.RED}$count")
                            i++
                        }
                    }
                    true
                }
            }

            else -> {
                sender.sendMessage("${ChatColor.RED}Unknown subcommand."); true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): MutableList<String> {
        when (args.size) {
            0 -> return mutableListOf("help", "top", "inspect", "detail", "where", "reload")
            1 -> return listOf("help", "top", "inspect", "detail", "where", "reload").filter {
                it.startsWith(
                    args[0],
                    true
                )
            }.toMutableList()

            2 -> if (args[0].equals("top", true) || args[0].equals("where", true)) {
                return listOf("5", "10", "20", "30", "50").toMutableList()
            }
        }
        return mutableListOf()
    }
}
