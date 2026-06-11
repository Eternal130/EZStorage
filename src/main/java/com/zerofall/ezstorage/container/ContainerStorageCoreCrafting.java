package com.zerofall.ezstorage.container;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.dunk.tfc.Food.ItemFoodTFC;
import com.dunk.tfc.Handlers.FoodCraftingHandler;
import com.dunk.tfc.api.Crafting.CraftingManagerTFC;
import com.dunk.tfc.api.Interfaces.IFood;
import com.zerofall.ezstorage.storage.IStorageProvider;
import com.zerofall.ezstorage.tileentity.TileEntityStorageCore;
import com.zerofall.ezstorage.util.EZInventory;
import com.zerofall.ezstorage.util.EZInventoryManager;

public class ContainerStorageCoreCrafting extends ContainerStorageCore {

    public InventoryCrafting craftMatrix = new InventoryCrafting(this, 3, 3);
    public IInventory craftResult = new InventoryCraftResult();
    private World worldObj;
    private EntityPlayer thePlayer;

    public ContainerStorageCoreCrafting(EntityPlayer player, World world, EZInventory inventory) {
        this(player, world);
        this.inventory = inventory;

        if (this.inventory != null && this.inventory.craftMatrix != null) {
            boolean loaded = false;
            for (int k = 0; k < 9; k++) {
                if (this.inventory.craftMatrix[k] != null) {
                    this.craftMatrix.setInventorySlotContents(k, this.inventory.craftMatrix[k]);
                    loaded = true;
                }
            }
            if (loaded) {
                this.onCraftMatrixChanged(this.craftMatrix);
            }
        }
    }

    public ContainerStorageCoreCrafting(EntityPlayer player, World world, EZInventory inventory,
        TileEntityStorageCore coreTileEntity) {
        this(player, world, inventory);
        this.coreTileEntity = coreTileEntity;
    }

    public ContainerStorageCoreCrafting(EntityPlayer player, World world) {
        super(player);
        this.thePlayer = player;
        this.worldObj = world;
        this.addSlotToContainer(new SlotCrafting(player, this.craftMatrix, this.craftResult, 0, 116, 132));
        int i;
        int j;

        for (i = 0; i < 3; ++i) {
            for (j = 0; j < 3; ++j) {
                this.addSlotToContainer(new Slot(this.craftMatrix, j + i * 3, 44 + j * 18, 114 + i * 18));
            }
        }

        this.onCraftMatrixChanged(this.craftMatrix);
    }

    public void onCraftMatrixChanged(IInventory inventoryIn) {
        // Use TFCP CraftingManager first, then fall back to vanilla
        ItemStack result = null;

        if (coreTileEntity != null) {
            result = CraftingManagerTFC.getInstance()
                .findMatchingRecipe(
                    this.craftMatrix,
                    coreTileEntity.xCoord,
                    coreTileEntity.yCoord,
                    coreTileEntity.zCoord,
                    worldObj);
        }

        if (result == null) {
            result = CraftingManager.getInstance()
                .findMatchingRecipe(this.craftMatrix, this.worldObj);
        }

        this.craftResult.setInventorySlotContents(0, result);

        // Apply TFCP food post-processing to the output slot
        if (result != null && result.getItem() instanceof ItemFoodTFC) {
            FoodCraftingHandler.updateOutput(thePlayer, result, this.craftMatrix);
        }
    }

    // Shift clicking
    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slotObject = (Slot) inventorySlots.get(index);
        if (slotObject != null && slotObject.getHasStack()) {
            if (slotObject instanceof SlotCrafting) {
                // Pre-craft hook for TFCP food
                ItemStack craftStack = slotObject.getStack();
                if (craftStack != null) {
                    FoodCraftingHandler.preCraft(playerIn, craftStack, this.craftMatrix);
                }

                boolean hasChanges = false;
                ItemStack[][] recipe = new ItemStack[9][];
                for (int i = 0; i < 9; i++) {
                    recipe[i] = new ItemStack[] { this.craftMatrix.getStackInSlot(i) };
                }

                ItemStack slotStack = slotObject.getStack();
                ItemStack resultStack = null;
                ItemStack original = slotStack.copy();
                int crafted = 0;

                while (true) {
                    if (!slotObject.getHasStack() || !slotObject.getStack()
                        .isItemEqual(slotStack)) {
                        break;
                    }

                    slotStack = slotObject.getStack();
                    if (crafted + slotStack.stackSize > slotStack.getMaxStackSize()) {
                        break;
                    }

                    resultStack = slotStack.copy();
                    boolean merged = this
                        .mergeItemStack(slotStack, this.rowCount() * 9, this.rowCount() * 9 + 36, true);
                    if (!merged) {
                        return null;
                    }

                    // It merged! grab another
                    crafted += resultStack.stackSize;
                    slotObject.onSlotChange(slotStack, resultStack);
                    slotObject.onPickupFromSlot(playerIn, slotStack);

                    if (slotObject.getStack() == null || !original.isItemEqual(slotObject.getStack())) {
                        if (tryToPopulateCraftingGrid(recipe, playerIn, false)) {
                            hasChanges = true;
                        }
                    }
                }

                if (hasChanges) {
                    EZInventoryManager.sendToClients(inventory, coreTileEntity);
                }

                if (resultStack == null || slotStack.stackSize == resultStack.stackSize) {
                    return null;
                }

                return resultStack;
            } else {
                if (coreTileEntity != null) {
                    ItemStack stackInSlot = slotObject.getStack();
                    slotObject.putStack(coreTileEntity.unifiedInput(stackInSlot));
                }
                EZInventoryManager.sendToClients(inventory, coreTileEntity);
                forceSyncPlayerState(playerIn);
            }
        }
        return null;
    }

    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer playerIn) {
        if (slotId > 0 && mode == 0 && clickedButton == 0) {
            if (slotId >= 0 && inventorySlots.size() > slotId) {
                Slot slotObject = inventorySlots.get(slotId);
                if (slotObject != null) {
                    if (slotObject instanceof SlotCrafting) {
                        ItemStack[][] recipe = new ItemStack[9][];
                        for (int i = 0; i < 9; i++) {
                            recipe[i] = new ItemStack[] { this.craftMatrix.getStackInSlot(i) };
                        }
                        ItemStack result = super.slotClick(slotId, clickedButton, mode, playerIn);
                        if (result != null && tryToPopulateCraftingGrid(recipe, playerIn, false)) {
                            EZInventoryManager.sendToClients(inventory, coreTileEntity);
                        }
                        return result;
                    }
                }
            }

        }
        return super.slotClick(slotId, clickedButton, mode, playerIn);
    }

    public boolean tryToPopulateCraftingGrid(ItemStack[][] recipe, EntityPlayer playerIn, boolean usePlayerInv) {
        boolean hasChanges = false;
        // Maps playerInv slot index -> list of crafting grid slots that need an item from that player slot
        HashMap<Integer, ArrayList<Slot>> playerInvSlotsMapping = new HashMap<>();
        final int craftingSlotsStartIndex = inventorySlots.size() - 3 * 3;

        for (int j = 0; j < recipe.length; j++) {
            ItemStack[] recipeItems = recipe[j];

            Slot slot = getSlotFromInventory(this.craftMatrix, j);
            if (slot == null) {
                continue;
            }

            ItemStack stackInSlot = slot.getStack();
            if (stackInSlot != null) {
                // TFCP uses stackSize > 1 as a marker to keep items in the grid (tools, split food)
                // Skip these slots to avoid conflicts
                if (isTfcpRetainedItem(stackInSlot)) {
                    inventoryItemStacks.set(craftingSlotsStartIndex + j, null);
                    continue;
                }

                if (getMatchingItemStackForRecipe(recipeItems, stackInSlot) != null) {
                    // Already has a valid item — force GUI update
                    inventoryItemStacks.set(craftingSlotsStartIndex + j, null);
                    continue;
                }
                // Return wrong item to storage
                ItemStack result;
                if (coreTileEntity != null) {
                    result = coreTileEntity.unifiedInput(stackInSlot);
                } else {
                    result = this.inventory.input(stackInSlot);
                }
                if (result != null) {
                    playerIn.dropPlayerItemWithRandomChoice(result, false);
                }
                slot.putStack(null);
                hasChanges = true;
            }

            if (recipeItems == null || recipeItems.length == 0) {
                slot.putStack(null);
                continue;
            }

            // --- Try to find the item ---
            ItemStack retrieved = null;
            boolean foundInPlayerInv = false;

            for (int k = 0; k < recipeItems.length; k++) {
                ItemStack recipeItem = recipeItems[k];
                if (recipeItem == null) continue;

                // Normalize to 1 item
                ItemStack recipeItemOne = recipeItem.copy();
                recipeItemOne.stackSize = 1;

                // 1) Try storage first
                retrieved = getMatchingItemFromStorage(recipeItemOne);
                if (retrieved != null) {
                    hasChanges = true;
                    break;
                }

                // 2) Try player inventory if allowed
                if (usePlayerInv) {
                    Integer playerInvSize = playerIn.inventory.mainInventory.length;
                    for (int i = 0; i < playerInvSize; i++) {
                        ItemStack playerItem = playerIn.inventory.mainInventory[i];
                        if (playerItem != null && isRecipeItemValid(recipeItemOne, playerItem)) {
                            ArrayList<Slot> targetSlots = playerInvSlotsMapping.get(i);
                            if (targetSlots == null) {
                                targetSlots = new ArrayList<>();
                                playerInvSlotsMapping.put(i, targetSlots);
                            }
                            // Check we haven't already consumed all copies of this item
                            if (playerItem.stackSize > targetSlots.size()) {
                                targetSlots.add(slot);
                                foundInPlayerInv = true;
                                break;
                            }
                        }
                    }
                    if (foundInPlayerInv) break;
                }
            }

            if (retrieved != null) {
                slot.putStack(retrieved);
            } else if (!foundInPlayerInv) {
                // Nothing found anywhere — clear slot
                slot.putStack(null);
            }
            // If foundInPlayerInv==true, the second loop below will fill the slot
        }

        // Second pass: transfer items from player inventory into crafting grid slots
        if (usePlayerInv && !playerInvSlotsMapping.isEmpty()) {
            Set<Entry<Integer, ArrayList<Slot>>> set = playerInvSlotsMapping.entrySet();
            for (Entry<Integer, ArrayList<Slot>> entry : set) {
                Integer playerInvSlotId = entry.getKey();
                ArrayList<Slot> targetSlots = entry.getValue();
                int targetSlotsCount = targetSlots.size();

                ItemStack playerInvSlot = playerIn.inventory.mainInventory[playerInvSlotId];
                if (playerInvSlot == null) continue;

                // Distribute evenly, last slot gets the remainder
                int itemsToRequest = playerInvSlot.stackSize / targetSlotsCount;
                if (itemsToRequest < 1) itemsToRequest = 1;

                for (int j = 0; j < targetSlotsCount; j++) {
                    Slot targetSlot = targetSlots.get(j);
                    if (targetSlot == null) continue;

                    // Re-fetch in case previous iteration consumed some
                    playerInvSlot = playerIn.inventory.mainInventory[playerInvSlotId];
                    if (playerInvSlot == null) break;

                    int toTake = (j == targetSlotsCount - 1) ? playerInvSlot.stackSize : itemsToRequest;
                    if (toTake < 1) toTake = 1;

                    ItemStack taken = playerIn.inventory.decrStackSize(playerInvSlotId, toTake);
                    if (taken != null && taken.stackSize > 0) {
                        // If slot already has a compatible item (e.g. from a previous grid state), merge
                        ItemStack existing = targetSlot.getStack();
                        if (existing != null && EZInventory.stacksEqual(existing, taken)) {
                            existing.stackSize += taken.stackSize;
                        } else {
                            targetSlot.putStack(taken);
                        }
                    } else {
                        if (targetSlot.getStack() == null) {
                            targetSlot.putStack(null);
                        }
                    }
                }
            }
        }

        return hasChanges;
    }

    /**
     * Check if an item in the crafting grid is being retained by TFCP's
     * stackSize++ mechanism. TFCP uses stackSize > 1 (up to 2) to mark items
     * that should stay in the grid after crafting (tools like knives, split
     * food portions).
     */
    private static boolean isTfcpRetainedItem(ItemStack stack) {
        if (stack == null) return false;
        if (stack.stackSize > 1) {
            return stack.getItem() instanceof IFood;
        }
        return false;
    }

    private ItemStack getMatchingItemFromStorage(ItemStack recipeItem) {
        if (coreTileEntity != null) {
            for (IStorageProvider provider : coreTileEntity.getProviders()) {
                if (!provider.isValid()) continue;
                List<ItemStack> items = provider.getAllItems();
                for (int i = 0; i < items.size(); i++) {
                    ItemStack group = items.get(i);
                    if (isRecipeItemValid(recipeItem, group)) {
                        if (group.stackSize >= recipeItem.stackSize) {
                            ItemStack result = provider.extract(i, 2);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                }
            }
            return null;
        }
        List<ItemStack> allItems = this.inventory.getAllItems();
        for (int i = 0; i < allItems.size(); i++) {
            ItemStack group = allItems.get(i);
            if (isRecipeItemValid(recipeItem, group)) {
                if (group.stackSize >= recipeItem.stackSize) {
                    return this.inventory.getItemStackAt(i, recipeItem.stackSize);
                }
            }
        }
        return null;
    }

    private static boolean isRecipeItemValid(ItemStack recipeItem, ItemStack candidate) {
        if (recipeItem == null || candidate == null || recipeItem.getItem() == null || candidate.getItem() == null)
            return false;
        if (OreDictionary.itemMatches(recipeItem, candidate, false)) {
            return true;
        }
        // Custom flexible check for tools & specific mod items where OreDictionary might be bypassed
        if (recipeItem.getItem() == candidate.getItem()) {
            if (recipeItem.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || recipeItem.getItemDamage() == candidate.getItemDamage()
                || recipeItem.isItemStackDamageable()) {
                return true;
            }
        }
        // Fallback for special items that might not match with OreDictionary standard (but vanilla handles)
        return EZInventory.stacksEqual(recipeItem, candidate);
    }

    private static ItemStack getMatchingItemStackForRecipe(ItemStack[] recipeItems, ItemStack stack) {
        if (recipeItems == null) {
            return null;
        }
        for (ItemStack recipeItem : recipeItems) {
            if (isRecipeItemValid(recipeItem, stack)) {
                return recipeItem;
            }
        }
        return null;
    }

    @Override
    protected int playerInventoryY() {
        return 174;
    }

    @Override
    protected int rowCount() {
        return 5;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        saveGrid();
        super.onContainerClosed(playerIn);
    }

    public void saveGrid() {
        if (this.inventory != null) {
            if (this.inventory.craftMatrix == null) {
                this.inventory.craftMatrix = new ItemStack[9];
            }
            boolean hasChanges = false;
            for (int i = 0; i < 9; i++) {
                ItemStack current = this.craftMatrix.getStackInSlot(i);
                if (!EZInventory.stacksEqual(this.inventory.craftMatrix[i], current)) {
                    hasChanges = true;
                }
                this.inventory.craftMatrix[i] = current;
            }
            if (hasChanges) {
                this.inventory.setHasChanges();
                EZInventoryManager.saveInventory(this.inventory);
            }
        }
    }

    public void clearGrid(EntityPlayer playerIn) {
        boolean cleared = false;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.craftMatrix.getStackInSlot(i);
            if (stack != null) {
                ItemStack result;
                if (coreTileEntity != null) {
                    result = coreTileEntity.unifiedInput(stack);
                } else {
                    result = this.inventory.input(stack);
                }
                this.craftMatrix.setInventorySlotContents(i, null);
                if (result != null) {
                    playerIn.dropPlayerItemWithRandomChoice(result, false);
                }
                cleared = true;
            }
        }

        if (cleared && !playerIn.worldObj.isRemote) {
            EZInventoryManager.sendToClients(inventory, coreTileEntity);
        }
    }
}
