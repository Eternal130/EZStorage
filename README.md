# Unified Storage

Simple storage mod for Minecraft 1.7.10 (Forge).

## Description

Unified Storage (former Simple storage) introduces an early-game storage system that scales and evolves as players progress, while keeping the vanilla flair. Want to put 100k Cobblestone in 1 slot? No problem. The blocks in the mod can add a crafting grid, additional storage, and more. Also includes integration into some mods for easier crafting or additional features!

> **This fork** adds external storage compatibility (via the Storage Adapter block), TFC+ (TerraFirmaCraft Plus) integration (crafting system, item restrictions, tier-appropriate recipes, and a dedicated food storage system), and significant performance optimizations to adapt to the development flow and storage needs of TFC+.

## Blocks & Items

- **Storage Core**
  - This is the core of your storage system
  - Click on this block to open the GUI (search box included), and add adjacent blocks to expand
  - Each system can only have 1 Storage Core
  - This block can only be broken if it contains no items
- **Storage Box**
  - Tier 1 storage add-on wich increases the storage capacity of the Storage Core by a small amount
- **Condensed Storage Box**
  - Tier 2 storage add-on
- **Hyper Storage Box**
  - Tier 3 storage add-on
- **Proxy Port**
  - Expose the storage inventory to hoppers, conduits, machines and AE2 storage bus
- **Crafting Box**
  - This adds a crafting grid to the GUI of your Storage Core (compatible with NEI + clicking for easy crafting from the internal inventory)
- **Storage Adapter**
  - Connects adjacent external inventories (chests, machines, etc.) to the storage system
  - Items in connected inventories become visible and accessible from the Storage Core GUI, alongside internal storage
  - Supports double chests and sided inventories
  - External inventories **must be in loaded chunks** to function — if the target area is unloaded, external items will not be accessible
- **Food Storage Box** *(TFC+)*
  - Bulk storage for a single kind of TFC+ food, measured by weight (oz) instead of item count
  - No GUI by design — items move in and out via hoppers or the Storage Core terminal
  - Foods stack together when their identity matches: same item, processing tags (brined/pickled/salted/cooked/dried), cook stage bucket, meal ingredients, infusion and yeast — taste never blocks stacking
  - Stored food keeps decaying based on environment temperature (identical math to a TFC chest, including offline catch-up); fully rotted contents are cleared automatically
  - Terminal integration: the entry shows total oz (badge) and TFC-style weight/decay bars; left click extracts 160 oz, right click 80 oz, the bulk key a full portion; less than the target extracts the remainder
  - Taste of extracted food is rewritten to the stored weighted average; the tooltip tiers it by the observer's cooking skill, exactly like regular TFC food
  - Per-food extraction caps: config defaults prefilled with the TFC sandwich (10 oz) and salad (20 oz); any unlisted food falls back to its own max weight (meals 20 oz, plain food 160 oz), all overridable per item via config
  - Optional storage cap (oz, config; unlimited by default): over-cap inserts are knife-split when any knife is stored in the system — the fitting part is absorbed and the rest returned; minimum split is 1 oz, and non-splittable foods (configurable no-split blacklist, defaulting to the TFC sandwich and salad) are rejected whole
  - Container foods (salads): the container is stripped into system storage on insert and consumed back from it on extract; extraction is blocked with a "requires: ..." hint while the container is unavailable
  - Hot food cools to ambient on insert; smoke progress resets (completed smokes are unaffected)
  - The stored food is rendered flat on the box's faces; Waila/WDMla shows contents, weight and decay
  - Cannot be broken while non-empty (same protection as the Storage Core)
- **Portable Storage Panel**
  - A wireless terminal that provides remote access to your Storage Core from anywhere
  - Tier can be upgraded for increased range, with an infinity tier offering unlimited range
  - A crafting grid can also be added via upgrade
  - Works without chunkloading the target Storage Core — internal storage is always accessible
  - Note: external storage connected via Storage Adapter requires the target chunks to be loaded

## Mod Integration

- **Not Enough Items** (GTNH version)
  - Overlay recipes
  - One-click crafting
  - NEI-like search
- **Waila**
  - Advanced tooltip overlay
  - Show storage content (items/types count) in world tooltip
  - Food Storage Box: contents, total weight and decay (works with WDMla's old-API compat layer)
- **JABBA**
  - Move the storage core from one place to another place using the dolly from Jabba
- **Crafting Tweaks**
  - Show typical crafting tweaks buttons on crafting grid
- **Et Futurum Requiem**
  - Spectator mode
- **Applied Energistics 2**
  - Inventory proxy can be used with AE storage buses

## Remarks

This mod is intented to be a compact storage solution, and not an automated storage network. As of right now, I'm not going to include any features like filtered output, network cables, external monitors, or anything else remeniscent of Applied Energistics. If you have an idea how such features would fit nicely in vanilla worlds, feel free to open an issue for discussion.

As from my side, this mod is feature-completed. I'll try fixing bugs as they were found or make improvements where possible. However, any contribution in form of troubleshooting or pull requests for bugfixes, improvements, mod compat, or new features are welcome at any time.

## Contribution

Feel free to open PRs for features, improvements, or compatibility fixes. I'm maintaining this at minimal effort for use on my server/modpack.

## Changes compared to upstream

This fork adds TFC+ (TerraFirmaCraft Plus) integration, external storage support, and performance optimizations to adapt to the development flow and storage needs of TFC+.

### TFC+ Adaptations

- TFC+ crafting system integration in the storage crafting grid
- TFC+ item restrictions applied to internal storage (e.g. tool damage)
- Recipes aligned with TFC+ progression (e.g. portable panel tier 3 uses black steel ingot instead of ender eye)
- Ore dictionary matching for upgrade recipes to use TFC+ materials

### External Storage

- Storage Adapter block connects adjacent external inventories to the storage system
- Unified I/O: items can be inserted into and extracted from external inventories via the Storage Core GUI
- Double chest support with proper item validation
- Chunk loading requirement: external inventories must be in loaded chunks

### Food Storage

- Food Storage Box: single-kind bulk food storage aggregated by weight, with TFC-faithful decay simulation (environment temperature, offline catch-up, rot protection) and weight-averaged taste
- Native storage provider integration: the box appears in the terminal with total-oz badge, TFC weight/decay bars, skill-tiered taste tooltip, and weight-based extraction semantics (160/80 oz, remainder fallback)
- Knife cap-splitting (system-wide knife detection), container flow through system storage (strip on insert, consume on extract, named missing-container hints), per-food extraction caps and a no-split blacklist (configurable)
- Waila/WDMla provider, TESR food icon on the block faces, non-empty break protection

### Performance Optimizations

- Replaced `ArrayList` with `LinkedHashMap` bucket storage for O(1) item lookup by ID
- Added dirty-flag and tick-level caches to eliminate redundant rebuilds of unified item list and external storage views
- Optimized GUI filtered items search from O(N×M) to O(N+M)
- Inventory lookup optimized from O(n) scan to O(1) HashMap
- All `EZInventory` consumers updated to use `getAllItems()` instead of direct field access

## Development

With vscode you need to run `gradlew eclipse` for the project to correctly recognize the class paths
