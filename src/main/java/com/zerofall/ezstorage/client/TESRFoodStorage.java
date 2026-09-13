package com.zerofall.ezstorage.client;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

import com.zerofall.ezstorage.tileentity.TileEntityFoodStorage;

/**
 * Renders the stored food's icon as overlay quads on the box's exterior
 * faces (top + four sides), modeled on TFC's TESRFoodPrep.drawItem: items
 * texture atlas, icon min/max UV, no explicit brightness. The icon data
 * reaches the client via the TE's description packet, which is only resent
 * on template transitions (first insert / full rot-clear).
 */
public class TESRFoodStorage extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityFoodStorage foodTe)) return;
        ItemStack template = foodTe.getTemplateStack();
        if (template == null) return;
        IIcon icon = template.getIconIndex();
        if (icon == null) return;

        bindTexture(TextureMap.locationItemsTexture);
        Tessellator tess = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        // Side quads are drawn single-sided and the winding varies per face.
        GL11.glDisable(GL11.GL_CULL_FACE);

        // Top face: inset square slightly above the block top (no z-fighting).
        // The 0.125..0.875 span matches the box texture's inner wood face
        // (1px dark border + 1px transition ring = 2px frame).
        quad(
            tess,
            icon,
            0.125d,
            1.002d,
            0.125d,
            0.875d,
            1.002d,
            0.125d,
            0.875d,
            1.002d,
            0.875d,
            0.125d,
            1.002d,
            0.875d,
            0.0f,
            1.0f,
            0.0f);
        // Four sides, each slightly outside its face plane (overlaying the wood).
        quad(
            tess,
            icon,
            0.125d,
            0.125d,
            -0.002d,
            0.875d,
            0.125d,
            -0.002d,
            0.875d,
            0.875d,
            -0.002d,
            0.125d,
            0.875d,
            -0.002d,
            0.0f,
            0.0f,
            -1.0f); // -Z
        quad(
            tess,
            icon,
            0.875d,
            0.125d,
            1.002d,
            0.125d,
            0.125d,
            1.002d,
            0.125d,
            0.875d,
            1.002d,
            0.875d,
            0.875d,
            1.002d,
            0.0f,
            0.0f,
            1.0f); // +Z
        quad(
            tess,
            icon,
            -0.002d,
            0.125d,
            0.125d,
            -0.002d,
            0.125d,
            0.875d,
            -0.002d,
            0.875d,
            0.875d,
            -0.002d,
            0.875d,
            0.125d,
            -1.0f,
            0.0f,
            0.0f); // -X
        quad(
            tess,
            icon,
            1.002d,
            0.125d,
            0.875d,
            1.002d,
            0.125d,
            0.125d,
            1.002d,
            0.875d,
            0.125d,
            1.002d,
            0.875d,
            0.875d,
            1.0f,
            0.0f,
            0.0f); // +X

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    /**
     * Draws one icon-textured quad from four corner positions (CCW seen from
     * the normal side). Mirrored UV orientation on some side faces is fine —
     * food icons are effectively symmetric for identification purposes.
     */
    private static void quad(Tessellator tess, IIcon icon, double x1, double y1, double z1, double x2, double y2,
        double z2, double x3, double y3, double z3, double x4, double y4, double z4, float nx, float ny, float nz) {
        double uMin = icon.getMinU(), uMax = icon.getMaxU(), vMin = icon.getMinV(), vMax = icon.getMaxV();
        tess.startDrawingQuads();
        tess.setNormal(nx, ny, nz);
        tess.addVertexWithUV(x1, y1, z1, uMin, vMax);
        tess.addVertexWithUV(x2, y2, z2, uMax, vMax);
        tess.addVertexWithUV(x3, y3, z3, uMax, vMin);
        tess.addVertexWithUV(x4, y4, z4, uMin, vMin);
        tess.draw();
    }
}
