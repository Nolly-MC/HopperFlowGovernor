# HopperFlow Governor — Usage Guide

Focus: install, verify, tune, and diagnose hopper hotspots with minimal steps.

---

## Quick start

1. Drop the jar in `plugins/`.
2. Start server. Confirm:
    - Console: `HopperFlow enabled`
    - `plugins/HopperFlowGovernor/config.yml` created
3. Test throttling:
    - Build a small 20+ hopper chain in one chunk.
    - Run `/hopperflow inspect` while standing in that chunk.
    - Expect nonzero **Throttled** when load exceeds your limits.

---

## Commands

> All commands require permission (default OP). Aliases: `/hflow`, `/hfg`.

| Command                 | Purpose                               | Notes                                             |
|-------------------------|---------------------------------------|---------------------------------------------------|
| `/hopperflow help`      | Show command help                     | Also lists usage hints                            |
| `/hopperflow reload`    | Reload `config.yml`                   | Applies new limits instantly                      |
| `/hopperflow inspect`   | Chunk summary                         | Shows moves and throttles in the last window      |
| `/hopperflow detail`    | Per-initiator breakdown               | hopper_block, hopper_minecart, dropper, dispenser |
| `/hopperflow where [N]` | Top throttled locations in this chunk | Defaults to 10, max 50                            |
| `/hopperflow top [N]`   | Worst chunks (server-wide)            | Defaults to 10, max 50                            |

**Extra permission:** `hopperflow.notify` shows actionbar warnings when throttling occurs nearby.

---

## Configuration

`plugins/HopperFlowGovernor/config.yml` (commented by default):

```yml
# HopperFlow Governor — caps inventory move events to stabilize TPS.
# Pure Bukkit. No item loss. Cancels only excess moves per tick window.

# Global per-chunk defaults (used when no per-type override exists)
rate_per_chunk_per_sec: 80          # default average moves/s per chunk
burst_per_chunk: 120                # default short-term burst tokens per chunk

# Global ceiling across all chunks
max_global_rate: 5000               # average moves/s allowed globally

# Optional per-type overrides; omit or set null to use the global defaults above
per_type_limits:
  hopper_block: { rate: 80, burst: 120 }
  hopper_minecart: { rate: 40, burst: 80 }
  dropper: { rate: 40, burst: 80 }
  dispenser: { rate: 20, burst: 40 }

# Which initiators are governed?
include:
  hopper_blocks: true               # vanilla block hoppers
  hopper_minecarts: false           # hopper minecart entities
  droppers: false                   # droppers pushing into inventories
  dispensers: false                 # dispensers pushing into inventories

# Name-prefix exemptions per type (renaming the container/minecart bypasses throttle)
exempt_name_prefixes:
  hopper_block: "[FAST]"
  hopper_minecart: ""
  dropper: ""
  dispenser: ""

# World and WorldGuard exemptions
exempt_worlds: [ the_end ]
exempt_regions: [ ]                 # WG region IDs (if WorldGuard is installed)

# Player feedback
notify_players_near_throttle: true
notify_radius: 16

# Accounting window + cleanup
stats_window_seconds: 60            # time window used by /inspect, /detail, /where, /top
cleanup_after_minutes: 15           # drop idle chunk stats/buckets

# Telemetry (bStats) — opt-in.
metrics:
  enabled: true
````

Apply changes: `/hopperflow reload`.

---

## What the limits mean

* **rate\_per\_chunk\_per\_sec**: average allowed `InventoryMoveItemEvent` per chunk per second.
* **burst\_per\_chunk**: short spike capacity (token bucket).
* **per\_type\_limits**: overrides for specific initiators, otherwise defaults apply.
* **max\_global\_rate**: global safety ceiling across all chunks.

If a move is over limit, the event is cancelled for that tick. Items stay put. No loss.

---

## Exemptions

### By name prefix (simple, recommended)

* Rename a hopper to a name that starts with your prefix.
* Example: Name a hopper `[FAST] My Sorter` → bypasses throttling (if hopper\_block prefix is `[FAST]`).

### By world

* Add world name (case-insensitive) to `exempt_worlds`.

### By WorldGuard region

* Add region IDs to `exempt_regions`. Requires WorldGuard installed.
* Use this for lobbies, admin farms, or timing-critical contraptions.

### By block metadata / PDC (advanced)

* The plugin checks a tile’s metadata key `hopperflow.exempt` and PDC key `exempt` (byte `1`).
* You need a separate admin tool or script to set these flags. There is no in-plugin setter.

---

## Verification checklist

1. **Actionbar notice**

    * Build a hopper clock in one chunk.
    * Stand near it.
    * Expect: “Hoppers throttled here (chunk X,Z)” if you have `hopperflow.notify`.

2. **Inspect**

    * `/hopperflow inspect`
    * Expect: nonzero **Throttled** when load is high.

3. **Detail**

    * `/hopperflow detail`
    * Expect: counts per initiator (e.g., hopper\_block vs dropper).

4. **Where**

    * `/hopperflow where 10`
    * Expect: exact XYZ of top throttled blocks/entities in the current chunk.

5. **Top**

    * `/hopperflow top 5`
    * Expect: worst chunks, descending throttles.

6. **Exempt name test**

    * Rename one hopper to `[FAST] Something`.
    * Expect: its throttling drops to 0 while others still throttle.

7. **WorldGuard region test** (if WG installed)

    * Add region ID to `exempt_regions`.
    * Move the setup inside that region.
    * Expect: throttles drop to 0 there.

---

## Tuning guide

Start conservative, then tighten:

| Scenario                 | Suggested settings                                            |
|--------------------------|---------------------------------------------------------------|
| Vanilla SMP, small farms | rate 80, burst 120, global 5000                               |
| Modest economy servers   | rate 60, burst 100, global 4000                               |
| Large farm meta servers  | rate 40, burst 80, global 3000                                |
| Hopper minecart heavy    | per\_type hopper\_minecart rate 20, burst 40                  |
| Dropper sorters          | per\_type dropper rate 30–50; consider `[FAST]` for key lines |

Heuristic:

* If players notice slow item throughput, raise **burst** before **rate**.
* If TPS dips during spikes, lower **burst** first.
* Keep **global** high enough to avoid global clamp unless needed for safety.

---

## Admin playbook

* “Players complain about slow sorters”
  → Check `/hopperflow where` in their chunk → if one device dominates throttles, exempt it by name or region.

* “Server-wide TPS dips during surge hours”
  → Lower `burst_per_chunk` 20–30%, keep `rate_per_chunk_per_sec` steady. Consider lowering `max_global_rate` slightly.

* “Some farms must remain full-speed”
  → Use `[FAST]` prefix or a WorldGuard region.

* “Which chunk hurts most?”
  → `/hopperflow top 10` then teleport and run `/hopperflow where`.

---

## Metrics (bStats)

* Optional and anonymous. Enable with `metrics.enabled: true`.
* Shaded bStats inside the jar. No personal data.
* Useful for guiding defaults and future improvements.

---

## Compatibility notes

* Pure Bukkit/Spigot API. Works on Spigot, Paper, Purpur (1.21+).
* Safe with ClearLag, async chunk plug-ins, and standard sorter designs.
* Does not touch stack sizes, timings, or redstone mechanics. It only cancels excess moves.

---

## Troubleshooting

* “No throttling recorded”

    * Load may be below limits. Lower `rate_per_chunk_per_sec` or set shorter `burst_per_chunk`.
    * Ensure `include.*` flags are `true` for the initiator types you want.

* “Actionbar not shown”

    * Verify you have `hopperflow.notify`.
    * Check `notify_players_near_throttle` and `notify_radius`.

* “Exempt not working”

    * Name prefix must match at the **start** of the name.
    * WorldGuard region IDs must match exactly. WG must be enabled.
    * Metadata/PDC requires external setter tool.

* “TPS still dips”

    * Lower `burst_per_chunk` and type-specific bursts.
    * Consider lowering `max_global_rate`.
    * Use `/hopperflow top` to locate hotspots and fix or exempt selective contraptions.

---

## Example workflows

### A. Hard cap hopper minecarts, keep block hoppers fast

```yml
per_type_limits:
  hopper_block: { rate: 80, burst: 120 }
  hopper_minecart: { rate: 20, burst: 30 }
include:
  hopper_blocks: true
  hopper_minecarts: true
```

### B. Allow droppers in sorter cores, clamp dispensers

```yml
per_type_limits:
  dropper: { rate: 60, burst: 100 }
  dispenser: { rate: 10, burst: 20 }
include:
  droppers: true
  dispensers: true
exempt_name_prefixes:
  dropper: "[FAST]"
```

### C. Exempt spawn chunks and hub region

```yml
exempt_worlds: [ world_nether ]
exempt_regions: [ hub, spawn ]
```

---

## FAQ

**Q: Does this delete items?**  
A: No. It only cancels the specific `InventoryMoveItemEvent` for that tick. Items remain in place.

**Q: Is Paper required?**  
A: No. Pure Bukkit/Spigot API. Paper is supported.

**Q: Can I whitelist entire farms?**  
A: Yes. Use name prefix, region, or world exemption.

**Q: Where do I see exact coordinates?**  
A: `/hopperflow where` in the affected chunk.
