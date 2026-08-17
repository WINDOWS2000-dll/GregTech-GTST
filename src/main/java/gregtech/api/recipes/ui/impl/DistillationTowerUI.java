package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;

public class DistillationTowerUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    public DistillationTowerUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, true, false, false);
    }

    @Override
    protected void addSlotMui2(@NotNull ParentWidget<?> parent, int x, int y, int slotIndex,
                               @NotNull IItemHandlerModifiable itemHandler, @NotNull FluidTankList fluidHandler,
                               boolean isFluid, boolean isOutputs) {
        if (isFluid) {
            @Nullable
            UITexture overlay;
            if (!isOutputs) overlay = GTGuiTextures.BEAKER_OVERLAY_1;
            else if (slotIndex == 0 || slotIndex == 3 || slotIndex == 6 || slotIndex == 9)
                overlay = GTGuiTextures.BEAKER_OVERLAY_2;
            else if (slotIndex == 1 || slotIndex == 4 || slotIndex == 7 || slotIndex == 10)
                overlay = GTGuiTextures.BEAKER_OVERLAY_3;
            else if (slotIndex == 2 || slotIndex == 5 || slotIndex == 8 || slotIndex == 11)
                overlay = GTGuiTextures.BEAKER_OVERLAY_4;
            else overlay = null;

            UITexture[] background = overlay == null ? new UITexture[] { GTGuiTextures.FLUID_SLOT } :
                    new UITexture[] { GTGuiTextures.FLUID_SLOT, overlay };

            parent.child(new GTFluidSlot()
                    .pos(x, y).size(18)
                    .background(background)
                    .syncHandler(GTFluidSlot.sync(fluidHandler.getTankAt(slotIndex))
                            .showAmount(true, true)
                            .accessibility(!isOutputs, true)));
        } else {
            parent.child(new ItemSlot()
                    .pos(x, y)
                    .background(new UITexture[] { GTGuiTextures.SLOT, GTGuiTextures.DUST_OVERLAY })
                    .slot(new ModularSlot(itemHandler, slotIndex).accessibility(!isOutputs, true)));
        }
    }

    @Override
    public @NotNull ParentWidget<?> buildUITemplate(@NotNull DoubleSupplier progressSupplier,
                                                    @NotNull IItemHandlerModifiable importItems,
                                                    @NotNull IItemHandlerModifiable exportItems,
                                                    @NotNull FluidTankList importFluids,
                                                    @NotNull FluidTankList exportFluids) {
        ParentWidget<?> parent = new ParentWidget<>();
        parent.child(createJeiProgressWidget(progressSupplier)
                .pos(47, 8).size(66, 58)
                .texture(GTGuiTextures.PROGRESS_BAR_DISTILLATION_TOWER, 66)
                .direction(RecipeMapUI.toMui2Direction(ProgressWidget.MoveType.HORIZONTAL)));
        addInventorySlotGroupMui2(parent, importItems, importFluids, false);
        addInventorySlotGroupMui2(parent, exportItems, exportFluids, true);
        // note: MUI1-only setSpecialTexture()/specialTexture() is not currently mirrored on the MUI2 side;
        // no RecipeMap using this UI sets one at the time of writing.
        return parent;
    }

    /**
     * MUI2 equivalent of the yOffset-9 variant of {@link RecipeMapUI#addInventorySlotGroupMui2}.
     */
    @Override
    protected void addInventorySlotGroupMui2(@NotNull ParentWidget<?> parent,
                                             @NotNull IItemHandlerModifiable itemHandler,
                                             @NotNull FluidTankList fluidHandler, boolean isOutputs) {
        int yOffset = 9;
        int itemInputsCount = itemHandler.getSlots();
        int fluidInputsCount = fluidHandler.getTanks();
        boolean invertFluids = false;
        if (itemInputsCount == 0) {
            int tmp = itemInputsCount;
            itemInputsCount = fluidInputsCount;
            fluidInputsCount = tmp;
            invertFluids = true;
        }
        int[] inputSlotGrid = RecipeMapUI.determineSlotsGrid(itemInputsCount);
        int itemSlotsToLeft = inputSlotGrid[0];
        int itemSlotsToDown = inputSlotGrid[1];
        int startInputsX = isOutputs ? 104 : 68 - itemSlotsToLeft * 18;
        int startInputsY = 55 - (int) (itemSlotsToDown / 2.0 * 18) + yOffset;
        boolean wasGroupOutput = itemHandler.getSlots() + fluidHandler.getTanks() == 12;
        if (wasGroupOutput && isOutputs) startInputsY -= 9;
        if (itemHandler.getSlots() == 6 && fluidHandler.getTanks() == 2 && !isOutputs) startInputsY -= 9;
        if (!isOutputs) {
            addSlotMui2(parent, 40, startInputsY + (itemSlotsToDown - 1) * 18 - 18, 0, itemHandler, fluidHandler,
                    invertFluids, false);
        } else {
            addSlotMui2(parent, 94, startInputsY + (itemSlotsToDown - 1) * 18, 0, itemHandler, fluidHandler,
                    invertFluids, true);
        }

        if (wasGroupOutput) startInputsY += 2;

        if (!isOutputs) return;

        if (!invertFluids) {
            startInputsY -= 18;
            startInputsX += 9;
        }

        if (fluidInputsCount > 0 || invertFluids) {
            int startSpecY = startInputsY + itemSlotsToDown * 18;
            for (int i = 0; i < fluidInputsCount; i++) {
                int x = startInputsX + 18 * (i % 3);
                int y = startSpecY - (i / 3) * 18;
                addSlotMui2(parent, x, y, i, itemHandler, fluidHandler, true, true);
            }
        }
    }
}
