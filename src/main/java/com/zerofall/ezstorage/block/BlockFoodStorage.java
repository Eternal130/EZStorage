package com.zerofall.ezstorage.block;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.zerofall.ezstorage.tileentity.TileEntityFoodStorage;

public class BlockFoodStorage extends EZBlockContainer {

    public BlockFoodStorage() {
        super("food_storage", Material.wood);
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileEntityFoodStorage();
    }
}
