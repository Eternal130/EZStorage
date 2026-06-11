package com.zerofall.ezstorage.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.dunk.tfc.Items.Tools.ItemTerraTool;
import com.dunk.tfc.Items.Tools.ItemWeapon;
import com.dunk.tfc.api.Enums.EnumSize;
import com.dunk.tfc.api.Interfaces.IFood;
import com.dunk.tfc.api.Interfaces.ISize;
import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.configuration.EZConfiguration;

public class EZInventory {

    private boolean hasChanges;

    private final Map<Item, List<ItemStack>> storageMap = new LinkedHashMap<>();
    private List<ItemStack> displayList = new ArrayList<>();
    private boolean displayListDirty = true;
    public List<ItemStack> inventory;

    public long maxItems = 0;
    private long totalCount = 0;
    private boolean totalCountDirty = true;
    public String id;
    public boolean disabled;
    public ItemStack[] craftMatrix;

    public EZInventory() {
        inventory = displayList;
    }

    private void ensureDisplayList() {
        if (!displayListDirty) {
            return;
        }
        displayList = new ArrayList<>();
        for (List<ItemStack> bucket : storageMap.values()) {
            for (ItemStack stack : bucket) {
                if (stack.stackSize > 0) {
                    displayList.add(stack);
                }
            }
        }
        displayListDirty = false;
        inventory = displayList;
    }

    private void markDisplayListDirty() {
        displayListDirty = true;
    }

    private ItemStack findInStorage(ItemStack itemStack) {
        List<ItemStack> bucket = storageMap.get(itemStack.getItem());
        if (bucket == null) {
            return null;
        }
        for (ItemStack group : bucket) {
            if (stacksEqual(group, itemStack)) {
                return group;
            }
        }
        return null;
    }

    public boolean getHasChanges() {
        return hasChanges;
    }

    public void setHasChanges() {
        hasChanges = true;
    }

    public void resetHasChanges() {
        hasChanges = false;
    }

    public ItemStack input(ItemStack itemStack) {
        if (!isItemAllowed(itemStack)) {
            return itemStack;
        }
        // Inventory is full
        long currentCount = getTotalCount();
        if (currentCount >= maxItems) {
            return itemStack;
        }
        long space = maxItems - currentCount;
        // Only part of the stack can fit
        int amount = (int) Math.min(space, (long) itemStack.stackSize);
        ItemStack stack = mergeStack(itemStack, amount);
        totalCountDirty = true;
        markDisplayListDirty();
        setHasChanges();
        return stack;
    }

    public ItemStack simulateInput(ItemStack itemStack) {
        if (!isItemAllowed(itemStack)) {
            return itemStack;
        }
        if (getTotalCount() >= maxItems) {
            return itemStack;
        }
        long space = maxItems - getTotalCount();
        int amount = (int) Math.min(space, (long) itemStack.stackSize);
        ItemStack remainder = itemStack.copy();
        remainder.stackSize -= amount;
        if (remainder.stackSize <= 0) {
            return null;
        }
        return remainder;
    }

    public void sort() {
        ensureDisplayList();
        Collections.sort(this.displayList, new ItemStackCountComparator());
        setHasChanges();
    }

    private ItemStack mergeStack(ItemStack itemStack, int amount) {
        ItemStack existing = findInStorage(itemStack);
        if (existing != null) {
            existing.stackSize += amount;
            totalCountDirty = true;
            setHasChanges();
        } else {
            if (EZConfiguration.maxItemTypes != 0 && slotCount() > EZConfiguration.maxItemTypes) {
                return itemStack;
            }
            ItemStack copy = itemStack.copy();
            copy.stackSize = amount;
            List<ItemStack> bucket = storageMap.get(copy.getItem());
            if (bucket == null) {
                bucket = new ArrayList<>();
                storageMap.put(copy.getItem(), bucket);
            }
            bucket.add(copy);
            totalCountDirty = true;
            setHasChanges();
        }

        itemStack.stackSize -= amount;
        if (itemStack.stackSize <= 0) {
            return null;
        } else {
            return itemStack;
        }
    }

    // Type: 0= full stack, 1= half stack, 2= single
    public ItemStack getItemsAt(int index, int type) {
        ensureDisplayList();
        if (index >= displayList.size()) {
            return null;
        }
        ItemStack group = displayList.get(index);
        ItemStack stack = group.copy();
        int size = Math.min(stack.getMaxStackSize(), group.stackSize);
        if (size > 1) {
            if (type == 1) {
                size = size / 2;
            } else if (type == 2) {
                size = 1;
            }
        }
        stack.stackSize = size;
        group.stackSize -= size;
        if (group.stackSize <= 0) {
            removeStackFromStorage(group);
        }
        totalCountDirty = true;
        markDisplayListDirty();
        setHasChanges();
        return stack;
    }

    public ItemStack getItemStackAt(int index, int size) {
        ensureDisplayList();
        if (index >= displayList.size()) {
            return null;
        }
        ItemStack group = displayList.get(index);
        ItemStack stack = group.copy();
        if (size > group.stackSize) {
            size = group.stackSize;
        }
        stack.stackSize = size;
        group.stackSize -= size;
        if (group.stackSize <= 0) {
            removeStackFromStorage(group);
        }
        totalCountDirty = true;
        markDisplayListDirty();
        setHasChanges();
        return stack;
    }

    public ItemStack simulateRemove(int index, int size) {
        ensureDisplayList();
        if (index >= displayList.size()) {
            return null;
        }
        ItemStack group = displayList.get(index);
        ItemStack stack = group.copy();
        if (size > group.stackSize) {
            size = group.stackSize;
        }
        stack.stackSize = size;
        return stack;
    }

    public List<ItemStack> getAllItems() {
        ensureDisplayList();
        return displayList;
    }

    public ItemStack getItems(ItemStack[] itemStacks) {
        for (ItemStack requested : itemStacks) {
            ItemStack group = findInStorage(requested);
            if (group != null && group.stackSize >= requested.stackSize) {
                ItemStack stack = group.copy();
                stack.stackSize = requested.stackSize;
                group.stackSize -= requested.stackSize;
                if (group.stackSize <= 0) {
                    removeStackFromStorage(group);
                }
                totalCountDirty = true;
                markDisplayListDirty();
                setHasChanges();
                return stack;
            }
        }
        return null;
    }

    public ItemStack extractAll(int index) {
        ensureDisplayList();
        if (index >= displayList.size()) {
            return null;
        }
        ItemStack group = displayList.get(index);
        ItemStack result = group.copy();
        removeStackFromStorage(group);
        totalCountDirty = true;
        markDisplayListDirty();
        setHasChanges();
        return result;
    }

    public ItemStack extractOne(int index) {
        return getItemsAt(index, 2);
    }

    public ItemStack extractStack(int index) {
        return getItemsAt(index, 0);
    }

    public int getIndexOf(ItemStack itemStack) {
        ensureDisplayList();
        for (int i = 0; i < displayList.size(); i++) {
            if (stacksEqual(displayList.get(i), itemStack)) {
                return i;
            }
        }
        return -1;
    }

    public int slotCount() {
        int count = 0;
        for (List<ItemStack> bucket : storageMap.values()) {
            count += bucket.size();
        }
        return count;
    }

    public static boolean stacksEqual(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null && stack2 == null) {
            return true;
        }
        if (stack1 == null || stack2 == null) {
            return false;
        }
        if (stack1.getItem() != stack2.getItem()) {
            return false;
        }
        if (stack1.getItemDamage() != stack2.getItemDamage()) {
            return false;
        }
        NBTTagCompound stack1Tag = stack1.getTagCompound();
        NBTTagCompound stack2Tag = stack2.getTagCompound();
        if (stack1Tag == null && stack2Tag == null) {
            return true;
        }
        if (stack1Tag == null || stack2Tag == null) {
            return false;
        }
        if (stack1Tag.equals(stack2Tag)) {
            return true;
        }
        return false;
    }

    public long getTotalCount() {
        if (!totalCountDirty) {
            return totalCount;
        }
        long count = 0;
        for (List<ItemStack> bucket : storageMap.values()) {
            for (ItemStack stack : bucket) {
                count += stack.stackSize;
            }
        }
        totalCount = count;
        totalCountDirty = false;
        return count;
    }

    @Override
    public String toString() {
        ensureDisplayList();
        return displayList.toString();
    }

    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList nbttaglist = new NBTTagList();
        for (List<ItemStack> bucket : storageMap.values()) {
            for (ItemStack group : bucket) {
                if (group != null && group.stackSize > 0) {
                    NBTTagCompound nbttagcompound1 = new NBTTagCompound();
                    group.writeToNBT(nbttagcompound1);
                    nbttagcompound1.setInteger("InternalCount", group.stackSize);
                    nbttaglist.appendTag(nbttagcompound1);
                }
            }
        }
        tag.setTag("Internal", nbttaglist);
        tag.setLong("InternalMax", this.maxItems);
        tag.setBoolean("isDisabled", this.disabled);

        if (this.craftMatrix != null) {
            NBTTagList gridList = new NBTTagList();
            for (int i = 0; i < 9; i++) {
                NBTTagCompound slotTag = new NBTTagCompound();
                slotTag.setByte("Slot", (byte) i);
                if (this.craftMatrix[i] != null) {
                    this.craftMatrix[i].writeToNBT(slotTag);
                }
                gridList.appendTag(slotTag);
            }
            tag.setTag("CraftMatrix", gridList);
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        NBTTagList nbttaglist = tag.getTagList("Internal", 10);

        if (nbttaglist != null) {
            storageMap.clear();
            for (int i = 0; i < nbttaglist.tagCount(); ++i) {
                NBTTagCompound nbttagcompound1 = nbttaglist.getCompoundTagAt(i);
                ItemStack stack = ItemStack.loadItemStackFromNBT(nbttagcompound1);
                if (stack == null) {
                    EZStorage.instance.LOG.warn("An ItemStack loaded from NBT was null.");
                    continue;
                }
                if (nbttagcompound1.hasKey("InternalCount", 3)) {
                    stack.stackSize = (int) nbttagcompound1.getInteger("InternalCount");
                } else if (nbttagcompound1.hasKey("InternalCount", 4)) {
                    stack.stackSize = (int) nbttagcompound1.getLong("InternalCount");
                }
                List<ItemStack> bucket = storageMap.get(stack.getItem());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    storageMap.put(stack.getItem(), bucket);
                }
                bucket.add(stack);
            }
            totalCountDirty = true;
            markDisplayListDirty();
        }
        this.maxItems = tag.getLong("InternalMax");
        this.disabled = tag.getBoolean("isDisabled");

        if (tag.hasKey("CraftMatrix", 9)) {
            NBTTagList gridList = tag.getTagList("CraftMatrix", 10);
            this.craftMatrix = new ItemStack[9];
            for (int i = 0; i < gridList.tagCount(); i++) {
                NBTTagCompound slotTag = gridList.getCompoundTagAt(i);
                byte slotIndex = slotTag.getByte("Slot");
                if (slotIndex >= 0 && slotIndex < 9) {
                    this.craftMatrix[slotIndex] = ItemStack.loadItemStackFromNBT(slotTag);
                }
            }
        }
    }

    private void removeStackFromStorage(ItemStack stack) {
        List<ItemStack> bucket = storageMap.get(stack.getItem());
        if (bucket != null) {
            for (int i = 0; i < bucket.size(); i++) {
                if (bucket.get(i) == stack) {
                    bucket.remove(i);
                    break;
                }
            }
            if (bucket.isEmpty()) {
                storageMap.remove(stack.getItem());
            }
        }
    }

    public void setItemsFromList(List<ItemStack> items) {
        storageMap.clear();
        for (ItemStack stack : items) {
            if (stack != null && stack.stackSize > 0) {
                List<ItemStack> bucket = storageMap.get(stack.getItem());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    storageMap.put(stack.getItem(), bucket);
                }
                bucket.add(stack);
            }
        }
        totalCountDirty = true;
        markDisplayListDirty();
    }

    private static final EnumSize MAX_ALLOWED_SIZE = EnumSize.LARGE;

    public static boolean isItemAllowed(ItemStack itemStack) {
        if (itemStack == null) return true;
        Item item = itemStack.getItem();

        if (item instanceof IFood) return false;

        if ((item instanceof ItemTool || item instanceof ItemTerraTool
            || item instanceof ItemWeapon
            || item instanceof ItemHoe) && item instanceof ISize
            && ((ISize) item).getSize(itemStack).stackSize < EnumSize.SMALL.stackSize) {
            return false;
        }

        if (item instanceof ISize) {
            EnumSize itemSize = ((ISize) item).getSize(itemStack);
            if (itemSize.stackSize > MAX_ALLOWED_SIZE.stackSize) {
                return false;
            }
        }

        return true;
    }
}
