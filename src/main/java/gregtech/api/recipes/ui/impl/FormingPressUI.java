package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class FormingPressUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    public FormingPressUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, true, true, false);
        setProgressBarMoveType(ProgressWidget.MoveType.HORIZONTAL);
        setProgressBarTextureMui2(GTGuiTextures.PROGRESS_BAR_COMPRESS);
    }

    @Override
    protected void addSlotMui2(@NotNull ParentWidget<?> parent, int x, int y, int slotIndex,
                               @NotNull IItemHandlerModifiable itemHandler, @NotNull FluidTankList fluidHandler,
                               boolean isFluid, boolean isOutputs) {
        if (isFluid) {
            super.addSlotMui2(parent, x, y, slotIndex, itemHandler, fluidHandler, true, isOutputs);
            return;
        }

        @Nullable
        UITexture overlay;
        if (isOutputs) overlay = GTGuiTextures.PRESS_OVERLAY_3;
        else if (slotIndex == 0 || slotIndex == 3) overlay = GTGuiTextures.PRESS_OVERLAY_2;
        else if (slotIndex == 1 || slotIndex == 4) overlay = GTGuiTextures.PRESS_OVERLAY_4;
        else if (slotIndex == 2 || slotIndex == 5) overlay = GTGuiTextures.PRESS_OVERLAY_1;
        else overlay = null;

        UITexture[] background = overlay == null ? new UITexture[] { GTGuiTextures.SLOT } :
                new UITexture[] { GTGuiTextures.SLOT, overlay };

        parent.child(new ItemSlot()
                .pos(x, y)
                .background(background)
                .slot(new ModularSlot(itemHandler, slotIndex).accessibility(!isOutputs, true)));
    }
}
