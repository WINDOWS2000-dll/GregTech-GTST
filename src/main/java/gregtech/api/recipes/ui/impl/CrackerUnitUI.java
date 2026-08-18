package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.widget.ParentWidget;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

@ApiStatus.Internal
public class CrackerUnitUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    public CrackerUnitUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, false, true, false);
        setProgressBarMoveType(ProgressWidget.MoveType.HORIZONTAL);

        setFluidSlotOverlayMui2(GTGuiTextures.CRACKING_OVERLAY_1, false);
        setFluidSlotOverlayMui2(GTGuiTextures.CRACKING_OVERLAY_2, true);
        setItemSlotOverlayMui2(GTGuiTextures.CIRCUIT_OVERLAY, false);
        setProgressBarTextureMui2(GTGuiTextures.PROGRESS_BAR_CRACKING);
    }

    @Override
    public @NotNull ParentWidget<?> buildUITemplate(@NotNull DoubleSupplier progressSupplier,
                                                    @NotNull IItemHandlerModifiable importItems,
                                                    @NotNull IItemHandlerModifiable exportItems,
                                                    @NotNull FluidTankList importFluids,
                                                    @NotNull FluidTankList exportFluids) {
        ParentWidget<?> parent = new ParentWidget<>();
        if (recipeMap().getMaxInputs() == 1) {
            addSlotMui2(parent, 52, 24, 0, importItems, importFluids, false, false);
        } else {
            int[] grid = determineSlotsGrid(recipeMap().getMaxInputs());
            for (int y = 0; y < grid[1]; y++) {
                for (int x = 0; x < grid[0]; x++) {
                    addSlotMui2(parent, 34 + (x * 18) - (Math.max(0, grid[0] - 2) * 18),
                            24 + (y * 18) - (Math.max(0, grid[1] - 1) * 18),
                            y * grid[0] + x, importItems, importFluids, false, false);
                }
            }
        }

        addInventorySlotGroupMui2(parent, exportItems, exportFluids, true);
        addSlotMui2(parent, 52, 24 + 19 + 18, 0, importItems, importFluids, true, false);
        addSlotMui2(parent, 34, 24 + 19 + 18, 1, importItems, importFluids, true, false);

        parent.child(createJeiProgressWidget(progressSupplier)
                .pos(42, 24 + 18).size(21, 19)
                .texture(GTGuiTextures.PROGRESS_BAR_CRACKING_INPUT, 19)
                .direction(RecipeMapUI.toMui2Direction(ProgressWidget.MoveType.VERTICAL)));
        parent.child(createJeiProgressWidget(progressSupplier)
                .pos(78, 23).size(20, 20)
                .texture(progressBarTextureMui2(), 20)
                .direction(RecipeMapUI.toMui2Direction(progressBarMoveType())));
        return parent;
    }
}
