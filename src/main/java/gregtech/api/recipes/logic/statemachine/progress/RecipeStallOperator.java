package gregtech.api.recipes.logic.statemachine.progress;

import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeStallType;
import gregtech.api.statemachine.GTStateMachineOperator;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

/**
 * Handles a failed per-tick check on the currently-selected active recipe, per the configured
 * {@link RecipeStallType}: decay progress by one tick's worth (mirroring the pace it would otherwise have
 * advanced), reset it to zero outright, or leave it untouched.
 * <p>
 * One parameterized operator covering all three {@link RecipeStallType} variants (including the pure no-op
 * {@link RecipeStallType#PAUSE}), since they only differ in how far they roll progress back.
 */
public final class RecipeStallOperator implements GTStateMachineOperator {

    private static final RecipeStallOperator DEGRESS = new RecipeStallOperator(RecipeStallType.DEGRESS);
    private static final RecipeStallOperator RESET = new RecipeStallOperator(RecipeStallType.RESET);
    private static final RecipeStallOperator PAUSE = new RecipeStallOperator(RecipeStallType.PAUSE);

    private final RecipeStallType type;

    private RecipeStallOperator(@NotNull RecipeStallType type) {
        this.type = type;
    }

    @NotNull
    public static RecipeStallOperator of(@NotNull RecipeStallType type) {
        return switch (type) {
            case RESET -> RESET;
            case PAUSE -> PAUSE;
            case DEGRESS -> DEGRESS;
        };
    }

    @Override
    public void operate(NBTTagCompound data) {
        if (type == RecipeStallType.PAUSE) return;
        NBTTagCompound entry = ActiveRecipeList.selected(data);
        int progress = entry.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY);
        if (progress <= 0) return;
        int newProgress = type == RecipeStallType.RESET ? 0 : Math.max(0, progress - 1);
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, newProgress);
    }
}
