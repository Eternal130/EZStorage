package com.zerofall.ezstorage.proxy;

import com.zerofall.ezstorage.EZStorage;
import com.zerofall.ezstorage.client.TESRFoodStorage;
import com.zerofall.ezstorage.integration.IntegrationUtils;
import com.zerofall.ezstorage.tileentity.TileEntityFoodStorage;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(EZStorage instance, FMLInitializationEvent event) {
        super.init(instance, event);
        IntegrationUtils.initClient();
        eventHandler.initKeybinds();

        // TESR: food icon overlay on the storage box (client-only class, never referenced server-side)
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityFoodStorage.class, new TESRFoodStorage());
    }
}
