# Simple Storage

Simple storage mod for Minecraft 1.7.10 (Forge).

## Description

Simple Storage (former EZStorage) introduces an early-game storage system that scales and evolves as players progress, while keeping the vanilla flair. Want to put 100k Cobblestone in 1 slot? No problem. The blocks in the mod can add a crafting grid, additional storage, and more. Also includes integration into some mods for easier crafting or additional features!

> **This fork** adds external storage compatibility (via the Storage Adapter block), TFC+ (TerraFirmaCraft Plus) integration (crafting system, item restrictions, tier-appropriate recipes), and significant performance optimizations to adapt to the development flow and storage needs of TFC+.

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
- **Storage Adapter** *(Experimental)*
  - Connects adjacent external inventories (chests, machines, etc.) to the storage system
  - Items in connected inventories become visible and accessible from the Storage Core GUI, alongside internal storage
  - Supports double chests and sided inventories
  - External inventories **must be in loaded chunks** to function — if the target area is unloaded, external items will not be accessible
  - Must be enabled via `experimentalContent` config option
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
- TFC+ item restrictions applied to internal storage (e.g. food decay, tool damage)
- Recipes aligned with TFC+ progression (e.g. portable panel tier 3 uses black steel ingot instead of ender eye)
- Ore dictionary matching for upgrade recipes to use TFC+ materials

### External Storage

- Storage Adapter block connects adjacent external inventories to the storage system
- Unified I/O: items can be inserted into and extracted from external inventories via the Storage Core GUI
- Double chest support with proper item validation
- Chunk loading requirement: external inventories must be in loaded chunks

### Performance Optimizations

- Replaced `ArrayList` with `LinkedHashMap` bucket storage for O(1) item lookup by ID
- Added dirty-flag and tick-level caches to eliminate redundant rebuilds of unified item list and external storage views
- Optimized GUI filtered items search from O(N×M) to O(N+M)
- Inventory lookup optimized from O(n) scan to O(1) HashMap
- All `EZInventory` consumers updated to use `getAllItems()` instead of direct field access

## Development

With vscode you need to run `gradlew eclipse` for the project to correctly recognize the class paths
