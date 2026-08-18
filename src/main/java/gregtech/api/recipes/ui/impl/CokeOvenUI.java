package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.ProgressBarMoveType;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.widget.ParentWidget;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

@ApiStatus.Internal
public class CokeOvenUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    /**
     * @param recipeMap the recipemap corresponding to this ui
     */
    public CokeOvenUI(@NotNull R recipeMap) {
        super(recipeMap, false, false, false, false, false);
    }

    @Override
    public @NotNull ParentWidget<?> buildUITemplate(@NotNull DoubleSupplier progressSupplier,
                                                    @NotNull IItemHandlerModifiable importItems,
                                                    @NotNull IItemHandlerModifiable exportItems,
                                                    @NotNull FluidTankList importFluids,
                                                    @NotNull FluidTankList exportFluids) {
        ParentWidget<?> parent = new ParentWidget<>();
        parent.child(createJeiProgressWidget(progressSupplier)
                .pos(70, 19).size(36, 18)
                .texture(GTGuiTextures.PROGRESS_BAR_COKE_OVEN, 36)
                .direction(RecipeMapUI.toMui2Direction(ProgressBarMoveType.HORIZONTAL)));
        addSlotMui2(parent, 52, 10, 0, importItems, importFluids, false, false);
        addSlotMui2(parent, 106, 10, 0, exportItems, exportFluids, false, true);
        addSlotMui2(parent, 106, 28, 0, exportItems, exportFluids, true, true);
        return parent;
    }
}
