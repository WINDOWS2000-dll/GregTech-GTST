package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.ProgressBarMoveType;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

@ApiStatus.Internal
public final class AssemblyLineUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /**
     * @param recipeMap the recipemap corresponding to this ui
     */
    public AssemblyLineUI(@NotNull R recipeMap) {
        super(recipeMap, false, false, false, false, false);
        setProgressBarMoveType(ProgressBarMoveType.HORIZONTAL);
    }

    @Override
    protected void addInventorySlotGroupMui2(@NotNull ParentWidget<?> parent,
                                             @NotNull IItemHandlerModifiable itemHandler,
                                             @NotNull FluidTankList fluidHandler, boolean isOutputs) {
        int startInputsX = 80 - 4 * 18;
        int startInputsY = 37 - 2 * 18;

        if (!isOutputs) {
            // Data Slot
            parent.child(new ItemSlot()
                    .pos(startInputsX + 18 * 7, 1 + 18 * 2)
                    .background(new UITexture[] { GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY })
                    .slot(new ModularSlot(itemHandler, 16).accessibility(true, true)));

            // item input slots
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    int slotIndex = i * 4 + j;
                    addSlotMui2(parent, startInputsX + 18 * j, startInputsY + 18 * i, slotIndex, itemHandler,
                            fluidHandler, false, false);
                }
            }

            // fluid slots
            int startFluidX = startInputsX + 18 * 5;
            for (int i = 0; i < 4; i++) {
                addSlotMui2(parent, startFluidX, startInputsY + 18 * i, i, itemHandler, fluidHandler, true, false);
            }
        } else {
            // output slot
            addSlotMui2(parent, startInputsX + 18 * 7, 1, 0, itemHandler, fluidHandler, false, true);
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
                .pos(80, 1).size(54, 72)
                .texture(GTGuiTextures.PROGRESS_BAR_ASSEMBLY_LINE, 54)
                .direction(RecipeMapUI.toMui2Direction(ProgressBarMoveType.HORIZONTAL)));
        parent.child(createJeiProgressWidget(progressSupplier)
                .pos(138, 19).size(10, 18)
                .texture(GTGuiTextures.PROGRESS_BAR_ASSEMBLY_LINE_ARROW, 18)
                .direction(RecipeMapUI.toMui2Direction(ProgressBarMoveType.VERTICAL)));
        addInventorySlotGroupMui2(parent, importItems, importFluids, false);
        addInventorySlotGroupMui2(parent, exportItems, exportFluids, true);
        return parent;
    }
}
