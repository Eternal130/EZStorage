package com.zerofall.ezstorage.integration.waila;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.zerofall.ezstorage.block.BlockFoodStorage;
import com.zerofall.ezstorage.tileentity.TileEntityFoodStorage;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * Waila/WDMla tooltip provider for the food box, registered through the
 * classic "Waila"/register IMC message (processed by Waila itself and by
 * WDMla's old-API compat layer). This class is only classloaded by the
 * receiving tooltip mod — common code merely sends the IMC string.
 */
public class FoodStorageWailaProvider implements IWailaDataProvider {

    /** IMC entry point, invoked reflectively by the receiving tooltip mod. */
    public static void callbackRegister(IWailaRegistrar registrar) {
        FoodStorageWailaProvider instance = new FoodStorageWailaProvider();
        registrar.registerBodyProvider(instance, BlockFoodStorage.class);
        registrar.registerNBTProvider(instance, TileEntityFoodStorage.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null; // no override: default item shown
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip; // default head
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound food = accessor.getNBTData()
            .getCompoundTag("ezFood");
        if (!food.getBoolean("HasFood")) {
            currenttip.add(StatCollector.translateToLocal("hud.msg.ezstorage.food.waila.empty"));
            return currenttip;
        }
        String name = food.getString("Name");
        float weight = food.getFloat("Weight");
        float decay = food.getFloat("Decay");
        float capOz = food.getFloat("CapOz");
        currenttip.add(StatCollector.translateToLocalFormatted("hud.msg.ezstorage.food.waila.content", name));
        currenttip.add(
            StatCollector.translateToLocalFormatted(
                "hud.msg.ezstorage.food.totalweight",
                String.format("%.1f", weight),
                String.format("%.1f", capOz)));
        if (decay > 0) {
            String pct = String.format("%.1f", decay * 100.0f / Math.max(weight, 0.01f));
            currenttip.add(
                StatCollector
                    .translateToLocalFormatted("hud.msg.ezstorage.food.decay", String.format("%.0f", decay), pct));
        }
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip; // default tail
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (!(te instanceof TileEntityFoodStorage foodTe)) return tag;
        NBTTagCompound food = new NBTTagCompound();
        if (foodTe.hasFood()) {
            food.setBoolean("HasFood", true);
            food.setString(
                "Name",
                foodTe.getTemplateStack()
                    .getDisplayName());
            food.setFloat("Weight", foodTe.getTotalWeightOz());
            food.setFloat("Decay", foodTe.getTotalDecayOz());
            food.setFloat("CapOz", TileEntityFoodStorage.getExtractCapOz(foodTe.getTemplateStack()));
        } else {
            food.setBoolean("HasFood", false);
        }
        tag.setTag("ezFood", food);
        return tag;
    }
}
