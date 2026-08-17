package gregtech.common.metatileentities;

import gregtech.api.GregTechAPI;
import gregtech.api.items.itemhandlers.InaccessibleItemStackHandler;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.metatileentity.IFastRenderMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.GregTechGuiScreen;
import gregtech.api.mui.factory.MetaTileEntityGuiFactory;
import gregtech.api.util.GTLog;
import gregtech.client.renderer.texture.custom.ClipboardRenderer;
import gregtech.common.items.behaviors.ClipboardBehavior;
import gregtech.core.network.packets.PacketClipboardNBTUpdate;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import codechicken.lib.vec.Vector3;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ClientScreenHandler;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static codechicken.lib.raytracer.RayTracer.*;
import static gregtech.api.capability.GregtechDataCodes.*;
import static gregtech.client.renderer.texture.Textures.CLIPBOARD_RENDERER;
import static gregtech.common.items.MetaItems.CLIPBOARD;

public class MetaTileEntityClipboard extends MetaTileEntity implements IFastRenderMetaTileEntity {

    private static final AxisAlignedBB CLIPBOARD_AABB_NORTH = new AxisAlignedBB(2.75 / 16.0, 0, 0, 13.25 / 16.0,
            16 / 16.0, 0.4 / 16.0);
    private static final AxisAlignedBB CLIPBOARD_AABB_SOUTH = new AxisAlignedBB(13.25 / 16.0, 0, 16 / 16.0, 2.75 / 16.0,
            16 / 16.0, 15.6 / 16.0);
    private static final AxisAlignedBB CLIPBOARD_AABB_WEST = new AxisAlignedBB(0, 0, 13.25 / 16.0, 0.4 / 16.0,
            16 / 16.0, 2.75 / 16.0);
    private static final AxisAlignedBB CLIPBOARD_AABB_EAST = new AxisAlignedBB(16 / 16.0, 0, 2.75 / 16.0, 15.6 / 16.0,
            16 / 16.0, 13.25 / 16.0);

    private static final AxisAlignedBB PAGE_AABB_NORTH = new AxisAlignedBB(3 / 16.0, 0.25 / 16.0, 0.25 / 16.0,
            13 / 16.0, 14.25 / 16.0, 0.3 / 16.0);
    private static final AxisAlignedBB PAGE_AABB_SOUTH = new AxisAlignedBB(13 / 16.0, 0.25 / 16.0, 15.75 / 16.0,
            3 / 16.0, 14.25 / 16.0, 15.7 / 16.0);
    private static final AxisAlignedBB PAGE_AABB_WEST = new AxisAlignedBB(0.25 / 16.0, 0.25 / 16.0, 13 / 16.0,
            0.3 / 16.0, 14.25 / 16.0, 3 / 16.0);
    private static final AxisAlignedBB PAGE_AABB_EAST = new AxisAlignedBB(15.75 / 16.0, 0.25 / 16.0, 3 / 16.0,
            15.7 / 16.0, 14.25 / 16.0, 13 / 16.0);

    public static final float scale = 1;
    private static final NBTBase NO_CLIPBOARD_SIG = new NBTTagInt(0);
    private boolean didSetFacing = false;

    /** Headless client-side widget tree projected in world space onto the placed clipboard. */
    @SideOnly(Side.CLIENT)
    private ModularScreen fakeGuiScreen;
    @SideOnly(Side.CLIENT)
    private GuiScreenWrapper fakeGuiWrapper;
    /** Server-side cache used to detect clipboard NBT changes so they can be broadcast to all clients. */
    private NBTTagCompound lastSyncedClipboardNBT;

    public MetaTileEntityClipboard(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public void update() {
        super.update();
        if (getWorld().isRemote) {
            if (fakeGuiScreen == null) {
                createFakeGui();
            }
        } else {
            NBTTagCompound current = getClipboard().getTagCompound();
            if (!Objects.equals(current, lastSyncedClipboardNBT)) {
                lastSyncedClipboardNBT = current == null ? null : current.copy();
                writeCustomData(SYNC_CLIPBOARD_NBT,
                        buf -> buf.writeCompoundTag(current == null ? new NBTTagCompound() : current));
            }
        }
    }

    @Override
    public int getLightOpacity() {
        return 0;
    }

    @Override
    public void renderMetaTileEntityFast(CCRenderState renderState, Matrix4 translation, float partialTicks) {
        ClipboardRenderer.renderBoard(renderState, translation.copy(), new IVertexOperation[] {}, getFrontFacing(),
                this, partialTicks);
    }

    // Intentionally left empty: the projected page GUI is NOT drawn here. This is called from inside
    // MetaTileEntityTESR's fast render pass, which draws into a BufferBuilder that is already mid-`begin()` for
    // block geometry; MUI2's screen drawing needs the Tessellator to itself, so drawing it here would corrupt
    // that shared buffer (and with it, unrelated world rendering like water/glass). See #renderProjectedGui.
    @Override
    public void renderMetaTileEntity(double x, double y, double z, float partialTicks) {}

    /**
     * Draws the always-visible page projection onto this placed clipboard. Called from
     * {@link ClipboardRenderer#renderWorldLastEvent} during {@link net.minecraftforge.client.event.RenderWorldLastEvent},
     * i.e. after all normal world geometry has been drawn and the Tessellator is free for MUI2 to use safely.
     *
     * {@code x}/{@code y}/{@code z} are the camera-relative position of this clipboard, matching the convention of
     * {@link IFastRenderMetaTileEntity#renderMetaTileEntity}.
     */
    @SideOnly(Side.CLIENT)
    public void renderProjectedGui(double x, double y, double z, float partialTicks) {
        if (this.getClipboard() != null)
            ClipboardRenderer.renderGUI(x, y, z, this.getFrontFacing(), this, partialTicks);
    }

    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(getPos().add(-1, 0, -1), getPos().add(2, 2, 2));
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        return null;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityClipboard(metaTileEntityId);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        if (!getClipboard().isItemEqual(CLIPBOARD.getStackForm())) return null;
        return ClipboardBehavior.buildEditablePanel(GTGuis.createPanel(this, 186, 263), getClipboard(),
                panelSyncManager);
    }

    /**
     * (Re)builds the headless, client-only widget tree that is projected in world space onto the placed clipboard.
     * MUI2's normal Forge-event-driven screen handling only ever operates on {@code Minecraft.currentScreen}, so it
     * cannot be used here; instead {@link #drawFakeGui(double, double, float)} drives the {@link ModularScreen} and
     * {@link GuiScreenWrapper} directly, bypassing that machinery entirely.
     */
    @SideOnly(Side.CLIENT)
    public void createFakeGui() {
        try {
            if (!getClipboard().isItemEqual(CLIPBOARD.getStackForm())) return;
            ModularPanel panel = ClipboardBehavior.buildDisplayPanel(getClipboard());
            GregTechGuiScreen screen = new GregTechGuiScreen(panel, GTGuiTheme.STANDARD);
            this.fakeGuiWrapper = new GuiScreenWrapper(screen);
            this.fakeGuiScreen = screen;
            // Normally set by GuiManager#openScreen right before the screen is actually displayed; widget
            // initialization (e.g. recipe-viewer-aware widgets) reads this, so it must be set before onResize.
            screen.getContext().setSettings(new UISettings());
            screen.onResize(ClipboardBehavior.PROJECTION_WIDTH, ClipboardBehavior.PROJECTION_HEIGHT);
        } catch (Exception e) {
            GTLog.logger.error("Could not create fake clipboard GUI", e);
        }
    }

    @SideOnly(Side.CLIENT)
    public boolean hasFakeGui() {
        return this.fakeGuiScreen != null;
    }

    /**
     * Draws the projected clipboard page. {@code x}/{@code y} are the normalized (0-1) coordinates the player is
     * looking at on the page, as returned by {@link #checkLookingAt(EntityPlayer)}, and are converted into pixel
     * mouse coordinates within the projected panel the same way {@link #onLeftClick} converts a click.
     */
    @SideOnly(Side.CLIENT)
    public void drawFakeGui(double x, double y, float partialTicks) {
        if (this.fakeGuiScreen == null || this.fakeGuiWrapper == null) return;
        float halfW = ClipboardBehavior.PROJECTION_WIDTH / 2f;
        float halfH = ClipboardBehavior.PROJECTION_HEIGHT / 2f;
        float glScale = 0.5f / Math.max(halfW, halfH);
        int[] mouse = toPixelCoords(x, y);
        GlStateManager.translate(-glScale * halfW, -glScale * halfH, 0);
        GlStateManager.scale(glScale, glScale, 1);
        this.fakeGuiScreen.getContext().updateState(mouse[0], mouse[1], partialTicks);
        // Normally driven by the client tick loop; needed here too so hovered widgets are recalculated for the
        // current mouse position (see #onMousePressed handling in receiveCustomData, which relies on this).
        this.fakeGuiScreen.onFrameUpdate();
        // MUI2's screen drawing (stencil clipping, color/depth masking, ...) is designed for drawing a normal
        // fullscreen GUI, not for being called mid-way through the world's tile entity fast render pass. Saving and
        // restoring the entire GL state around it prevents it from corrupting unrelated world rendering (water,
        // glass, etc.) if it leaves something enabled/disabled that the caller doesn't expect.
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            ClientScreenHandler.drawScreenInternal(this.fakeGuiScreen, this.fakeGuiWrapper, mouse[0], mouse[1],
                    partialTicks);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** Converts normalized (0-1) page coordinates into pixel mouse coordinates within the projected panel. */
    private static int[] toPixelCoords(double x, double y) {
        float halfW = ClipboardBehavior.PROJECTION_WIDTH / 2f;
        float halfH = ClipboardBehavior.PROJECTION_HEIGHT / 2f;
        float scale = 0.5f / Math.max(halfW, halfH);
        int mouseX = (int) ((x / scale) + (halfW > halfH ? 0 : (halfW - halfH)));
        int mouseY = (int) ((y / scale) + (halfH > halfW ? 0 : (halfH - halfW)));
        return new int[] { mouseX, mouseY };
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.itemInventory = new InaccessibleItemStackHandler(this);
    }

    public ItemStack getClipboard() {
        if (this.itemInventory.getStackInSlot(0) == ItemStack.EMPTY) {
            ((InaccessibleItemStackHandler) this.itemInventory).setStackInSlot(0, CLIPBOARD.getStackForm());
        }
        return this.itemInventory.getStackInSlot(0);
    }

    public void initializeClipboard(ItemStack stack) {
        ((InaccessibleItemStackHandler) this.itemInventory).setStackInSlot(0, stack.copy());
        writeCustomData(INIT_CLIPBOARD_NBT, buf -> {
            buf.writeCompoundTag(stack.getTagCompound());
        });
    }

    public void setClipboard(ItemStack stack) {
        ((InaccessibleItemStackHandler) this.itemInventory).setStackInSlot(0, stack.copy());
    }

    @Override
    public void getDrops(@NotNull List<@NotNull ItemStack> dropsList, @Nullable EntityPlayer harvester) {
        dropsList.clear();
        dropsList.add(this.getClipboard());
    }

    @Override
    public float getBlockHardness() {
        return 100;
    }

    @Override
    public int getHarvestLevel() {
        return 4;
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            if (getWorld() != null && !getWorld().isRemote) {
                MetaTileEntityGuiFactory.open(playerIn, this);
            }
        } else {
            breakClipboard(playerIn);
        }
        return true;
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing wrenchSide,
                                 CuboidRayTraceResult hitResult) {
        return false;
    }

    private void breakClipboard(@Nullable EntityPlayer player) {
        if (!getWorld().isRemote) {
            BlockPos pos = this.getPos(); // Saving this for later so it doesn't get mangled
            World world = this.getWorld(); // Same here

            List<ItemStack> drops = new ArrayList<>();
            getDrops(drops, player);

            Block.spawnAsEntity(getWorld(), pos, drops.get(0));
            this.dropAllCovers();
            this.onRemoval();

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
    }

    @Override
    public void onNeighborChanged() {
        if (!getWorld().isRemote && didSetFacing) {
            BlockPos pos = getPos().offset(getFrontFacing());
            IBlockState state = getWorld().getBlockState(pos);
            if (state.getBlock().isAir(state, getWorld(), pos) ||
                    !state.isSideSolid(getWorld(), pos, getFrontFacing())) {
                breakClipboard(null);
            }
        }
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        super.setFrontFacing(frontFacing);
        this.didSetFacing = true;
    }

    @Override
    public String getHarvestTool() {
        return ToolClasses.AXE;
    }

    @Override
    public void addCollisionBoundingBox(List<IndexedCuboid6> collisionList) {
        AxisAlignedBB aabb;
        switch (this.getFrontFacing()) {
            case SOUTH:
                aabb = CLIPBOARD_AABB_SOUTH;
                break;
            case WEST:
                aabb = CLIPBOARD_AABB_WEST;
                break;
            case EAST:
                aabb = CLIPBOARD_AABB_EAST;
                break;
            default:
                aabb = CLIPBOARD_AABB_NORTH;
        }
        collisionList.add(new IndexedCuboid6(null, aabb));
    }

    public IndexedCuboid6 getPageCuboid() {
        AxisAlignedBB aabb;
        switch (this.getFrontFacing()) {
            case SOUTH:
                aabb = PAGE_AABB_SOUTH;
                break;
            case WEST:
                aabb = PAGE_AABB_WEST;
                break;
            case EAST:
                aabb = PAGE_AABB_EAST;
                break;
            default:
                aabb = PAGE_AABB_NORTH;
        }
        return new IndexedCuboid6(null, aabb);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(CLIPBOARD_RENDERER.getParticleTexture(), 0xFFFFFF);
    }

    public Pair<Double, Double> checkLookingAt(EntityPlayer player) {
        if (this.getWorld() != null && player != null) {
            Vec3d startVec = getStartVec(player);
            Vec3d endVec = getEndVec(player);
            CuboidRayTraceResult rayTraceResult = rayTrace(this.getPos(), new Vector3(startVec), new Vector3(endVec),
                    getPageCuboid());
            if (rayTraceResult != null && rayTraceResult.sideHit == this.getFrontFacing().getOpposite()) {
                TileEntity tileEntity = this.getWorld().getTileEntity(rayTraceResult.getBlockPos());
                if (tileEntity instanceof IGregTechTileEntity &&
                        ((IGregTechTileEntity) tileEntity).getMetaTileEntity() instanceof MetaTileEntityClipboard) {
                    double[] pos = handleRayTraceResult(rayTraceResult, this.getFrontFacing().getOpposite());
                    if (pos[0] >= 0 && pos[0] <= 1 && pos[1] >= 0 && pos[1] <= 1)
                        return Pair.of(pos[0], pos[1]);
                }
            }
        }
        return null;
    }

    private static double[] handleRayTraceResult(CuboidRayTraceResult rayTraceResult, EnumFacing spin) {
        double x, y;
        double dX = rayTraceResult.sideHit.getAxis() == EnumFacing.Axis.X ?
                rayTraceResult.hitVec.z - rayTraceResult.getBlockPos().getZ() :
                rayTraceResult.hitVec.x - rayTraceResult.getBlockPos().getX();
        double dY = rayTraceResult.sideHit.getAxis() == EnumFacing.Axis.Y ?
                rayTraceResult.hitVec.z - rayTraceResult.getBlockPos().getZ() :
                rayTraceResult.hitVec.y - rayTraceResult.getBlockPos().getY();
        if (spin == EnumFacing.NORTH) {
            x = 1 - dX;
        } else if (spin == EnumFacing.SOUTH) {
            x = dX;
        } else if (spin == EnumFacing.EAST) {
            x = 1 - dX;
            if (rayTraceResult.sideHit.getXOffset() < 0 || rayTraceResult.sideHit.getZOffset() > 0) {
                x = 1 - x;
            }
        } else {
            x = 1 - dX;
            if (rayTraceResult.sideHit.getXOffset() < 0 || rayTraceResult.sideHit.getZOffset() > 0) {
                x = 1 - x;
            }
        }

        y = 1 - dY; // Since y values are quite weird here

        // Scale these to be 0 - 1
        x -= 3.0 / 16;
        y -= 1.75 / 16;
        x /= 14.0 / 16;
        y /= 14.0 / 16;

        return new double[] { x, y };
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        if (this.getClipboard() != null && this.getClipboard().getTagCompound() != null)
            data.setTag("clipboardNBT", this.getClipboard().getTagCompound());
        else
            data.setTag("clipboardNBT", NO_CLIPBOARD_SIG);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        NBTBase clipboardNBT = data.getTag("clipboardNBT");
        if (clipboardNBT != NO_CLIPBOARD_SIG && clipboardNBT instanceof NBTTagCompound) {
            ItemStack clipboard = this.getClipboard();
            clipboard.setTagCompound((NBTTagCompound) clipboardNBT);
            this.setClipboard(clipboard);
        }
    }

    public void setClipboardNBT(NBTTagCompound data) {
        ItemStack clipboard = this.getClipboard();
        clipboard.setTagCompound(data);
        this.setClipboard(clipboard);
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        if (this.getClipboard() != null && this.getClipboard().getTagCompound() != null)
            buf.writeCompoundTag(this.getClipboard().getTagCompound());
        else {
            buf.writeCompoundTag(new NBTTagCompound());
        }
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        try {
            NBTTagCompound clipboardNBT = buf.readCompoundTag();
            if (clipboardNBT != null && !clipboardNBT.equals(new NBTTagCompound())) {
                ItemStack clipboard = this.getClipboard();
                clipboard.setTagCompound(clipboardNBT);
                this.setClipboard(clipboard);
            }
        } catch (Exception e) {
            GTLog.logger.error("Could not initialize Clipboard from InitialSyncData buffer", e);
        }
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == CREATE_FAKE_UI) {
            if (getWorld().isRemote) {
                createFakeGui();
            }
            this.scheduleRenderUpdate();
        } else if (dataId == MOUSE_POSITION) {
            int mouseX = buf.readVarInt();
            int mouseY = buf.readVarInt();
            if (getWorld().isRemote && hasFakeGui()) {
                this.fakeGuiScreen.getContext().updateState(mouseX, mouseY, 0);
                this.fakeGuiScreen.onFrameUpdate(); // recalculate hovered widgets for this mouse position
                this.fakeGuiScreen.onMousePressed(0); // Left mouse button
                this.fakeGuiScreen.onMouseRelease(0);
                this.scheduleRenderUpdate();
                this.sendNBTToServer();
            }
        } else if (dataId == INIT_CLIPBOARD_NBT) {
            try {
                NBTTagCompound clipboardNBT = buf.readCompoundTag();
                if (clipboardNBT != NO_CLIPBOARD_SIG) {
                    ItemStack clipboard = this.getClipboard();
                    clipboard.setTagCompound(clipboardNBT);
                    this.setClipboard(clipboard);
                }
            } catch (Exception e) {
                GTLog.logger.error("Could not read Clipboard Init NBT from CustomData buffer", e);
            }
        } else if (dataId == SYNC_CLIPBOARD_NBT) {
            try {
                NBTTagCompound clipboardNBT = buf.readCompoundTag();
                setClipboardNBT(clipboardNBT);
                if (getWorld().isRemote) {
                    createFakeGui();
                }
            } catch (Exception e) {
                GTLog.logger.error("Could not sync Clipboard NBT from CustomData buffer", e);
            }
        }
    }

    private void sendNBTToServer() {
        PacketBuffer packetBuffer = new PacketBuffer(Unpooled.buffer());
        packetBuffer.writeCompoundTag(this.getClipboard().getTagCompound());
        GregTechAPI.networkHandler.sendToServer(new PacketClipboardNBTUpdate(
                this.getWorld().provider.getDimension(),
                this.getPos(),
                1, packetBuffer));
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) { // JEI shouldn't show this
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {}

    @Override
    public void onLeftClick(EntityPlayer player, EnumFacing facing, CuboidRayTraceResult hitResult) {
        if (this.getWorld().isRemote) return;
        Pair<Double, Double> clickCoords = this.checkLookingAt(player);
        if (clickCoords == null) return;
        int[] mouse = toPixelCoords(clickCoords.getLeft(), clickCoords.getRight());
        if (0 <= mouse[0] && mouse[0] <= ClipboardBehavior.PROJECTION_WIDTH &&
                0 <= mouse[1] && mouse[1] <= ClipboardBehavior.PROJECTION_HEIGHT) {
            this.writeCustomData(MOUSE_POSITION, buf -> {
                buf.writeVarInt(mouse[0]);
                buf.writeVarInt(mouse[1]);
            });
        }
    }

    @Override
    public boolean canPlaceCoverOnSide(@NotNull EnumFacing side) {
        return false;
    }

    @Override
    public boolean acceptsCovers() {
        return false;
    }

    @Override
    public boolean canRenderMachineGrid(@NotNull ItemStack mainHandStack, @NotNull ItemStack offHandStack) {
        return false;
    }

    @Override
    public boolean showToolUsages() {
        return false;
    }

    @Override
    public ItemStack getPickItem(EntityPlayer player) {
        return this.getClipboard();
    }

    @NotNull
    @Override
    public SoundType getSoundType() {
        return SoundType.WOOD;
    }
}
