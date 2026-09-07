package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Advances {@link RecipeSearchOperator}'s iterator, applying {@code RecipeLogicHooks#recipeSearchPredicate} if
 * configured, until a candidate passes or the iterator is exhausted. The loop happens entirely within this one
 * operator call, since there's no need to make each individual skip a distinct graph step.
 */
public final class RecipeSelectionOperator implements GTStateMachineTransientOperator {

    public static final String SELECTED_RECIPE_KEY = "SelectedCandidate";

    /** On {@code data}: whether a candidate was found this call. */
    public static final String SUCCESS_KEY = "RecipeSelectionSuccess";

    public static final Predicate<NBTTagCompound> SUCCESS_PREDICATE = d -> d.getBoolean(SUCCESS_KEY);

    private final @NotNull RecipeLogicConfig config;

    public RecipeSelectionOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        Iterator<Recipe> iterator = (Iterator<Recipe>) transientData.get(RecipeSearchOperator.ITERATOR_KEY);
        if (iterator == null) throw new IllegalStateException("RecipeSelectionOperator ran without a search iterator");

        data.setBoolean(SUCCESS_KEY, false);
        while (iterator.hasNext()) {
            Recipe candidate = iterator.next();
            if (config.hooks.recipeSearchPredicate == null || config.hooks.recipeSearchPredicate.test(candidate)) {
                transientData.put(SELECTED_RECIPE_KEY, candidate);
                data.setBoolean(SUCCESS_KEY, true);
                return;
            }
        }
        reportNoMatch(transientData);
    }

    /**
     * Computes and reports {@code config.hooks.onNoMatchFound}'s diagnostic breakdown, but only once that hook is
     * actually wired (see its own JavaDoc for why this keeps the untraced case free) &mdash; nothing here runs at
     * all for the overwhelming majority of searches.
     */
    @SuppressWarnings("unchecked")
    private void reportNoMatch(@NotNull Map<String, Object> transientData) {
        if (config.hooks.onNoMatchFound == null) return;
        long maxVoltage = (long) transientData.getOrDefault(RecipeSearchOperator.MAX_VOLTAGE_KEY, Long.MAX_VALUE);
        RecipePropertySet properties = (RecipePropertySet) transientData.get(RecipeSearchOperator.PROPERTIES_KEY);
        List<ItemStack> items = (List<ItemStack>) transientData.get(RecipeSearchOperator.ITEMS_SNAPSHOT_KEY);
        List<FluidStack> fluids = (List<FluidStack>) transientData.get(RecipeSearchOperator.FLUIDS_SNAPSHOT_KEY);
        config.hooks.onNoMatchFound
                .accept(config.lookup.get().diagnoseNoMatch(maxVoltage, properties, items, fluids));
    }
}
