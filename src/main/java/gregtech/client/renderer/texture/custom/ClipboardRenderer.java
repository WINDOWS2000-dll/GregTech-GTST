package gregtech.client.renderer.texture.custom;

import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.texture.IconRegistrar;
import gregtech.common.metatileentities.MetaTileEntityClipboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import codechicken.lib.vec.Rotation;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ClipboardRenderer implements IconRegistrar {

    private static final Cuboid6 pageBox = new Cuboid6(3 / 16.0, 0.25 / 16.0, 0.25 / 16.0, 13 / 16.0, 14.25 / 16.0,
            0.3 / 16.0);
    private static final Cuboid6 boardBox = new Cuboid6(2.75 / 16.0, 0 / 16.0, 0 / 16.0, 13.25 / 16.0, 15.25 / 16.0,
            0.25 / 16.0);
    private static final Cuboid6 clipBox = new Cuboid6(5.75 / 16.0, 14.75 / 16.0, 0.25 / 16.0, 10.25 / 16.0,
            15.5 / 16.0, 0.4 / 16.0);
    private static final Cuboid6 graspBox = new Cuboid6(7 / 16.0, 15.25 / 16.0, 0.1 / 16.0, 9 / 16.0, 16 / 16.0,
            0.35 / 16.0);

    private static final List<EnumFacing> rotations = Arrays.asList(EnumFacing.NORTH, EnumFacing.WEST, EnumFacing.SOUTH,
            EnumFacing.EAST);

    @SideOnly(Side.CLIENT)
    private static HashMap<Cuboid6, TextureAtlasSprite> boxTextureMap;

    @SideOnly(Side.CLIENT)
    private TextureAtlasSprite[] textures;

    public ClipboardRenderer() {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            boxTextureMap = new HashMap<>();
            textures = new TextureAtlasSprite[3];
        }
        Textures.iconRegisters.add(this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(@NotNull TextureMap textureMap) {
        this.textures[0] = textureMap.registerSprite(new ResourceLocation("gregtech:blocks/clipboard/wood"));
        boxTextureMap.put(boardBox, this.textures[0]);
        this.textures[1] = textureMap.registerSprite(new ResourceLocation("gregtech:blocks/clipboard/clip"));
        boxTextureMap.put(clipBox, this.textures[1]);
        boxTextureMap.put(graspBox, this.textures[1]);
        this.textures[2] = textureMap.registerSprite(new ResourceLocation("gregtech:blocks/clipboard/page"));
        boxTextureMap.put(pageBox, this.textures[2]);
    }

    @SideOnly(Side.CLIENT)
    public static void renderBoard(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline,
                                   EnumFacing rotation, MetaTileEntityClipboard clipboard, float partialTicks) {
        translation.translate(0.5, 0.5, 0.5);
        translation.rotate(Math.toRadians(90.0 * rotations.indexOf(rotation)), Rotation.axes[1]);
        translation.translate(-0.5, -0.5, -0.5);
        World world = clipboard.getWorld();
        if (world != null) renderState.setBrightness(world, clipboard.getPos());
        // Render Clipboard
        for (EnumFacing renderSide : EnumFacing.VALUES) {
            boxTextureMap.forEach((box, sprite) -> Textures.renderFace(renderState, translation, pipeline, renderSide,
                    box, sprite, null));
        }
    }

    @SideOnly(Side.CLIENT)
    public static void renderGUI(double x, double y, double z, EnumFacing rotation, MetaTileEntityClipboard clipboard,
                                 float partialTicks) {
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.pushMatrix();
        float lastBrightnessX = OpenGlHelper.lastBrightnessX;
        float lastBrightnessY = OpenGlHelper.lastBrightnessY;

        float lx = 240, ly = 240;
        World world = clipboard.getWorld();
        if (world != null) {
            int light = world.getCombinedLight(clipboard.getPos(), 0);

            lx = (float) light % 0x10000;
            ly = (float) light / 0x10000;
        }

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        RenderHelper.disableStandardItemLighting();

        // All of these are done in reverse order, by the way, if you're reviewing this :P

        GlStateManager.translate(x, y, z);
        GlStateManager.translate(0.5, 0.451, 0.5);
        GlStateManager.rotate((float) (90.0 * rotations.indexOf(rotation)), 0, 1, 0);
        GlStateManager.translate(0, 0, -0.468);
        GlStateManager.rotate(180, 1, 0, 0);
        GlStateManager.scale(0.875, 0.875, 0.875);

        if (clipboard.hasFakeGui()) {
            Pair<Double, Double> result = clipboard.checkLookingAt(Minecraft.getMinecraft().player);
            GlStateManager.translate(0, 0, 0.01);
            GlStateManager.scale(1, 1, -1);
            if (result == null) {
                clipboard.drawFakeGui(0, 0, partialTicks);
            } else {
                clipboard.drawFakeGui(result.getKey(), result.getValue(), partialTicks);
            }
        }

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastBrightnessX, lastBrightnessY);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    @SideOnly(Side.CLIENT)
    public TextureAtlasSprite getParticleTexture() {
        return textures[0];
    }

    /**
     * Draws the projected page GUI of every nearby placed clipboard. Run once per frame after all normal world
     * geometry has been drawn (see {@link gregtech.client.event.ClientEventHandler#onRenderWorldLast}), so the
     * Tessellator is free for MUI2 to use without corrupting the shared buffers used by the fast render pass.
     */
    @SideOnly(Side.CLIENT)
    public static void renderWorldLastEvent(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        Entity view = mc.getRenderViewEntity();
        if (world == null || view == null) return;

        float partialTicks = event.getPartialTicks();
        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        double maxDistanceSq = 64.0 * 64.0;
        for (TileEntity tileEntity : world.loadedTileEntityList) {
            if (!(tileEntity instanceof IGregTechTileEntity gtTileEntity)) continue;
            if (!(gtTileEntity.getMetaTileEntity() instanceof MetaTileEntityClipboard clipboard)) continue;
            BlockPos pos = clipboard.getPos();
            double dx = pos.getX() + 0.5 - camX;
            double dy = pos.getY() + 0.5 - camY;
            double dz = pos.getZ() + 0.5 - camZ;
            if (dx * dx + dy * dy + dz * dz > maxDistanceSq) continue;
            clipboard.renderProjectedGui(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ, partialTicks);
        }
    }
}
