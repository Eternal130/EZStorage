package com.zerofall.ezstorage.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.Reference;
import com.zerofall.ezstorage.container.ContainerStorageCore;
import com.zerofall.ezstorage.network.server.MsgStorage;
import com.zerofall.ezstorage.tileentity.TileEntityStorageCore;

public class EZInventoryManager {

    private static final HashMap<String, EZInventory> inventories = new HashMap<>();
    private static final Map<String, List<TileEntityStorageCore>> coreRegistry = new HashMap<>();

    public static void registerCore(TileEntityStorageCore core) {
        if (core == null || core.inventoryId == null || core.inventoryId.isEmpty()) {
            return;
        }
        coreRegistry.computeIfAbsent(core.inventoryId, k -> new ArrayList<>())
            .add(core);
    }

    public static void unregisterCore(TileEntityStorageCore core) {
        if (core == null || core.inventoryId == null || core.inventoryId.isEmpty()) {
            return;
        }
        List<TileEntityStorageCore> cores = coreRegistry.get(core.inventoryId);
        if (cores != null) {
            cores.remove(core);
            if (cores.isEmpty()) {
                coreRegistry.remove(core.inventoryId);
            }
        }
    }

    public static EZInventory createInventory() {
        return createInventory(new EZInventory());
    }

    public static EZInventory createInventory(EZInventory inventory) {
        if (!inventories.containsKey(inventory.id)) {
            inventory.id = UUID.randomUUID()
                .toString();
            inventories.put(inventory.id, inventory);
        }
        inventory.setHasChanges();
        return inventory;
    }

    public static EZInventory getInventory(String id) {
        // Find loaded inventory
        EZInventory cached = inventories.get(id);
        if (cached != null) {
            return cached;
        }

        // Load inventory
        NBTTagCompound tag = readFromFile(getFilePath(id));
        if (tag != null) {
            EZInventory inventory = new EZInventory();
            if (inventory != null) {
                inventory.readFromNBT(tag);
                inventory.resetHasChanges();
                inventory.id = id;
                inventories.put(inventory.id, inventory);
                return inventory;
            }
        }

        // Inventory not found
        return null;
    }

    public static void saveInventories() {
        HashMap<String, NBTTagCompound> cache = new HashMap<String, NBTTagCompound>();

        // Write to NBT
        for (EZInventory inventory : inventories.values()) {
            if (inventory.getHasChanges()) {
                NBTTagCompound tag = new NBTTagCompound();
                inventory.writeToNBT(tag);
                inventory.resetHasChanges();
                cache.put(inventory.id, tag);
            }
        }

        // Write to file
        if (!cache.isEmpty()) {
            new Thread(() -> {
                synchronized (inventories) {
                    for (Entry<String, NBTTagCompound> kvp : cache.entrySet()) {
                        File file = getFilePath(kvp.getKey());
                        saveToFile(kvp.getValue(), file);
                    }
                }
            }).start();
        }
    }

    public static void saveInventory(EZInventory inventory) {
        if (inventories.containsKey(inventory.id) && inventory.getHasChanges()) {
            NBTTagCompound tag = new NBTTagCompound();
            inventory.writeToNBT(tag);
            inventory.resetHasChanges();
            File file = getFilePath(inventory.id);
            new Thread(() -> {
                synchronized (inventories) {
                    saveToFile(tag, file);
                }
            }).start();
        }
    }

    public static void deleteInventory(EZInventory inventory) {
        if (inventories.remove(inventory.id) != null) {
            File file = getFilePath(inventory.id);
            new Thread(() -> {
                synchronized (inventories) {
                    file.delete();
                }
            }).start();;
        }
    }

    private static File getFilePath(String id) {
        // World root directory
        File worldDir = DimensionManager.getCurrentSaveRootDirectory();

        // EZInventory root directory
        File inventoryDir = new File(worldDir, Reference.MOD_ID + "/inventories");
        inventoryDir.mkdirs();

        // Inventory file
        return new File(inventoryDir, id.toString() + ".dat");
    }

    private static void saveToFile(NBTTagCompound tag, File file) {
        File fileNew = new File(file + ".new");
        File fileOld = new File(file + ".old");

        try {
            // Write to new temporary file
            FileOutputStream outputStream = new FileOutputStream(fileNew);
            CompressedStreamTools.writeCompressed(tag, outputStream);
            outputStream.close();

            // Delete old backup file
            if (fileOld.exists()) {
                fileOld.delete();
            }

            // Rename existing file to old backup file
            file.renameTo(fileOld);

            // Delete current existing file
            if (file.exists()) {
                file.delete();
            }

            // Rename new temporary file
            if (!fileNew.renameTo(file)) {
                throw new IOException("Couldn't rename new temporary file.");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            EZStorage.instance.LOG.warn("Couldn't write inventory to file system.", ex);
        }
    }

    private static NBTTagCompound readFromFile(File file) {
        File fileOld = new File(file + ".old");
        NBTTagCompound tag = null;

        if (file.exists()) {
            try {
                FileInputStream inputStream = new FileInputStream(file);
                tag = CompressedStreamTools.readCompressed(inputStream);
                inputStream.close();
            } catch (IOException ex) {
                EZStorage.instance.LOG.warn("Couldn't read inventory file. Try falling back to backup, if exists.", ex);
                ex.printStackTrace();
            }
        }

        if (tag == null && fileOld.exists()) {
            try {
                FileInputStream inputStream = new FileInputStream(fileOld);
                tag = CompressedStreamTools.readCompressed(inputStream);
                inputStream.close();
            } catch (IOException ex) {
                EZStorage.instance.LOG.warn("Couldn't read inventory backup file.", ex);
                ex.printStackTrace();
            }
        }

        return tag;
    }

    public static void sendToClients(EZInventory inventory) {
        sendToClients(inventory, true);
    }

    /**
     * Build a MsgStorage that represents the unified (merged across all providers) view
     * of this inventory, without mutating the EZInventory instance.
     * Uses a temporary EZInventory to serialize unified data to NBT.
     */
    public static MsgStorage buildUnifiedMsg(EZInventory inventory, TileEntityStorageCore core) {
        if (core == null || core.getProviders()
            .size() <= 1) {
            return new MsgStorage(inventory);
        }

        // Construct a temporary inventory with unified data for serialization only
        EZInventory temp = new EZInventory();
        temp.inventory = new ArrayList<ItemStack>(core.getUnifiedItemList());
        temp.maxItems = core.getUnifiedCapacity();

        return new MsgStorage(inventory.id, temp);
    }

    /**
     * Send unified (merged across all providers) item data to clients.
     */
    public static void sendToClients(EZInventory inventory, TileEntityStorageCore core) {
        if (core == null || core.getProviders()
            .size() <= 1) {
            sendToClients(inventory);
            return;
        }

        MsgStorage msg = buildUnifiedMsg(inventory, core);
        sendMsgToClients(inventory, msg);
    }

    private static void sendMsgToClients(EZInventory inventory, MsgStorage msg) {
        if (inventory == null || !inventories.containsKey(inventory.id)) {
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }

        for (WorldServer world : server.worldServers) {
            for (EntityPlayer player : world.playerEntities) {
                if (player.openContainer instanceof ContainerStorageCore container && container.inventory == inventory
                    && player instanceof EntityPlayerMP playerMP) {
                    EZStorage.instance.network.sendTo(msg, playerMP);
                }
            }
        }
    }

    public static void sendToClients(EZInventory inventory, boolean checkTileEntities) {
        if (inventory == null || !inventories.containsKey(inventory.id)) {
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }

        MsgStorage msg = new MsgStorage(inventory);
        for (WorldServer world : server.worldServers) {
            for (EntityPlayer player : world.playerEntities) {
                if (player.openContainer instanceof ContainerStorageCore container && container.inventory == inventory
                    && player instanceof EntityPlayerMP playerMP) {
                    EZStorage.instance.network.sendTo(msg, playerMP);
                }
            }
        }

        if (checkTileEntities) {
            List<TileEntityStorageCore> cores = coreRegistry.get(inventory.id);
            if (cores != null) {
                for (TileEntityStorageCore core : cores) {
                    core.updateTileEntity(false);
                }
            }
        }
    }

    public static void clearCache() {
        inventories.clear();
    }
}
