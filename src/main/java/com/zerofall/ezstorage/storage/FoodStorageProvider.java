package com.zerofall.ezstorage.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.dunk.tfc.api.Constant.Global;
import com.zerofall.ezstorage.configuration.EZConfiguration;
import com.zerofall.ezstorage.tileentity.TileEntityFoodStorage;

/**
 * Provider that exposes a food storage box's aggregate to the storage core's
 * unified item list. The single entry is the box's display stack (identity
 * template + marker NBT + baked average taste), with stackSize = total oz.
 *
 * Providers are wiped and re-created by the core on every multiblock scan, so
 * the tile entity reference is only held for one scan cycle (max 400 ticks).
 */
public class FoodStorageProvider implements IStorageProvider {

    private final TileEntityFoodStorage te;

    public FoodStorageProvider(TileEntityFoodStorage te) {
        this.te = te;
    }

    @Override
    public boolean isValid() {
        return !te.isInvalid() && te.getWorldObj() != null
            && te.getWorldObj()
                .getTileEntity(te.xCoord, te.yCoord, te.zCoord) == te;
    }

    @Override
    public ItemStack input(ItemStack itemStack) {
        return te.tryAbsorb(itemStack) ? null : itemStack;
    }

    @Override
    public ItemStack simulateInput(ItemStack itemStack) {
        return te.canAbsorb(itemStack) ? null : itemStack;
    }

    @Override
    public ItemStack extract(int index, int type) {
        if (index != 0) return null;
        // Defensive only: the terminal intercepts food clicks via
        // unifiedExtractOz and never calls this. Half click = 80oz, else 160oz.
        return te.extractPortion(type == 1 ? Global.FOOD_MAX_WEIGHT / 2 : Global.FOOD_MAX_WEIGHT);
    }

    @Override
    public ItemStack extractAll(int index) {
        if (index != 0) return null;
        // One legal max-portion stack; taking literally everything would
        // create an over-weight, illegal food stack.
        return te.extractPortion(Global.FOOD_MAX_WEIGHT);
    }

    @Override
    public ItemStack extractExact(int index, int amount) {
        if (index != 0 || amount <= 0) return null;
        // Amount is an item count on this interface; scale to oz so that
        // hoppers/pipes pulling through the unified path get the same 160oz
        // portions as hoppers touching the box directly.
        return te.extractPortion(EZConfiguration.hopperExtractOz * amount);
    }

    /** Extracts up to the given weight in oz; used by the core's unifiedExtractOz. */
    public ItemStack extractPortion(float oz) {
        return te.extractPortion(oz);
    }

    @Override
    public List<ItemStack> getAllItems() {
        List<ItemStack> items = new ArrayList<ItemStack>();
        ItemStack display = te.getDisplayStack();
        if (display != null) {
            items.add(display);
        }
        return items;
    }

    @Override
    public long getTotalCount() {
        // 1 while food is present, 0 when empty: feeds the core's 40-tick
        // change hash so the unified list refreshes when food appears or
        // rots away entirely.
        return te.getDisplayStack() != null ? 1 : 0;
    }

    @Override
    public long getCapacity() {
        // Capacity is measured in oz, not item count; do not pollute the
        // item-count display.
        return 0;
    }

    @Override
    public int getSlotCount() {
        return te.getDisplayStack() != null ? 1 : 0;
    }

    @Override
    public void markDirty() {
        te.onChangeLike();
    }
}
