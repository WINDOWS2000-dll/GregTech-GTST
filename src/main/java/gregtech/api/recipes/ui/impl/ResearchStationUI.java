package gregtech.api.recipes.ui.impl;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.ProgressBarMoveType;
import gregtech.api.recipes.ui.RecipeMapUI;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

@ApiStatus.Internal
public class ResearchStationUI<R extends RecipeMap<?>> extends RecipeMapUI<R> {

    public ResearchStationUI(@NotNull R recipeMap) {
        super(recipeMap, true, true, true, true, false);
        setProgressBarMoveType(ProgressBarMoveType.HORIZONTAL);
    }

    @Override
    public @NotNull ParentWidget<?> buildUITemplate(@NotNull DoubleSupplier progressSupplier,
                                                    @NotNull IItemHandlerModifiable importItems,
                                                    @NotNull IItemHandlerModifiable exportItems,
                                                    @NotNull FluidTankList importFluids,
                                                    @NotNull FluidTankList exportFluids) {
        ParentWidget<?> parent = new ParentWidget<>();
        parent.child(new Widget<>()
                .pos(10, 0).size(84, 60)
                .background(GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_BASE));
        parent.child(createJeiProgressWidget(progressSupplier)
                .pos(72, 28).size(54, 5)
                .texture(GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_1, 54)
                .direction(RecipeMapUI.toDirection(ProgressBarMoveType.HORIZONTAL)));
        parent.child(createJeiProgressWidget(progressSupplier)
                .pos(119, 32).size(10, 18)
                .texture(GTGuiTextures.PROGRESS_BAR_RESEARCH_STATION_2, 18)
                .direction(RecipeMapUI.toDirection(ProgressBarMoveType.VERTICAL_DOWNWARDS)));
        parent.child(new ItemSlot()
                .pos(115, 50)
                .background(new UITexture[] { GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY })
                .slot(new ModularSlot(exportItems, 0).accessibility(true, true)));
        parent.child(new ItemSlot()
                .pos(43, 21)
                .background(new UITexture[] { GTGuiTextures.SLOT, GTGuiTextures.SCANNER_OVERLAY })
                .slot(new ModularSlot(importItems, 1).accessibility(true, true)));
        parent.child(new ItemSlot()
                .pos(97, 21)
                .background(new UITexture[] { GTGuiTextures.SLOT, GTGuiTextures.RESEARCH_STATION_OVERLAY })
                .slot(new ModularSlot(importItems, 0).accessibility(true, true)));
        return parent;
    }
}
