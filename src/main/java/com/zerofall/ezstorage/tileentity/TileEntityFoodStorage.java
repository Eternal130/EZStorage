package com.zerofall.ezstorage.tileentity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dunk.tfc.Core.TFC_Core;
import com.dunk.tfc.Core.TFC_Time;
import com.dunk.tfc.Food.ItemSalad;
import com.dunk.tfc.Food.ItemSandwich;
import com.dunk.tfc.api.Food;
import com.dunk.tfc.api.Interfaces.IFood;
import com.dunk.tfc.api.TFCItems;
import com.dunk.tfc.api.TFCOptions;
import com.dunk.tfc.api.Tools.IKnife;
import com.zerofall.ezstorage.Reference;
import com.zerofall.ezstorage.configuration.EZConfiguration;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Multiblock member that stores TFC food aggregated by weight in ounces.
 *
 * The box holds a single "kind" of food at a time, tracked by an identity
 * template (a real stack copy with all volatile keys stripped) plus aggregate
 * values: totalWeight (oz), totalDecay (oz, may be negative for fresh food),
 * a shared decayTimer (TFC total-hours clock, minimum across absorbed stacks)
 * and tasteSum, a weighted sum of (weight x taste_i) over all inserted stacks.
 *
 * Rot is applied by TFC's own tick code: every tick the aggregate is
 * materialized into a single virtual stack, run through
 * {@link TFC_Core#handleItemTicking(ItemStack[], World, int, int, int, float, float, boolean)}
 * with chest-equivalent parameters, and the resulting weight/decay/timer are
 * read back into the aggregate. Taste is not part of the identity key and is
 * only carried as a weighted sum; it gets displayed in a later phase.
 */
public class TileEntityFoodStorage extends TileEntityMultiblock implements IInventory, ISidedInventory {

    /** Slot index of the input mouth. */
    public static final int SLOT_INPUT = 0;
    /** Slot index of the aggregate view slot. */
    public static final int SLOT_AGGREGATE = 1;

    /** NBT marker identifying a food aggregate display stack. */
    public static final String NBT_MARKER = "ezFoodAggregate";
    /** NBT key on display stacks holding the total decay in oz for tooltips. */
    public static final String NBT_DECAY = "ezFoodDecay";

    /** Display-stack NBT key carrying the exact aggregate weight (float oz). */
    public static final String NBT_WEIGHT = "ezFoodWeight";

    /** Volatile root-NBT keys stripped from the identity template. */
    private static final String[] VOLATILE_KEYS = { "foodWeight", "foodDecay", "decayTimer", "tasteSweetMod",
        "tasteSourMod", "tasteSaltyMod", "tasteBitterMod", "tasteUmamiMod", "tasteSweet", "tasteSour", "tasteSalty",
        "tasteBitter", "tasteUmami", "mealSkill", "temperature" };

    /** Identity: real stack copy with volatile keys stripped. */
    private ItemStack template;
    /** Total stored weight in oz. */
    private float totalWeight;
    /** Total absolute decay in oz, may be negative (freshness). */
    private float totalDecay;
    /** Sum of (weight x taste_i) over all absorbed stacks. */
    private final float[] tasteSum = new float[5];
    /** TFC total-hours decay clock. */
    private int decayTimer;
    /** Aggregate weight shown by the last aggregate-slot read (server only). */
    private float lastViewWeight = -1.0f;
    /**
     * Pre-extraction snapshot. Vanilla hopper suction reads the aggregate slot,
     * extracts one portion via decrStackSize, and if the hopper cannot absorb
     * the portion it writes the previously-read stack back into the same slot.
     * For a normal inventory that write-back undoes the extraction; for this
     * virtual slot we must restore the pre-extraction aggregate ourselves or
     * the deducted portion is voided. The snapshot (including the template,
     * because extraction may empty and clear the box) is only honored when the
     * write-back matches what was read, and expires at the next tile tick.
     */
    private ItemStack preExtractTemplate;
    private float preExtractWeight, preExtractDecay;
    private int preExtractTimer;
    private final float[] preExtractTaste = new float[5];
    private boolean restoreValid = false;
    /**
     * Container consumed by the immediately preceding extractOz, refunded
     * when a hopper write-back undoes that extraction (the food portion never
     * left, so the container paid to system storage must go back). Cleared
     * before every extraction attempt and at every tile tick.
     */
    private ItemStack lastPaidContainer;

    /** Cached "system storage has a knife" answer, refreshed every 40 ticks. */
    private boolean knifeCached;
    private int knifeCheckTimer;

    /** Lazily parsed config: items that can never be knife-split. */
    private static Set<Item> noSplitItems;
    /** Lazily parsed config: per-item extract portion cap overrides in oz. */
    private static Map<Item, Integer> extractCaps;

    /**
     * Checks whether the given stack is a food aggregate display stack
     * (carries the {@link #NBT_MARKER} tag).
     */
    public static boolean isFoodAggregate(ItemStack is) {
        return is != null && is.getTagCompound() != null
            && is.getTagCompound()
                .hasKey(NBT_MARKER);
    }

    /**
     * Stable identity key for a food aggregate display stack, independent of
     * the volatile values baked into its NBT (exact weight, decay, taste
     * average — those change on every insert/extract/rot tick). Two display
     * stacks of the same food kind must map to the same key so the GUI's
     * incremental diff can update the existing row in place instead of
     * orphaning it as a 0-count tombstone and appending a new row. The key
     * hashes the identity template: all NBT except the volatile keys and the
     * display-only marker keys.
     */
    public static String aggregateKey(ItemStack is) {
        if (!isFoodAggregate(is)) return null;
        NBTTagCompound tag = is.getTagCompound();
        // Work on a COPY: stripping keys must never mutate the live stack.
        NBTTagCompound identity = (NBTTagCompound) tag.copy();
        for (String key : VOLATILE_KEYS) {
            identity.removeTag(key);
        }
        identity.removeTag(NBT_MARKER);
        identity.removeTag(NBT_DECAY);
        identity.removeTag(NBT_WEIGHT);
        NBTTagCompound procTag = identity.getCompoundTag(Food.PROCESSING_TAG);
        if (identity.hasKey(Food.PROCESSING_TAG)) {
            procTag.removeTag(Food.SMOKE_COUNTER_TAG);
        }
        return Item.getIdFromItem(is.getItem()) + ":" + is.getItemDamage() + ":" + identity.hashCode();
    }

    /**
     * Checks whether the given stack is food this box can store: a single item
     * implementing TFC's IFood with a positive weight.
     */
    public static boolean isStorableFood(ItemStack is) {
        return is != null && is.stackSize == 1 && is.getItem() instanceof IFood && Food.getWeight(is) > 0;
    }

    /**
     * Checks whether the given stack carries a container item (bowl, knife
     * byproduct, ...) or is a salad-style bowl food.
     */
    public static boolean hasFoodContainer(ItemStack is) {
        return is.getItem()
            .getContainerItem(is) != null
            || (is.getTagCompound() != null && is.getTagCompound()
                .hasKey("bowlMeta"));
    }

    /**
     * The container stack that must be surrendered for one unit of the given
     * food: its vanilla container item (copied, size 1), or the ceramic bowl
     * recorded in {@code bowlMeta} for salads. Null for plain food.
     */
    public static ItemStack getNeededContainer(ItemStack templateLike) {
        ItemStack cc = templateLike.getItem()
            .getContainerItem(templateLike);
        if (cc != null) {
            cc = cc.copy();
            cc.stackSize = 1;
            return cc;
        }
        NBTTagCompound tag = templateLike.getTagCompound();
        if (tag != null && tag.hasKey("bowlMeta")) {
            return new ItemStack(TFCItems.potteryBowl, 1, tag.getInteger("bowlMeta"));
        }
        return null;
    }

    /** Deposits a stripped container into system storage; pops it into the world when there is no space. */
    private void depositContainer(ItemStack container) {
        ItemStack remainder = container;
        TileEntityStorageCore core = getCore();
        if (core != null) {
            remainder = core.unifiedInput(container);
        }
        if (remainder != null) {
            spill(remainder);
        }
    }

    /**
     * True when one matching container is available in system storage for the
     * current template; consumes it when {@code consume} is set.
     */
    private boolean payContainerFromSystem(boolean consume) {
        ItemStack needed = getNeededContainer(template);
        if (needed == null) return true;
        TileEntityStorageCore core = getCore();
        if (core == null) return false;
        return consume ? core.unifiedConsumeContainer(needed) : core.unifiedHasContainer(needed);
    }

    /**
     * Display name of the container currently blocking extraction, or null if
     * nothing is blocking. Used for the terminal's action-bar hint.
     */
    public String getMissingContainerName() {
        if (template == null) return null;
        ItemStack needed = getNeededContainer(template);
        if (needed == null) return null;
        TileEntityStorageCore core = getCore();
        if (core != null && core.unifiedHasContainer(needed)) return null;
        return needed.getDisplayName();
    }

    /**
     * Copies the stack and removes all volatile food keys from the root NBT so
     * the copy can serve as a stable identity template.
     */
    public static ItemStack stripVolatileKeys(ItemStack is) {
        ItemStack copy = is.copy();
        NBTTagCompound tag = copy.getTagCompound();
        if (tag != null) {
            for (String key : VOLATILE_KEYS) {
                tag.removeTag(key);
            }
            // Smoke progress (in the "Processing Tag" sub-compound) is reset on
            // insert (user decision): partially smoked food stores fine, but
            // extracts always come out unsmoked — smoking restarts from zero
            // on the rack. Completed smokes are unaffected (their effect lives
            // in FuelProfile, an equality key).
            NBTTagCompound procTag = tag.getCompoundTag(Food.PROCESSING_TAG);
            if (tag.hasKey(Food.PROCESSING_TAG)) {
                procTag.removeTag(Food.SMOKE_COUNTER_TAG);
            }
        }
        return copy;
    }

    /**
     * The food identity key: item + damage + all processing/treatment flags.
     * Taste is deliberately not compared, and cooked time only roughly (same
     * 120-tick cook bucket) so visually identical foods merge.
     */
    public static boolean keyMatches(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem() || a.getItemDamage() != b.getItemDamage()) return false;

        if (Food.isBrined(a) != Food.isBrined(b)) return false;
        if (Food.isPickled(a) != Food.isPickled(b)) return false;
        if (Food.isSalted(a) != Food.isSalted(b)) return false;
        if (Food.isCooked(a) != Food.isCooked(b)) return false;
        if (Food.isDried(a) != Food.isDried(b)) return false;

        if (!Arrays.equals(orZero(Food.getCookedProfile(a)), orZero(Food.getCookedProfile(b)))) return false;
        if (!Arrays.equals(orZero(Food.getFuelProfile(a)), orZero(Food.getFuelProfile(b)))) return false;

        if (((int) Food.getCooked(a) - 600) / 120 != ((int) Food.getCooked(b) - 600) / 120) return false;

        if (!Arrays.equals(orMinusOne(Food.getFoodGroups(a)), orMinusOne(Food.getFoodGroups(b)))) return false;

        if (!Objects.equals(Food.getInfusion(a), Food.getInfusion(b))) return false;

        if (Food.isYeasty(a) != Food.isYeasty(b)) return false;

        // Bowl type must match: extraction refunds the bowl recorded in
        // bowlMeta, so different bowl variants must never merge.
        if (nbtInt(a, "bowlMeta") != nbtInt(b, "bowlMeta")) return false;

        return true;
    }

    private static int[] orZero(int[] profile) {
        return profile != null ? profile : new int[] { 0, 0, 0, 0, 0 };
    }

    private static int[] orMinusOne(int[] groups) {
        return groups != null ? groups : new int[] { -1, -1, -1, -1, -1 };
    }

    /**
     * Absorbs a stack into the aggregate. Returns null when fully absorbed
     * (or rotted away in catch-up), otherwise the leftover stack which the
     * caller must return or spill — food is never voided. When over cap and
     * a knife is available in system storage, absorbs the fitting part and
     * returns the rest as a real leftover stack.
     *
     * @param automation true for hopper/pipe insertion (container foods
     *                   gated by allowHopperContainerFood), false for the
     *                   terminal path (container food always allowed).
     */
    private ItemStack absorb(ItemStack is, boolean automation) {
        if (!isStorableFood(is)) return is;
        // Temperature is stripped on insert (VOLATILE_KEYS): hot food may be
        // stored but cools instantly to ambient — the box never carries the
        // temperature key, so no mid-aggregate cooking/cooling happens in the
        // tick's virtual stack and extracts always come out unheated.

        // Container gate: terminal always allows container food (the container
        // is stripped into system storage below); hoppers/pipes only when
        // configured.
        ItemStack container = getNeededContainer(is);
        if (container != null && automation && !EZConfiguration.allowHopperContainerFood) return is;

        if (template != null && !keyMatches(template, is)) return is;

        // Per-stack decay normalization: advance the incoming stack's private
        // decay clock to "now" BEFORE merging. Without this, a stack carrying
        // a stale decayTimer (e.g. kept in an unloaded chunk) drags the
        // aggregate's clock backward via the min() below, and the catch-up
        // decay for its whole elapsed backlog then applies to the ENTIRE
        // merged mass — proportionally amplifying the aggregate's decay ratio
        // 10-25x and "rotting" freshly stored food almost instantly. Catching
        // the stack up alone first restores vanilla per-stack semantics: each
        // stack pays only its own backlog (TFC's decayProtection clamps huge
        // gaps to 24h, so the loop converges in a bounded number of hours).
        ItemStack normalized = is.copy();
        if (worldObj != null) {
            int now = (int) TFC_Time.getTotalHours();
            // Worst case one iteration per hour of gap; any gap beyond
            // decayProtectionDays is clamped to 24h by tickDecay itself, so
            // protection-days*24 + slack bounds the loop for any config.
            int guard = TFCOptions.decayProtectionDays * 24 + 48;
            while (Food.getDecayTimer(normalized) < now && guard-- > 0) {
                normalized = TFC_Core.tickDecay(normalized, worldObj, xCoord, yCoord, zCoord, 1.0f, 1.0f);
                if (normalized == null || normalized.stackSize <= 0) return is; // rotted away on catch-up
            }
        }

        float w = Food.getWeight(normalized);

        int cap = EZConfiguration.foodStorageCapOz;
        float toAbsorb = w;
        if (cap > 0 && totalWeight + w > cap) {
            float space = cap - totalWeight;
            if (space >= 1.0f && !isNoSplit(is) && hasKnifeCached()) {
                toAbsorb = space; // knife split: absorb what fits
            } else {
                return is; // over cap, no split possible
            }
        }

        float scale = toAbsorb / w;

        if (template == null) {
            template = stripVolatileKeys(is);
            decayTimer = Food.getDecayTimer(normalized);
        }

        int[] taste = Food.getFoodTasteProfile(is);
        for (int i = 0; i < 5; i++) {
            tasteSum[i] += toAbsorb * taste[i];
        }
        totalWeight += toAbsorb;
        totalDecay += Food.getDecay(normalized) * scale;
        decayTimer = Math.min(decayTimer, Food.getDecayTimer(normalized));

        if (container != null) {
            depositContainer(container);
        }

        if (toAbsorb < w) {
            // Knife-split leftover: return the rest as a real stack so the
            // caller can hand it back / spill it — never void food.
            // Decay BEFORE weight: the copy still carries the full decay d,
            // and setWeight(lo, w - toAbsorb) would destroy the stack when
            // d > w - toAbsorb (heavily decayed stack, small space). The
            // scaled share d*(1-scale) is below both the remaining weight
            // and the current full weight, so both setters are safe.
            ItemStack leftover = normalized.copy();
            Food.setDecay(leftover, Food.getDecay(normalized) * (1 - scale));
            Food.setWeight(leftover, w - toAbsorb);
            onChange();
            return leftover;
        }

        onChange();
        return null;
    }

    /**
     * Terminal-path absorb: container foods allowed, leftovers handed back
     * to the caller (provider.input returns them to the pipeline).
     */
    public ItemStack absorbTerminal(ItemStack is) {
        return absorb(is, false);
    }

    /**
     * Extracts up to the requested weight in oz as a brand-new stack. In
     * "intact" mode only undecayed weight comes out and the rot stays behind;
     * in "proportional" mode the extracted portion carries its share of decay.
     *
     * @param payContainer true to consume one container from system storage
     *                     when the food needs one (direct single-box paths);
     *                     false for the unified multi-box path, which pays
     *                     exactly ONE container for the merged result stack
     *                     instead of one per contributing box.
     */
    private ItemStack extractOz(float requestedOz, boolean payContainer) {
        if (template == null || totalWeight <= 0) return null;

        float maxPortion = Math.min(requestedOz, getExtractCapOz(template));

        boolean proportional = "proportional".equals(EZConfiguration.decayExtractMode);
        float take;
        float outDecay;
        if (proportional) {
            take = Math.min(maxPortion, totalWeight);
            outDecay = (totalDecay / totalWeight) * take;
        } else {
            // Intact mode: only undecayed weight leaves the box. Negative decay
            // (TFC freshness protection) must NOT inflate the extractable amount
            // above the stored weight.
            float intactWeight = Math.max(totalWeight - Math.max(totalDecay, 0.0f), 0.0f);
            take = Math.min(maxPortion, intactWeight);
            outDecay = 0;
        }

        if (take <= 0) return null;

        // Container food: every extracted stack must pay one container from
        // system storage (stripped on insert). Blocked when none is available
        // — the terminal surfaces a named hint in that case.
        if (payContainer) {
            if (!payContainerFromSystem(true)) return null;
            lastPaidContainer = getNeededContainer(template); // refunded if a hopper write-back undoes this extraction
        }

        ItemStack out = template.copy();
        out.stackSize = 1;
        Food.setWeight(out, take);
        Food.setDecay(out, outDecay);
        Food.setDecayTimer(out, (int) TFC_Time.getTotalHours());
        // Rewrite taste to the weighted average (design 2.5): must run before
        // the aggregate totals below are mutated.
        bakeAverageTaste(out);

        float ratio = totalWeight > 0 ? (totalWeight - take) / totalWeight : 0;
        totalWeight -= take;
        for (int i = 0; i < 5; i++) {
            tasteSum[i] *= ratio;
        }
        if (proportional) {
            totalDecay -= outDecay;
        }

        if (totalWeight <= 0.01f) clearRot();

        onChange();
        return out;
    }

    /** Public absorb for the storage provider; true iff fully absorbed. */
    public boolean tryAbsorb(ItemStack is) {
        return absorbTerminal(is) == null;
    }

    /** Non-mutating absorb check (mirrors the absorb gate). */
    public boolean canAbsorb(ItemStack is) {
        return isItemValidForSlot(SLOT_INPUT, is);
    }

    /** Public weight-based extraction for the storage provider and terminal. */
    public ItemStack extractPortion(float oz) {
        if (template == null) return null;
        return extractOz(oz, true);
    }

    /**
     * Weight-based extraction WITHOUT paying a container, for the core's
     * multi-box merge path: the merged result stack pays exactly one
     * container from system storage (see
     * {@code TileEntityStorageCore#unifiedConsumeContainer}), not one per box.
     */
    public ItemStack extractPortionNoPay(float oz) {
        if (template == null) return null;
        return extractOz(oz, false);
    }

    /** Public change notification for the storage provider. */
    public void onChangeLike() {
        onChange();
    }

    /**
     * Builds the terminal display stack: identity template + aggregate
     * marker + baked weighted-average taste mods, with stackSize = total oz.
     * Taste is baked as mod = round(avg - probe) where probe is the value
     * read from the template (base + cook/smoke profiles, since all taste
     * keys were stripped), so the vanilla getter chain displays the exact
     * weighted average for both plain foods and meals.
     */
    public ItemStack getDisplayStack() {
        if (template == null || totalWeight <= 0) return null;

        ItemStack display = template.copy();
        // Header/badge semantics: stackSize counts PORTIONS (how many single
        // extracts the box holds), not raw oz — the terminal's top-right total
        // then reads items + food portions instead of mixing ounces into the
        // item count. The exact weight rides in NBT_WEIGHT (tooltip/badge).
        display.stackSize = Math.max(1, Math.round(totalWeight / Math.max(getExtractCapOz(template), 1.0f)));

        NBTTagCompound tag = display.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            display.setTagCompound(tag);
        }
        tag.setBoolean(NBT_MARKER, true);
        tag.setInteger(NBT_DECAY, Math.round(totalDecay));

        // Bake weight/decay so TFC's FoodItemRenderer (registered per-item,
        // INVENTORY type) draws its two overlay bars on this stack in GUIs:
        // white weight bar = one max portion's fill (clamped so the ratio
        // stays in [0,1]; TFC skips bars outside that range), decay bar =
        // the aggregate decay ratio (fresh/negative clamped to zero = full
        // green bar). The exact total for tooltips rides in NBT_WEIGHT.
        float maxPortion = ((IFood) template.getItem()).getFoodMaxWeight(template);
        float barWeight = Math.min(totalWeight, maxPortion);
        Food.setWeight(display, barWeight);
        float decayRatio = Math.max(totalDecay / totalWeight, 0.0f);
        Food.setDecay(display, decayRatio * barWeight);
        tag.setFloat(NBT_WEIGHT, totalWeight);

        bakeAverageTaste(display);

        return display;
    }

    /**
     * Bakes the weighted-average taste into the given stack as taste-mod keys.
     * The probe reads the template through the IFood getters (base + cook/smoke
     * profiles, since all taste keys were stripped from the template), so the
     * vanilla getter chain afterwards displays exactly the stored weighted
     * average — for both plain foods and meals. Uses the CURRENT aggregate
     * totals; callers must invoke before mutating them.
     */
    private void bakeAverageTaste(ItemStack stack) {
        if (template == null || totalWeight <= 0) return;

        IFood food = (IFood) template.getItem();
        int[] probe = { food.getTasteSweet(template), food.getTasteSour(template), food.getTasteSalty(template),
            food.getTasteBitter(template), food.getTasteSavory(template) };
        Food.setSweetMod(stack, Math.round(tasteSum[0] / totalWeight - probe[0]));
        Food.setSourMod(stack, Math.round(tasteSum[1] / totalWeight - probe[1]));
        Food.setSaltyMod(stack, Math.round(tasteSum[2] / totalWeight - probe[2]));
        Food.setBitterMod(stack, Math.round(tasteSum[3] / totalWeight - probe[3]));
        Food.setSavoryMod(stack, Math.round(tasteSum[4] / totalWeight - probe[4]));
    }

    /** Materializes the aggregate into a single virtual food stack. */
    private ItemStack materialize() {
        ItemStack s = template.copy();
        s.stackSize = 1;
        Food.setWeight(s, totalWeight);
        Food.setDecay(s, totalDecay);
        Food.setDecayTimer(s, decayTimer);
        return s;
    }

    /** Wipes all state once the food has fully rotted away. */
    private void clearRot() {
        template = null;
        totalWeight = 0;
        totalDecay = 0;
        for (int i = 0; i < tasteSum.length; i++) {
            tasteSum[i] = 0;
        }
        decayTimer = (int) TFC_Time.getTotalHours();
        onChange();
    }

    private void onChange() {
        // Invalidate the core's cached unified list: every aggregate mutation
        // (absorb/extract/rot tick/clear) changes the baked display stack, and
        // callers that bypass the core's unified ops (crafting-grid refill via
        // provider.extract, direct hopper I/O) would otherwise ship a stale
        // list to clients — wrong badge weights, wrong header counts, and a
        // terminal row disagreeing with the real stack handed out.
        TileEntityStorageCore core = getCore();
        if (core != null) {
            core.markUnifiedListDirty();
        }
        if (worldObj != null) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
        }
    }

    // ////////////////////////////////////////////////////////////
    // Knife presence cache (refreshed every 40 server ticks)
    // ////////////////////////////////////////////////////////////

    private boolean hasKnifeCached() {
        return knifeCached;
    }

    /** True if any item in the given list is a TFC knife. */
    private static boolean scanInventoryForKnife(List<ItemStack> items) {
        for (ItemStack is : items) {
            if (is != null && is.getItem() instanceof IKnife) return true;
        }
        return false;
    }

    private void refreshKnifeCache() {
        TileEntityStorageCore core = getCore();
        knifeCached = core != null && scanInventoryForKnife(core.getUnifiedItemList());
    }

    // ////////////////////////////////////////////////////////////
    // Config-backed no-split blacklist & extract cap overrides (lazy parse)
    // ////////////////////////////////////////////////////////////

    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

    /** True when the item can never be knife-split past the cap. */
    private static boolean isNoSplit(ItemStack is) {
        if (is.getItem() instanceof ItemSandwich || is.getItem() instanceof ItemSalad) return true;
        return parsedNoSplitItems().contains(is.getItem());
    }

    private static Set<Item> parsedNoSplitItems() {
        synchronized (TileEntityFoodStorage.class) {
            if (noSplitItems == null) {
                noSplitItems = new HashSet<>();
                for (String entry : EZConfiguration.foodNoSplitBlacklist) {
                    String id = entry.trim();
                    if (id.isEmpty()) continue;
                    Item item = GameRegistry.findItem(splitModId(id), splitName(id));
                    if (item != null) {
                        noSplitItems.add(item);
                    } else {
                        LOGGER.warn("Unknown item in foodNoSplitBlacklist, ignoring: {}", id);
                    }
                }
            }
            return noSplitItems;
        }
    }

    /**
     * Extract portion cap in oz for the given food: the config override if
     * present, else the food's own max weight.
     */
    public static float getExtractCapOz(ItemStack templateLike) {
        Integer override = parsedExtractCaps().get(templateLike.getItem());
        if (override != null) return override;
        return ((IFood) templateLike.getItem()).getFoodMaxWeight(templateLike);
    }

    private static Map<Item, Integer> parsedExtractCaps() {
        synchronized (TileEntityFoodStorage.class) {
            if (extractCaps == null) {
                extractCaps = new HashMap<>();
                for (String entry : EZConfiguration.foodExtractCapOverrides) {
                    String id = entry.trim();
                    if (id.isEmpty()) continue;
                    int eq = id.lastIndexOf('=');
                    if (eq <= 0 || eq == id.length() - 1) {
                        LOGGER.warn("Malformed foodExtractCapOverrides entry, ignoring: {}", id);
                        continue;
                    }
                    Integer oz;
                    try {
                        oz = Integer.parseInt(
                            id.substring(eq + 1)
                                .trim());
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Invalid oz value in foodExtractCapOverrides, ignoring: {}", id);
                        continue;
                    }
                    Item item = GameRegistry.findItem(splitModId(id.substring(0, eq)), splitName(id.substring(0, eq)));
                    if (item != null) {
                        extractCaps.put(item, oz);
                    } else {
                        LOGGER.warn("Unknown item in foodExtractCapOverrides, ignoring: {}", id);
                    }
                }
            }
            return extractCaps;
        }
    }

    private static String splitModId(String modidName) {
        int colon = modidName.indexOf(':');
        return colon > 0 ? modidName.substring(0, colon) : "minecraft";
    }

    private static String splitName(String modidName) {
        int colon = modidName.indexOf(':');
        return colon > 0 ? modidName.substring(colon + 1) : modidName;
    }

    private static int nbtInt(ItemStack is, String key) {
        NBTTagCompound t = is.getTagCompound();
        return t != null && t.hasKey(key) ? t.getInteger(key) : -1;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        // Hopper suction is synchronous within one hopper tick; anything not
        // reconciled by now is stale.
        restoreValid = false;
        lastPaidContainer = null;
        // Refresh the "system storage has a knife" cache every 40 ticks even
        // when the box is empty (knife-splitting matters most on insert into
        // an empty/near-full box).
        if (--knifeCheckTimer <= 0) {
            knifeCheckTimer = 40;
            refreshKnifeCache();
        }
        if (template == null || totalWeight <= 0) return;

        ItemStack[] arr = new ItemStack[] { materialize() };
        float w0 = totalWeight;
        TFC_Core.handleItemTicking(arr, worldObj, xCoord, yCoord, zCoord, 1.0f, 1.0f, false);
        if (arr[0] == null || arr[0].stackSize <= 0) {
            clearRot();
            return;
        }
        totalWeight = Food.getWeight(arr[0]);
        totalDecay = Food.getDecay(arr[0]);
        decayTimer = Food.getDecayTimer(arr[0]);
        // Keep the taste average invariant under decay: tasteSum is a weighted
        // sum, so shrink it by the same ratio the weight shrank.
        if (w0 > 0 && totalWeight > 0 && totalWeight != w0) {
            float tasteRatio = totalWeight / w0;
            for (int i = 0; i < tasteSum.length; i++) {
                tasteSum[i] *= tasteRatio;
            }
        }
    }

    // ////////////////////////////////////////////////////////////
    // IInventory - two virtual slots: 0 = input mouth, 1 = aggregate view
    // ////////////////////////////////////////////////////////////

    @Override
    public int getSizeInventory() {
        return 2;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == SLOT_AGGREGATE && template != null && totalWeight > 0) {
            ItemStack view = materialize();
            if (worldObj != null && !worldObj.isRemote) lastViewWeight = totalWeight;
            return view;
        }
        return null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot == SLOT_AGGREGATE) {
            // The container paid by the extraction this hopper is about to
            // attempt is only refundable for THIS extraction attempt.
            lastPaidContainer = null;
            if (worldObj != null && !worldObj.isRemote && template != null && totalWeight > 0) {
                preExtractTemplate = template;
                preExtractWeight = totalWeight;
                preExtractDecay = totalDecay;
                preExtractTimer = decayTimer;
                System.arraycopy(tasteSum, 0, preExtractTaste, 0, tasteSum.length);
                // Only reconcilable if the caller read the current state (vanilla
                // hopper always reads immediately before extracting).
                restoreValid = lastViewWeight > 0 && Math.abs(lastViewWeight - totalWeight) < 0.5f;
            } else {
                restoreValid = false;
            }
            lastViewWeight = -1.0f;
            return extractOz(EZConfiguration.hopperExtractOz * amount, true);
        }
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot == SLOT_INPUT) {
            if (stack != null && stack.stackSize > 0) {
                ItemStack left = absorb(stack, true);
                if (left != null) {
                    spill(left);
                }
            }
            return;
        }
        if (slot == SLOT_AGGREGATE && stack != null
            && stack.stackSize > 0
            && restoreValid
            && preExtractTemplate != null
            && keyMatches(preExtractTemplate, stack)
            && Math.abs(Food.getWeight(stack) - preExtractWeight) < 0.5f) {
            // Vanilla hopper write-back of the view it read: the portion it
            // pulled via decrStackSize never made it into the hopper, so put
            // the aggregate back to its pre-extraction state. The snapshot
            // template is used because extraction may have emptied the box.
            template = preExtractTemplate;
            totalWeight = preExtractWeight;
            totalDecay = preExtractDecay;
            decayTimer = preExtractTimer;
            System.arraycopy(preExtractTaste, 0, tasteSum, 0, tasteSum.length);
            restoreValid = false;
            // The extraction consumed a container from system storage; the
            // write-back means the food portion never left, so the container
            // must go back — re-input it, spilling only if storage filled
            // meanwhile (the correct no-voiding fallback).
            if (lastPaidContainer != null) {
                depositContainer(lastPaidContainer);
                lastPaidContainer = null;
            }
            onChange();
        }
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public String getInventoryName() {
        return "food_storage";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && !isInvalid()
            && player.getDistanceSq((double) xCoord + 0.5D, (double) yCoord + 0.5D, (double) zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot != SLOT_INPUT || stack == null) return false;
        if (!isStorableFood(stack)) return false;
        // Automation semantics (this gate backs canInsertItem/hoppers).
        ItemStack container = getNeededContainer(stack);
        if (container != null && !EZConfiguration.allowHopperContainerFood) return false;
        if (template != null && !keyMatches(template, stack)) return false;
        int cap = EZConfiguration.foodStorageCapOz;
        if (cap > 0 && totalWeight + Food.getWeight(stack) > cap) {
            // Over cap is only acceptable when a knife split could absorb
            // the fitting part.
            float space = cap - totalWeight;
            if (!(space >= 1.0f && !isNoSplit(stack) && hasKnifeCached())) return false;
        }
        return true;
    }

    // ////////////////////////////////////////////////////////////
    // ISidedInventory
    // ////////////////////////////////////////////////////////////

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return new int[] { SLOT_INPUT, SLOT_AGGREGATE };
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot == SLOT_INPUT && isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        if (slot != SLOT_AGGREGATE || stack == null) return false;
        if (template == null) return false;
        if (!hasFoodContainer(stack)) return true;
        if (!EZConfiguration.allowHopperContainerFood) return false;
        // Container food: also require a payable container in system storage.
        return payContainerFromSystem(false);
    }

    // ////////////////////////////////////////////////////////////
    // NBT persistence
    // ////////////////////////////////////////////////////////////

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (template != null) {
            NBTTagCompound templateTag = new NBTTagCompound();
            template.writeToNBT(templateTag);
            tag.setTag("FoodTemplate", templateTag);
            tag.setFloat("TotalWeight", totalWeight);
            tag.setFloat("TotalDecay", totalDecay);
            tag.setInteger("DecayTimer", decayTimer);
            NBTTagList tasteList = new NBTTagList();
            for (int i = 0; i < tasteSum.length; i++) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setFloat("Taste", tasteSum[i]);
                tasteList.appendTag(entry);
            }
            tag.setTag("TasteSum", tasteList);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("FoodTemplate")) {
            template = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("FoodTemplate"));
            totalWeight = tag.getFloat("TotalWeight");
            totalDecay = tag.getFloat("TotalDecay");
            decayTimer = tag.getInteger("DecayTimer");
            if (tag.hasKey("TasteSum")) {
                NBTTagList tasteList = tag.getTagList("TasteSum", 10);
                for (int i = 0; i < tasteSum.length && i < tasteList.tagCount(); i++) {
                    tasteSum[i] = tasteList.getCompoundTagAt(i)
                        .getFloat("Taste");
                }
            }
        }
    }

    /** Spills a rejected stack into the world as an EntityItem; never voids it. */
    private void spill(ItemStack stack) {
        if (worldObj == null || stack == null || stack.stackSize <= 0) return;
        if (worldObj.isRemote) return;
        EntityItem entity = new EntityItem(worldObj, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, stack.copy());
        worldObj.spawnEntityInWorld(entity);
    }
}
