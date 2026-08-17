package gregtech.common.items.behaviors;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntityClipboard;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import codechicken.lib.raytracer.RayTracer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.value.IIntValue;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import gregtech.common.mui.widget.GTTextFieldWidget;

import static gregtech.common.metatileentities.MetaTileEntities.CLIPBOARD_TILE;

public class ClipboardBehavior implements IItemBehaviour, ItemUIFactory {

    public static final int MAX_PAGES = 25;
    private static final int TEXT_COLOR = 0x1E1E1E;

    /** Size of the read-mostly panel projected onto a placed clipboard. See {@link #buildDisplayPanel(ItemStack)}. */
    public static final int PROJECTION_WIDTH = 170, PROJECTION_HEIGHT = 238;

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ItemStack stack = guiData.getUsedItemStack();
        return buildEditablePanel(GTGuis.createPanel(stack, 186, 263), stack, syncManager);
    }

    /**
     * Builds the fully editable clipboard panel (title/tasks are text fields). Used both when the item is opened
     * from hand and when the placed clipboard is opened with a non-sneaking right click.
     */
    public static ModularPanel buildEditablePanel(ModularPanel panel, ItemStack stack, PanelSyncManager syncManager) {
        initNBT(stack);

        panel.background(GTGuiTextures.CLIPBOARD_BACKGROUND).disableHoverBackground();

        IntSyncValue pageIndexValue = new IntSyncValue(() -> getPageNum(stack), page -> setPageNum(stack, page));
        syncManager.syncValue("page_index", pageIndexValue);

        panel.child(new GTTextFieldWidget()
                .pos(28, 28).size(130, 12)
                .background(GTGuiTextures.CLIPBOARD_TEXT_BOX)
                .disableHoverBackground()
                .value(new StringSyncValue(() -> getTitle(stack), val -> setTitle(stack, val)))
                .setMaxLength(25)
                .setTextColor(TEXT_COLOR));

        for (int i = 0; i < 8; i++) {
            int finalI = i;
            panel.child(createTaskCheckButton(14, 55 + 22 * i,
                    new IntSyncValue(() -> getButtonState(stack, finalI), state -> setButton(stack, finalI, state))));

            panel.child(new GTTextFieldWidget()
                    .pos(32, 58 + 22 * i).size(140, 12)
                    .background(GTGuiTextures.CLIPBOARD_TEXT_BOX)
                    .disableHoverBackground()
                    .value(new StringSyncValue(() -> getString(stack, finalI), val -> setString(stack, finalI, val)))
                    .setMaxLength(23)
                    .setTextColor(TEXT_COLOR));
        }

        panel.child(new ButtonWidget<>()
                .pos(38, 231).size(16, 16)
                .background(GTGuiTextures.BUTTON_LEFT)
                .disableHoverBackground()
                .onMousePressed(mouseButton -> {
                    incrPageNum(pageIndexValue, Interactable.hasShiftDown() ? -10 : -1);
                    return true;
                }));
        panel.child(new ButtonWidget<>()
                .pos(132, 231).size(16, 16)
                .background(GTGuiTextures.BUTTON_RIGHT)
                .disableHoverBackground()
                .onMousePressed(mouseButton -> {
                    incrPageNum(pageIndexValue, Interactable.hasShiftDown() ? 10 : 1);
                    return true;
                }));
        panel.child(IKey.dynamic(() -> (pageIndexValue.getIntValue() + 1) + " / " + MAX_PAGES)
                .asWidget()
                .pos(93, 240));

        return panel;
    }

    /**
     * Builds the read-mostly clipboard panel that is projected in world space onto the placed clipboard block (see
     * {@link MetaTileEntityClipboard}). Only the cycle buttons and page navigation are interactive; title/tasks are
     * plain text. This is rendered headlessly and never goes through the normal MUI2 sync machinery (there is no
     * player actually viewing this panel through a container), so widget values read/write the item NBT directly
     * instead of going through sync values.
     */
    public static ModularPanel buildDisplayPanel(ItemStack stack) {
        initNBT(stack);

        ModularPanel panel = GTGuis.createPanel("clipboard_projection", PROJECTION_WIDTH, PROJECTION_HEIGHT)
                .background(GTGuiTextures.CLIPBOARD_PAPER_BACKGROUND)
                .disableHoverBackground();

        panel.child(new Widget<>()
                .pos(18, 8).size(130, 14)
                .background(GTGuiTextures.CLIPBOARD_TEXT_BOX));
        panel.child(IKey.dynamic(() -> getTitle(stack)).color(TEXT_COLOR).asWidget().pos(20, 10));

        for (int i = 0; i < 8; i++) {
            int finalI = i;
            panel.child(createTaskCheckButton(6, 37 + 20 * i, new IntValue.Dynamic(
                    () -> getButtonState(stack, finalI), state -> setButton(stack, finalI, state))));

            panel.child(new Widget<>()
                    .pos(22, 38 + 20 * i).size(140, 12)
                    .background(GTGuiTextures.CLIPBOARD_TEXT_BOX));
            panel.child(IKey.dynamic(() -> getString(stack, finalI)).color(TEXT_COLOR).asWidget()
                    .pos(24, 40 + 20 * i));
        }

        panel.child(new ButtonWidget<>()
                .pos(30, 200).size(16, 16)
                .background(GTGuiTextures.BUTTON_LEFT)
                .disableHoverBackground()
                .onMousePressed(mouseButton -> {
                    setPageNum(stack, getPageNum(stack) + (Interactable.hasShiftDown() ? -10 : -1));
                    return true;
                }));
        panel.child(new ButtonWidget<>()
                .pos(124, 200).size(16, 16)
                .background(GTGuiTextures.BUTTON_RIGHT)
                .disableHoverBackground()
                .onMousePressed(mouseButton -> {
                    setPageNum(stack, getPageNum(stack) + (Interactable.hasShiftDown() ? 10 : 1));
                    return true;
                }));
        panel.child(IKey.dynamic(() -> (getPageNum(stack) + 1) + " / " + MAX_PAGES).color(TEXT_COLOR).asWidget()
                .pos(85, 208));

        return panel;
    }

    /** The checkbox-like cycle button (unchecked/checked/in-progress/blocked) used for a single task row. */
    private static CycleButtonWidget createTaskCheckButton(int x, int y, IIntValue<?> value) {
        return new CycleButtonWidget()
                .pos(x, y).size(15, 15)
                .length(4)
                .stateBackground(GTGuiTextures.CLIPBOARD_BUTTON)
                .disableThemeBackground(true)
                .disableHoverThemeBackground(true)
                .disableHoverBackground()
                .value(value);
    }

    private static NBTTagCompound getPageCompound(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack)) return null;
        short pageNum = stack.getTagCompound().getShort("PageIndex");
        return stack.getTagCompound().getCompoundTag("Page" + pageNum);
    }

    private static void setPageCompound(ItemStack stack, NBTTagCompound pageCompound) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        short pageNum = stack.getTagCompound().getShort("PageIndex");
        stack.getTagCompound().setTag("Page" + pageNum, pageCompound);
    }

    private static void initNBT(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
            tagCompound.setShort("PageIndex", (short) 0);
            tagCompound.setShort("TotalPages", (short) 0);

            NBTTagCompound pageCompound = new NBTTagCompound();
            pageCompound.setShort("ButStat", (short) 0);
            pageCompound.setString("Title", "");
            for (int i = 0; i < 8; i++) {
                pageCompound.setString("Task" + i, "");
            }

            for (int i = 0; i < MAX_PAGES; i++) {
                tagCompound.setTag("Page" + i, pageCompound.copy());
            }

            stack.setTagCompound(tagCompound);
        }
    }

    private static void setButton(ItemStack stack, int pos, int newState) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = getPageCompound(stack);
        short buttonState;
        buttonState = tagCompound.getShort("ButStat");

        short clearedState = (short) (buttonState & ~(3 << (pos * 2))); // Clear out the desired slot
        buttonState = (short) (clearedState | (newState << (pos * 2))); // And add the new state back in

        tagCompound.setShort("ButStat", buttonState);
        setPageCompound(stack, tagCompound);
    }

    private static int getButtonState(ItemStack stack, int pos) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return 0;
        NBTTagCompound tagCompound = getPageCompound(stack);
        short buttonState;
        buttonState = tagCompound.getShort("ButStat");
        return ((buttonState >> pos * 2) & 3);
    }

    private static void setString(ItemStack stack, int pos, String newString) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = getPageCompound(stack);
        tagCompound.setString("Task" + pos, newString);
        setPageCompound(stack, tagCompound);
    }

    private static String getString(ItemStack stack, int pos) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return "";
        NBTTagCompound tagCompound = getPageCompound(stack);
        return tagCompound.getString("Task" + pos);
    }

    private static void setTitle(ItemStack stack, String newString) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = getPageCompound(stack);
        assert tagCompound != null;
        tagCompound.setString("Title", newString);
        setPageCompound(stack, tagCompound);
    }

    private static String getTitle(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return "";
        NBTTagCompound tagCompound = getPageCompound(stack);
        return tagCompound.getString("Title");
    }

    private static int getPageNum(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return 1;
        NBTTagCompound tagCompound = stack.getTagCompound();
        return tagCompound.getInteger("PageIndex");
    }

    private static void setPageNum(ItemStack stack, int page) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = stack.getTagCompound();
        assert tagCompound != null;
        tagCompound.setInteger("PageIndex", Math.max(Math.min(page, MAX_PAGES - 1), 0));
        stack.setTagCompound(tagCompound);
    }

    private static void incrPageNum(IntSyncValue pageIndexValue, int increment) {
        int next = Math.max(Math.min(pageIndexValue.getIntValue() + increment, MAX_PAGES - 1), 0);
        pageIndexValue.setIntValue(next, true, true);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote && RayTracer.retrace(player).typeOfHit != RayTraceResult.Type.BLOCK) { // So that the player
                                                                                                   // doesn't place a
                                                                                                   // clipboard before
                                                                                                   // suddenly getting
                                                                                                   // the GUI
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    @Override
    public ActionResult<ItemStack> onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                             EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && facing.getAxis() != EnumFacing.Axis.Y) {
            ItemStack heldItem = player.getHeldItem(hand).copy();
            heldItem.setCount(1); // don't place multiple items at a time
            // Make sure it's the right block
            IBlockState testState = world.getBlockState(pos);
            Block testBlock = testState.getBlock();
            if (!testBlock.isAir(world.getBlockState(pos), world, pos) && testState.isSideSolid(world, pos, facing)) {
                // Step away from the block so that you don't replace it, and then give it our fun blockstate
                BlockPos shiftedPos = pos.offset(facing);
                Block shiftedBlock = world.getBlockState(shiftedPos).getBlock();
                if (shiftedBlock.isAir(world.getBlockState(shiftedPos), world, shiftedPos)) {
                    IBlockState state = GregTechAPI.mteManager.getRegistry(GTValues.MODID).getBlock().getDefaultState();
                    world.setBlockState(shiftedPos, state);
                    // Get new TE
                    shiftedBlock.createTileEntity(world, state);
                    // And manipulate it to our liking
                    IGregTechTileEntity holder = (IGregTechTileEntity) world.getTileEntity(shiftedPos);
                    if (holder != null) {
                        MetaTileEntityClipboard clipboard = (MetaTileEntityClipboard) holder
                                .setMetaTileEntity(CLIPBOARD_TILE);
                        if (clipboard != null) {
                            clipboard.initializeClipboard(heldItem);
                            clipboard.setFrontFacing(facing.getOpposite());
                            ItemStack returnedStack = player.getHeldItem(hand);
                            if (!player.isCreative()) {
                                returnedStack.setCount(player.getHeldItem(hand).getCount() - 1);
                            }
                            return ActionResult.newResult(EnumActionResult.SUCCESS, returnedStack);
                        }
                    }
                }
            }
        }
        return ActionResult.newResult(EnumActionResult.PASS, player.getHeldItem(hand));
    }
}
