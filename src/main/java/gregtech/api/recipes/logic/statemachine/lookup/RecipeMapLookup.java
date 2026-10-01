package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.BitflagRecipeLookup;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The standard {@link RecipeLookup}: wraps an existing {@link RecipeMap}, delegating every search to its cached
 * {@link RecipeMap#getBitflagLookup()} (see that method's/{@link BitflagRecipeLookup}'s JavaDoc for how it stays
 * fast and up to date across many searches). This mirrors {@link RecipeMap#findRecipe(long, List, List)}'s own
 * matching rules (voltage tier, then ingredient matching) exactly, generalized to return every match instead of
 * just the first.
 * <p>
 * <b>Delegates to {@link BitflagRecipeLookup} rather than scanning {@link RecipeMap#getRecipeList()} directly:</b>
 * a plain linear scan (O(recipe count)) calling {@link Recipe#matches} against every recipe, on top of
 * {@code getRecipeList()}'s own cost (it rebuilds and re-sorts a fresh list every single call, not a cached
 * snapshot), is expensive enough for a {@link RecipeMap} with hundreds of recipes (e.g. the Assembler's) to trip
 * GregTech's own per-tile lag-source watchdog, since every {@code RecipeWorkable}-based machine runs this once per
 * idle tick. Delegating to {@link BitflagRecipeLookup} here, rather than switching every machine's
 * {@code createConfig()} over to {@code recipeMap::getBitflagLookup} directly, covers every existing/future call
 * site built around {@code () -> new RecipeMapLookup(recipeMap)} at once, with no API change anywhere else. This
 * also avoids a correctness gap: {@link BitflagRecipeLookup} actually honors the property-aware
 * {@link #findRecipes(long, RecipePropertySet, List, List)} overload (Cleanroom/
 * Dimension/Biome/Computation/CoilTemperature/FusionStartEnergy filters), which this class's old self-contained
 * implementation silently ignored by never overriding it.
 * <p>
 * <b>Falls back to {@link RecipeMap#findRecipe(long, List, List, boolean)} itself when {@link BitflagRecipeLookup}
 * has no match</b>: a handful of {@link RecipeMap} subclasses (e.g. {@code RecipeMapFurnace}'s vanilla-smelting
 * fallback, also {@code RecipeMapFluidCanner}/{@code RecipeMapFormingPress}/{@code RecipeMapScanner}) override
 * {@code findRecipe} to synthesize a {@link Recipe} on demand for inputs that have no pre-registered GT recipe at
 * all, rather than pre-registering every possible one. {@link BitflagRecipeLookup}'s index (built from
 * {@link RecipeMap#getRecipeList()}) is blind to those synthesized recipes entirely, since they never exist in that
 * list &mdash; discovered when Electric Furnace (backed by {@code RecipeMapFurnace}) silently refused to smelt
 * anything without an explicit GT recipe once switched to an earlier version of this lookup. Calling
 * {@code findRecipe} virtually dispatches to whichever override the concrete {@link RecipeMap} provides, so this
 * class doesn't need to know about any of them by name; doing it only when the indexed search comes up empty avoids
 * the extra call's cost for the common case (a plain {@link RecipeMap} with no override, which is the vast
 * majority).
 */
public final class RecipeMapLookup implements RecipeLookup {

    private final @NotNull RecipeMap<?> recipeMap;

    public RecipeMapLookup(@NotNull RecipeMap<?> recipeMap) {
        this.recipeMap = recipeMap;
    }

    @Override
    public @NotNull Iterator<Recipe> findRecipes(long maxVoltage, @NotNull List<ItemStack> items,
                                                 @NotNull List<FluidStack> fluids) {
        return findRecipes(maxVoltage, null, items, fluids);
    }

    @Override
    public @NotNull Iterator<Recipe> findRecipes(long maxVoltage, @Nullable RecipePropertySet properties,
                                                 @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids) {
        List<ItemStack> filteredItems = items.stream().filter(stack -> !stack.isEmpty())
                .collect(Collectors.toList());
        List<FluidStack> filteredFluids = fluids.stream().filter(stack -> stack != null && stack.amount != 0)
                .collect(Collectors.toList());

        Iterator<Recipe> matches = recipeMap.getBitflagLookup().findRecipes(maxVoltage, properties, filteredItems,
                filteredFluids);

        // Falling back to findRecipe() unconditionally on an empty result would conflate
        // two different reasons for that emptiness -- "no recipe matches these ingredients at all" (this fallback's
        // actual intended case, e.g. RecipeMapFurnace's vanilla-smelting synthesis) and "a recipe matches the
        // ingredients but a property filter (Cleanroom/Dimension/CoilTemperature/FusionStartEnergy/...) correctly
        // excluded it" -- and findRecipe() itself has no property awareness at all, so it would silently
        // rediscover and return the very recipe the filter just excluded. Re-querying without properties
        // distinguishes the two: only genuinely no ingredient match (still empty here too) is allowed through to
        // the dynamic-synthesis fallback below.
        if (!matches.hasNext() && (properties == null || !recipeMap.getBitflagLookup()
                .findRecipes(maxVoltage, null, filteredItems, filteredFluids).hasNext())) {
            Recipe dynamic = recipeMap.findRecipe(maxVoltage, filteredItems, filteredFluids, false);
            if (dynamic != null) return Collections.singletonList(dynamic).iterator();
        }
        return matches;
    }

    @Override
    public @NotNull String diagnoseNoMatch(long maxVoltage, @Nullable RecipePropertySet properties,
                                           @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids) {
        return recipeMap.getBitflagLookup().diagnoseNoMatch(maxVoltage, properties, items, fluids);
    }
}
