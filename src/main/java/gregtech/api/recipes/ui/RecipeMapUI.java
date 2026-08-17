package gregtech.api.recipes.ui;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.common.mui.widget.GTFluidSlot;
import gregtech.integration.IntegrationModule;
import gregtech.integration.jei.JustEnoughItemsModule;
import gregtech.integration.jei.recipe.RecipeMapCategory;
import gregtech.modules.GregTechModules;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.DoubleSupplier;

@ApiStatus.Experimental
public class RecipeMapUI<R extends RecipeMap<?>> {

    private final Byte2ObjectMap<TextureArea> slotOverlays = new Byte2ObjectOpenHashMap<>();
    private final Byte2ObjectMap<UITexture> slotOverlaysMui2 = new Byte2ObjectOpenHashMap<>();

    private final R recipeMap;
    private final boolean modifyItemInputs;
    private final boolean modifyItemOutputs;
    private final boolean modifyFluidInputs;
    private final boolean modifyFluidOutputs;

    private final boolean isGenerator;

    private TextureArea progressBarTexture = GuiTextures.PROGRESS_BAR_ARROW;
    private ProgressWidget.MoveType moveType = ProgressWidget.MoveType.HORIZONTAL;
    private @Nullable TextureArea specialTexture;
    private int @Nullable [] specialTexturePosition;

    // MUI2 equivalent of progressBarTexture. Kept as a separate field since MUI1's TextureArea and MUI2's
    // UITexture are not interconvertible (TextureArea only stores relative 0.0-1.0 UV coordinates).
    private @NotNull UITexture progressBarTextureMui2 = GTGuiTextures.PROGRESS_BAR_ARROW;

    private boolean isJEIVisible = true;

    /**
     * @param recipeMap          the recipemap corresponding to this ui
     * @param modifyItemInputs   if item input amounts can be modified
     * @param modifyItemOutputs  if item output amounts can be modified
     * @param modifyFluidInputs  if fluid input amounts can be modified
     * @param modifyFluidOutputs if fluid output amounts can be modified
     */
    public RecipeMapUI(@NotNull R recipeMap, boolean modifyItemInputs, boolean modifyItemOutputs,
                       boolean modifyFluidInputs, boolean modifyFluidOutputs, boolean isGenerator) {
        this.recipeMap = recipeMap;
        this.modifyItemInputs = modifyItemInputs;
        this.modifyItemOutputs = modifyItemOutputs;
        this.modifyFluidInputs = modifyFluidInputs;
        this.modifyFluidOutputs = modifyFluidOutputs;
        this.isGenerator = isGenerator;
    }

    /**
     * Compute the storage key for slot overlays.
     *
     * @param isOutput if the slot is an output slot
     * @param isFluid  if the slot is a fluid slot
     * @param isLast   if the slot is the last slot of its type
     * @return the key
     */
    @ApiStatus.Internal
    public static byte computeOverlayKey(boolean isOutput, boolean isFluid, boolean isLast) {
        return (byte) ((isOutput ? 2 : 0) + (isFluid ? 1 : 0) + (isLast ? 4 : 0));
    }

    /**
     * Determines the slot grid for an item input amount
     *
     * @param itemInputsCount the item input amount
     * @return [slots to the left, slots downwards]
     */
    @Contract("_ -> new")
    public static int @NotNull [] determineSlotsGrid(int itemInputsCount) {
        int itemSlotsToLeft;
        int itemSlotsToDown;
        double sqrt = Math.sqrt(itemInputsCount);
        // if the number of input has an integer root
        // return it.
        if (sqrt % 1 == 0) {
            itemSlotsToLeft = (int) sqrt;
            itemSlotsToDown = itemSlotsToLeft;
        } else if (itemInputsCount == 3) {
            itemSlotsToLeft = 3;
            itemSlotsToDown = 1;
        } else {
            // if we couldn't fit all into a perfect square,
            // increase the amount of slots to the left
            itemSlotsToLeft = (int) Math.ceil(sqrt);
            itemSlotsToDown = itemSlotsToLeft - 1;
            // if we still can't fit all the slots in a grid,
            // increase the amount of slots on the bottom
            if (itemInputsCount > itemSlotsToLeft * itemSlotsToDown) {
                itemSlotsToDown = itemSlotsToLeft;
            }
        }
        return new int[] { itemSlotsToLeft, itemSlotsToDown };
    }

    /**
     * MUI2 equivalent of the old MUI1 createUITemplate. This DOES NOT include machine control widgets or bind the
     * player inventory. <br>
     * Note: slot overlays set via {@link #setItemSlotOverlay} / {@link #setFluidSlotOverlay} are MUI1-only
     * ({@link TextureArea}) and are not applied here; only the base slot/fluid-slot background is used.
     *
     * @param progressSupplier a supplier for the progress bar
     * @param importItems      the input item inventory
     * @param exportItems      the output item inventory
     * @param importFluids     the input fluid inventory
     * @param exportFluids     the output fluid inventory
     * @return the populated parent widget
     */
    public @NotNull ParentWidget<?> buildUITemplate(@NotNull DoubleSupplier progressSupplier,
                                                    @NotNull IItemHandlerModifiable importItems,
                                                    @NotNull IItemHandlerModifiable exportItems,
                                                    @NotNull FluidTankList importFluids,
                                                    @NotNull FluidTankList exportFluids) {
        ParentWidget<?> parent = new ParentWidget<>();
        parent.child(buildProgressWidgetMui2(progressSupplier));
        addInventorySlotGroupMui2(parent, importItems, importFluids, false);
        addInventorySlotGroupMui2(parent, exportItems, exportFluids, true);
        return parent;
    }

    /**
     * MUI2 equivalent of {@link #createUITemplateNoOutputs}.
     *
     * @param progressSupplier a supplier for the progress bar
     * @param importItems      the input item inventory
     * @param importFluids     the input fluid inventory
     * @return the populated parent widget
     */
    public @NotNull ParentWidget<?> buildUITemplateNoOutputs(@NotNull DoubleSupplier progressSupplier,
                                                             @NotNull IItemHandlerModifiable importItems,
                                                             @NotNull FluidTankList importFluids) {
        ParentWidget<?> parent = new ParentWidget<>();
        parent.child(buildProgressWidgetMui2(progressSupplier));
        addInventorySlotGroupMui2(parent, importItems, importFluids, false);
        return parent;
    }

    private RecipeMapProgressWidgetMui2 buildProgressWidgetMui2(@NotNull DoubleSupplier progressSupplier) {
        // ProgressWidget's fluent setters return the non-generic base type, so the chain can't be
        // typed as RecipeMapProgressWidgetMui2; apply it to the instance directly instead.
        RecipeMapProgressWidgetMui2 widget = new RecipeMapProgressWidgetMui2();
        widget.value(new DoubleSyncValue(progressSupplier))
                .pos(78, 23)
                .size(20, 20)
                .texture(progressBarTextureMui2, 20)
                .direction(toMui2Direction(moveType));
        return widget;
    }

    /**
     * Creates a MUI2 progress bar widget that opens JEI to this recipe map's categories when clicked, same as
     * {@link #buildProgressWidgetMui2}, but without any default position, size, or texture. Intended for machines
     * that build their own custom layout instead of using {@link #buildUITemplate}, so they can still expose the
     * click-to-open-JEI behavior on their progress bar.
     *
     * @param progressSupplier a supplier for the progress bar's value
     * @return the JEI-aware progress widget, pre-configured only with its value
     */
    public @NotNull com.cleanroommc.modularui.widgets.ProgressWidget createJeiProgressWidget(
                                                                                              @NotNull DoubleSupplier progressSupplier) {
        RecipeMapProgressWidgetMui2 widget = new RecipeMapProgressWidgetMui2();
        widget.value(new DoubleSyncValue(progressSupplier));
        return widget;
    }

    /**
     * MUI2 equivalent of {@link gregtech.api.gui.widgets.RecipeProgressWidget}. Clicking it opens JEI to this
     * recipe map's categories, same as the MUI1 progress bar.
     */
    private class RecipeMapProgressWidgetMui2 extends com.cleanroommc.modularui.widgets.ProgressWidget
                                              implements Interactable {

        private RecipeMapProgressWidgetMui2() {
            tooltipAutoUpdate(true);
            tooltipBuilder(t -> t.addLine(IKey.lang("gui.widget.recipeProgressWidget.default_tooltip")));
        }

        @Override
        public @NotNull Result onMousePressed(int mouseButton) {
            if (!GregTechAPI.moduleManager.isModuleEnabled(GregTechModules.MODULE_JEI)) {
                return Result.IGNORE;
            }
            List<String> categoryID = new ArrayList<>();
            if (recipeMap == RecipeMaps.FURNACE_RECIPES) {
                // furnace recipes are never registered as GT recipe categories (they're converted from vanilla
                // smelting recipes on the fly), so there's no RecipeMapCategory to fall back on here.
                categoryID.add("minecraft.smelting");
            } else {
                Collection<RecipeMapCategory> categories = RecipeMapCategory.getCategoriesFor(recipeMap);
                if (categories == null || categories.isEmpty()) {
                    return Result.IGNORE;
                }
                for (RecipeMapCategory category : categories) {
                    categoryID.add(category.getUid());
                }
            }
            if (JustEnoughItemsModule.jeiRuntime == null) {
                IntegrationModule.logger.error("GTCEu JEI integration has crashed, this is not a good thing");
                return Result.IGNORE;
            }
            JustEnoughItemsModule.jeiRuntime.getRecipesGui().showCategories(categoryID);
            return Result.SUCCESS;
        }
    }

    /**
     * @param parent       the parent widget to add to
     * @param itemHandler  the item handler to use
     * @param fluidHandler the fluid handler to use
     * @param isOutputs    if slots should be output slots
     */
    protected void addInventorySlotGroupMui2(@NotNull ParentWidget<?> parent,
                                             @NotNull IItemHandlerModifiable itemHandler,
                                             @NotNull FluidTankList fluidHandler, boolean isOutputs) {
        int itemInputsCount = itemHandler.getSlots();
        int fluidInputsCount = fluidHandler.getTanks();
        boolean invertFluids = false;
        if (itemInputsCount == 0) {
            int tmp = itemInputsCount;
            itemInputsCount = fluidInputsCount;
            fluidInputsCount = tmp;
            invertFluids = true;
        }
        int[] inputSlotGrid = determineSlotsGrid(itemInputsCount);
        int itemSlotsToLeft = inputSlotGrid[0];
        int itemSlotsToDown = inputSlotGrid[1];
        int startInputsX = isOutputs ? 106 : 70 - itemSlotsToLeft * 18;
        int startInputsY = 33 - (int) (itemSlotsToDown / 2.0 * 18);
        boolean wasGroup = itemHandler.getSlots() + fluidHandler.getTanks() == 12;
        if (wasGroup) startInputsY -= 9;
        else if (itemHandler.getSlots() >= 6 && fluidHandler.getTanks() >= 2 && !isOutputs) startInputsY -= 9;
        for (int i = 0; i < itemSlotsToDown; i++) {
            for (int j = 0; j < itemSlotsToLeft; j++) {
                int slotIndex = i * itemSlotsToLeft + j;
                if (slotIndex >= itemInputsCount) break;
                int x = startInputsX + 18 * j;
                int y = startInputsY + 18 * i;
                addSlotMui2(parent, x, y, slotIndex, itemHandler, fluidHandler, invertFluids, isOutputs);
            }
        }
        if (wasGroup) startInputsY += 2;
        if (fluidInputsCount > 0 || invertFluids) {
            if (itemSlotsToDown >= fluidInputsCount && itemSlotsToLeft < 3) {
                int startSpecX = isOutputs ? startInputsX + itemSlotsToLeft * 18 : startInputsX - 18;
                for (int i = 0; i < fluidInputsCount; i++) {
                    int y = startInputsY + 18 * i;
                    addSlotMui2(parent, startSpecX, y, i, itemHandler, fluidHandler, !invertFluids, isOutputs);
                }
            } else {
                int startSpecY = startInputsY + itemSlotsToDown * 18;
                for (int i = 0; i < fluidInputsCount; i++) {
                    int x = isOutputs ? startInputsX + 18 * (i % 3) :
                            startInputsX + itemSlotsToLeft * 18 - 18 - 18 * (i % 3);
                    int y = startSpecY + (i / 3) * 18;
                    addSlotMui2(parent, x, y, i, itemHandler, fluidHandler, !invertFluids, isOutputs);
                }
            }
        }
    }

    /**
     * MUI2 equivalent of {@link #addSlot}.
     *
     * @param parent       the parent widget to add to
     * @param x            the x coordinate of the slot
     * @param y            the y coordinate of the slot
     * @param slotIndex    the slot index of the slot
     * @param itemHandler  the item handler to use
     * @param fluidHandler the fluid handler to use
     * @param isFluid      if the slot is a fluid slot
     * @param isOutputs    if slots should be output slots
     */
    protected void addSlotMui2(@NotNull ParentWidget<?> parent, int x, int y, int slotIndex,
                               @NotNull IItemHandlerModifiable itemHandler, @NotNull FluidTankList fluidHandler,
                               boolean isFluid, boolean isOutputs) {
        if (!isFluid) {
            parent.child(new ItemSlot()
                    .pos(x, y)
                    .background(getOverlaysForSlotMui2(isOutputs, false, slotIndex == itemHandler.getSlots() - 1))
                    .slot(new ModularSlot(itemHandler, slotIndex).accessibility(!isOutputs, true)));
        } else {
            parent.child(new GTFluidSlot()
                    .pos(x, y)
                    .size(18)
                    .background(getOverlaysForSlotMui2(isOutputs, true, slotIndex == fluidHandler.getTanks() - 1))
                    .syncHandler(GTFluidSlot.sync(fluidHandler.getTankAt(slotIndex))
                            .showAmount(true, true)
                            .accessibility(!isOutputs, true)));
        }
    }

    /**
     * Converts a MUI1 {@link ProgressWidget.MoveType} to its MUI2
     * {@link com.cleanroommc.modularui.widgets.ProgressWidget.Direction} equivalent.
     *
     * @param moveType the MUI1 move type
     * @return the corresponding MUI2 direction
     */
    public static com.cleanroommc.modularui.widgets.ProgressWidget.@NotNull Direction toMui2Direction(
                                                                                                        @NotNull ProgressWidget.MoveType moveType) {
        return switch (moveType) {
            case HORIZONTAL -> com.cleanroommc.modularui.widgets.ProgressWidget.Direction.RIGHT;
            case HORIZONTAL_BACKWARDS -> com.cleanroommc.modularui.widgets.ProgressWidget.Direction.LEFT;
            case VERTICAL, VERTICAL_INVERTED -> com.cleanroommc.modularui.widgets.ProgressWidget.Direction.UP;
            case VERTICAL_DOWNWARDS -> com.cleanroommc.modularui.widgets.ProgressWidget.Direction.DOWN;
            case CIRCULAR -> com.cleanroommc.modularui.widgets.ProgressWidget.Direction.CIRCULAR_CW;
        };
    }

    /**
     * MUI2 equivalent of the old MUI1 getOverlaysForSlot.
     *
     * @param isOutput if the slot is an output slot
     * @param isFluid  if the slot is a fluid slot
     * @param isLast   if the slot is the last slot of its type
     * @return the overlays for a slot
     */
    protected UITexture @NotNull [] getOverlaysForSlotMui2(boolean isOutput, boolean isFluid, boolean isLast) {
        UITexture base = isFluid ? GTGuiTextures.FLUID_SLOT : GTGuiTextures.SLOT;
        byte overlayKey = computeOverlayKey(isOutput, isFluid, isLast);
        if (slotOverlaysMui2.containsKey(overlayKey)) {
            return new UITexture[] { base, slotOverlaysMui2.get(overlayKey) };
        }
        return new UITexture[] { base };
    }

    /**
     * @return the height used to determine size of background texture in JEI
     */
    public int getPropertyHeightShift() {
        int maxPropertyCount = 0;
        if (shouldShiftWidgets()) {
            for (Recipe recipe : recipeMap.getRecipeList()) {
                int count = recipe.propertyStorage().size();
                if (count > maxPropertyCount) {
                    maxPropertyCount = count;
                }
            }
        }
        return maxPropertyCount * 10; // GTRecipeWrapper#LINE_HEIGHT
    }

    /**
     * @return widgets should be shifted
     */
    private boolean shouldShiftWidgets() {
        return recipeMap.getMaxInputs() + recipeMap.getMaxOutputs() >= 6 ||
                recipeMap.getMaxFluidInputs() + recipeMap.getMaxFluidOutputs() >= 6;
    }

    /**
     * @return the progress bar's move type
     */
    public @NotNull ProgressWidget.MoveType progressBarMoveType() {
        return moveType;
    }

    /**
     * @param moveType the new progress bar move type
     */
    public void setProgressBarMoveType(@NotNull ProgressWidget.MoveType moveType) {
        this.moveType = moveType;
    }

    /**
     * @return the texture of the progress bar
     */
    public @NotNull TextureArea progressBarTexture() {
        return progressBarTexture;
    }

    /**
     * @param progressBarTexture the new progress bar texture
     */
    public void setProgressBarTexture(@NotNull TextureArea progressBarTexture) {
        this.progressBarTexture = progressBarTexture;
    }

    /**
     * @param progressBarTexture the new progress bar texture
     * @param moveType           the new progress bar move type
     */
    public void setProgressBar(@NotNull TextureArea progressBarTexture, @NotNull ProgressWidget.MoveType moveType) {
        this.progressBarTexture = progressBarTexture;
        this.moveType = moveType;
    }

    /**
     * @param progressBarTexture     the new progress bar texture
     * @param progressBarTextureMui2 the new MUI2 progress bar texture
     * @param moveType               the new progress bar move type
     */
    public void setProgressBar(@NotNull TextureArea progressBarTexture, @NotNull UITexture progressBarTextureMui2,
                               @NotNull ProgressWidget.MoveType moveType) {
        this.progressBarTexture = progressBarTexture;
        this.progressBarTextureMui2 = progressBarTextureMui2;
        this.moveType = moveType;
    }

    /**
     * @return the MUI2 texture of the progress bar
     */
    public @NotNull UITexture progressBarTextureMui2() {
        return progressBarTextureMui2;
    }

    /**
     * @param progressBarTextureMui2 the new MUI2 progress bar texture
     */
    public void setProgressBarTextureMui2(@NotNull UITexture progressBarTextureMui2) {
        this.progressBarTextureMui2 = progressBarTextureMui2;
    }

    /**
     * @param specialTexture the special texture to set
     * @param x              the x coordinate of the texture
     * @param y              the y coordinate of the texture
     * @param width          the width of the texture
     * @param height         the height of the texture
     */
    public void setSpecialTexture(@NotNull TextureArea specialTexture, int x, int y, int width, int height) {
        setSpecialTexture(specialTexture, new int[] { x, y, width, height });
    }

    /**
     * @param specialTexture the special texture to set
     * @param position       the position of the texture: [x, y, width, height]
     */
    public void setSpecialTexture(@NotNull TextureArea specialTexture, int @NotNull [] position) {
        this.specialTexture = specialTexture;
        this.specialTexturePosition = position;
    }

    /**
     * @return the special texture
     */
    public @Nullable TextureArea specialTexture() {
        return this.specialTexture;
    }

    /**
     * @return the special texture's position
     */
    public int @Nullable @UnmodifiableView [] specialTexturePosition() {
        return this.specialTexturePosition;
    }

    /**
     * @return if this ui should be visible in JEI
     */
    public boolean isJEIVisible() {
        return isJEIVisible;
    }

    /**
     * @param isJEIVisible if the ui should be visible in JEI
     */
    public void setJEIVisible(boolean isJEIVisible) {
        this.isJEIVisible = isJEIVisible;
    }

    /**
     * @return if item input slot amounts can be modified
     */
    public boolean canModifyItemInputs() {
        return modifyItemInputs;
    }

    /**
     * @return if item output slot amounts can be modified
     */
    public boolean canModifyItemOutputs() {
        return modifyItemOutputs;
    }

    /**
     * @return if fluid input slot amounts can be modified
     */
    public boolean canModifyFluidInputs() {
        return modifyFluidInputs;
    }

    /**
     * @return if fluid output slot amounts can be modified
     */
    public boolean canModifyFluidOutputs() {
        return modifyFluidOutputs;
    }

    /**
     * @return if this UI represents an energy generating recipemap
     */
    public boolean isGenerator() {
        return isGenerator;
    }

    /**
     * @param texture  the texture to set
     * @param isOutput if the slot is an output slot
     */
    public void setItemSlotOverlay(@NotNull TextureArea texture, boolean isOutput) {
        this.slotOverlays.put(computeOverlayKey(isOutput, false, false), texture);
        this.slotOverlays.put(computeOverlayKey(isOutput, false, true), texture);
    }

    /**
     * @param texture    the texture to set
     * @param isOutput   if the slot is an output slot
     * @param isLastSlot if the slot is the last slot
     */
    public void setItemSlotOverlay(@NotNull TextureArea texture, boolean isOutput, boolean isLastSlot) {
        this.slotOverlays.put(computeOverlayKey(isOutput, false, isLastSlot), texture);
    }

    /**
     * @param texture  the texture to set
     * @param isOutput if the slot is an output slot
     */
    public void setFluidSlotOverlay(@NotNull TextureArea texture, boolean isOutput) {
        this.slotOverlays.put(computeOverlayKey(isOutput, true, false), texture);
        this.slotOverlays.put(computeOverlayKey(isOutput, true, true), texture);
    }

    /**
     * @param texture    the texture to set
     * @param isOutput   if the slot is an output slot
     * @param isLastSlot if the slot is the last slot
     */
    public void setFluidSlotOverlay(@NotNull TextureArea texture, boolean isOutput, boolean isLastSlot) {
        this.slotOverlays.put(computeOverlayKey(isOutput, true, isLastSlot), texture);
    }

    /**
     * @param key     the key to store the slot's texture with
     * @param texture the texture to store
     */
    @ApiStatus.Internal
    public void setSlotOverlay(byte key, @NotNull TextureArea texture) {
        this.slotOverlays.put(key, texture);
    }

    /**
     * MUI2 equivalent of {@link #setItemSlotOverlay(TextureArea, boolean)}.
     *
     * @param texture  the MUI2 texture to set
     * @param isOutput if the slot is an output slot
     */
    public void setItemSlotOverlayMui2(@NotNull UITexture texture, boolean isOutput) {
        this.slotOverlaysMui2.put(computeOverlayKey(isOutput, false, false), texture);
        this.slotOverlaysMui2.put(computeOverlayKey(isOutput, false, true), texture);
    }

    /**
     * MUI2 equivalent of {@link #setItemSlotOverlay(TextureArea, boolean, boolean)}.
     *
     * @param texture    the MUI2 texture to set
     * @param isOutput   if the slot is an output slot
     * @param isLastSlot if the slot is the last slot
     */
    public void setItemSlotOverlayMui2(@NotNull UITexture texture, boolean isOutput, boolean isLastSlot) {
        this.slotOverlaysMui2.put(computeOverlayKey(isOutput, false, isLastSlot), texture);
    }

    /**
     * MUI2 equivalent of {@link #setFluidSlotOverlay(TextureArea, boolean)}.
     *
     * @param texture  the MUI2 texture to set
     * @param isOutput if the slot is an output slot
     */
    public void setFluidSlotOverlayMui2(@NotNull UITexture texture, boolean isOutput) {
        this.slotOverlaysMui2.put(computeOverlayKey(isOutput, true, false), texture);
        this.slotOverlaysMui2.put(computeOverlayKey(isOutput, true, true), texture);
    }

    /**
     * MUI2 equivalent of {@link #setFluidSlotOverlay(TextureArea, boolean, boolean)}.
     *
     * @param texture    the MUI2 texture to set
     * @param isOutput   if the slot is an output slot
     * @param isLastSlot if the slot is the last slot
     */
    public void setFluidSlotOverlayMui2(@NotNull UITexture texture, boolean isOutput, boolean isLastSlot) {
        this.slotOverlaysMui2.put(computeOverlayKey(isOutput, true, isLastSlot), texture);
    }

    /**
     * MUI2 equivalent of {@link #setSlotOverlay(byte, TextureArea)}.
     *
     * @param key     the key to store the slot's texture with
     * @param texture the MUI2 texture to store
     */
    @ApiStatus.Internal
    public void setSlotOverlayMui2(byte key, @NotNull UITexture texture) {
        this.slotOverlaysMui2.put(key, texture);
    }

    /**
     * @return the UI's recipemap
     */
    public @NotNull R recipeMap() {
        return recipeMap;
    }
}
