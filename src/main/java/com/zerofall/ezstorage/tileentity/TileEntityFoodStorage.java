package com.zerofall.ezstorage.tileentity;

import java.util.Arrays;
import java.util.Objects;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.dunk.tfc.Core.TFC_Core;
import com.dunk.tfc.Core.TFC_Time;
import com.dunk.tfc.api.Food;
import com.dunk.tfc.api.Interfaces.IFood;

import com.zerofall.ezstorage.configuration.EZConfiguration;

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

    /** Volatile root-NBT keys stripped from the identity template. */
    private static final String[] VOLATILE_KEYS = { "foodWeight", "foodDecay", "decayTimer", "tasteSweetMod",
        "tasteSourMod", "tasteSaltyMod", "tasteBitterMod", "tasteUmamiMod", "tasteSweet", "tasteSour", "tasteSalty",
        "tasteBitter", "tasteUmami", "mealSkill" };

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

        return Food.isYeasty(a) == Food.isYeasty(b);
    }

    private static int[] orZero(int[] profile) {
        return profile != null ? profile : new int[] { 0, 0, 0, 0, 0 };
    }

    private static int[] orMinusOne(int[] groups) {
        return groups != null ? groups : new int[] { -1, -1, -1, -1, -1 };
    }

    /**
     * Absorbs a stack into the aggregate. Returns true only if the whole
     * stack was consumed; anything else must be spilled by the caller.
     */
    private boolean absorb(ItemStack is) {
        if (!isStorableFood(is)) return false;
        if (hasFoodContainer(is) && !EZConfiguration.allowHopperContainerFood) return false;

        float w = Food.getWeight(is);

        // Validate everything before committing any state, so a rejected
        // insert into an empty box does not leave the template locked.
        if (template != null && !keyMatches(template, is)) return false;

        int cap = EZConfiguration.foodStorageCapOz;
        if (cap > 0 && totalWeight + w > cap) return false;

        if (template == null) {
            template = stripVolatileKeys(is);
            decayTimer = Food.getDecayTimer(is);
        }

        int[] taste = Food.getFoodTasteProfile(is);
        for (int i = 0; i < 5; i++) {
            tasteSum[i] += w * taste[i];
        }
        totalWeight += w;
        totalDecay += Food.getDecay(is);
        decayTimer = Math.min(decayTimer, Food.getDecayTimer(is));

        onChange();
        return true;
    }

    /**
     * Extracts up to the requested weight in oz as a brand-new stack. In
     * "intact" mode only undecayed weight comes out and the rot stays behind;
     * in "proportional" mode the extracted portion carries its share of decay.
     */
    private ItemStack extractOz(float requestedOz) {
        if (template == null || totalWeight <= 0) return null;

        float maxPortion = Math.min(requestedOz, ((IFood) template.getItem()).getFoodMaxWeight(template));

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

        ItemStack out = template.copy();
        out.stackSize = 1;
        Food.setWeight(out, take);
        Food.setDecay(out, outDecay);
        Food.setDecayTimer(out, (int) TFC_Time.getTotalHours());

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
        if (worldObj != null) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
        }
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        // Hopper suction is synchronous within one hopper tick; anything not
        // reconciled by now is stale.
        restoreValid = false;
        if (template == null || totalWeight <= 0) return;

        ItemStack[] arr = new ItemStack[] { materialize() };
        TFC_Core.handleItemTicking(arr, worldObj, xCoord, yCoord, zCoord, 1.0f, 1.0f, false);
        if (arr[0] == null || arr[0].stackSize <= 0) {
            clearRot();
            return;
        }
        totalWeight = Food.getWeight(arr[0]);
        totalDecay = Food.getDecay(arr[0]);
        decayTimer = Food.getDecayTimer(arr[0]);
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
            return extractOz(EZConfiguration.hopperExtractOz * amount);
        }
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot == SLOT_INPUT) {
            if (stack != null && stack.stackSize > 0) {
                if (!absorb(stack)) {
                    spill(stack);
                }
            }
            return;
        }
        if (slot == SLOT_AGGREGATE && stack != null && stack.stackSize > 0 && restoreValid && preExtractTemplate != null
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
        if (hasFoodContainer(stack) && !EZConfiguration.allowHopperContainerFood) return false;
        if (template != null && !keyMatches(template, stack)) return false;
        int cap = EZConfiguration.foodStorageCapOz;
        if (cap > 0 && totalWeight + Food.getWeight(stack) > cap) return false;
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
        return template != null && (!hasFoodContainer(stack) || EZConfiguration.allowHopperContainerFood);
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
        EntityItem entity = new EntityItem(
            worldObj,
            xCoord + 0.5D,
            yCoord + 0.5D,
            zCoord + 0.5D,
            stack.copy());
        worldObj.spawnEntityInWorld(entity);
    }
}
