package com.zerofall.ezstorage.recipes;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.zerofall.ezstorage.enums.PortableStoragePanelTier;
import com.zerofall.ezstorage.init.EZBlocks;
import com.zerofall.ezstorage.item.ItemPortableStoragePanel;

public class PortableStoragePanelUpgradeRecipe implements IRecipe {

    private ItemStack result;

    @Override
    public boolean matches(InventoryCrafting craftingGridInventory, World world) {
        result = null;
        ItemStack panelStack = null;
        ItemPortableStoragePanel panelItem = null;
        boolean hasRedstone = false;
        boolean hasUpgradeItem = false;
        boolean hasCraftingUpgrade = false;
        PortableStoragePanelTier tier = null;
        PortableStoragePanelTier nextTier = null;

        for (int i = 0; i < craftingGridInventory.getSizeInventory(); i++) {
            ItemStack slotStack = craftingGridInventory.getStackInSlot(i);
            if (slotStack == null) {
                continue;
            }

            Item slotItem = slotStack.getItem();
            Block slotBlock = (slotItem instanceof ItemBlock) ? ((ItemBlock) slotItem).field_150939_a : null;
            if (panelStack == null && slotItem instanceof ItemPortableStoragePanel panel) {
                panelStack = slotStack;
                panelItem = panel;
                tier = panelItem.getTier(panelStack);
                nextTier = PortableStoragePanelTier.getNextTier(tier);
            } else if (!hasRedstone && slotBlock == Blocks.redstone_block) {
                hasRedstone = true;
            } else if (!hasUpgradeItem && nextTier != null && isUpgradeMatch(slotItem, nextTier)) {
                hasUpgradeItem = true;
            } else if (!hasCraftingUpgrade && slotBlock == EZBlocks.crafting_box) {
                hasCraftingUpgrade = true;
            } else {
                break;
            }
        }

        if (panelStack != null && panelItem != null && hasRedstone) {
            if (hasUpgradeItem && tier != null && nextTier != null) {
                result = panelStack.copy();
                panelItem.setTier(result, nextTier);
                return true;
            } else if (hasCraftingUpgrade) {
                result = panelStack.copy();
                panelItem.setHasCraftingArea(result, true);
                return true;
            }
        }

        return false;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting p_77572_1_) {
        if (result != null) {
            return result.copy();
        }
        return null;
    }

    @Override
    public int getRecipeSize() {
        return 3;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return result;
    }

    public static ItemStack getUpgradeItemStack(PortableStoragePanelTier tier) {
        switch (tier) {
            case TIER_2:
                return new ItemStack(Items.ender_pearl);
            case TIER_3:
                return new ItemStack(Items.ender_eye);
            case TIER_INFINITY:
                return getFirstOreDictStack("ingotBlueSteel");
            default:
                return null;
        }
    }

    private static boolean isUpgradeMatch(Item item, PortableStoragePanelTier tier) {
        switch (tier) {
            case TIER_2:
                return item == Items.ender_pearl;
            case TIER_3:
                return item == Items.ender_eye;
            case TIER_INFINITY:
                return isOreDictMatch(item, "ingotBlueSteel");
            default:
                return false;
        }
    }

    private static boolean isOreDictMatch(Item item, String oreName) {
        for (ItemStack oreStack : OreDictionary.getOres(oreName)) {
            if (oreStack != null && oreStack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack getFirstOreDictStack(String oreName) {
        for (ItemStack oreStack : OreDictionary.getOres(oreName)) {
            if (oreStack != null && oreStack.getItem() != null) {
                return oreStack.copy();
            }
        }
        return null;
    }
}
