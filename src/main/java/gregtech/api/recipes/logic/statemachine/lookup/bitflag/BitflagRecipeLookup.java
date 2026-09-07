package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

/**
 * The performance-optimized {@link RecipeLookup}: wraps a {@link RecipeMap}'s recipes in an
 * {@link IngredientBitflagIndex} (fast ingredient-type matching) plus a voltage {@link RecipeThresholdIndex}
 * (fast pre-exclusion of over-voltage recipes), rebuilt lazily whenever the map's recipes actually change,
 * instead of {@code RecipeMapLookup}'s per-search linear scan.
 * <p>
 * <b>Must be held onto and reused across searches, unlike {@code RecipeMapLookup}:</b> the whole point of the
 * indices this class builds is to pay their construction cost once and amortize it across many searches. A machine
 * wiring {@code config.lookup = () -> new BitflagRecipeLookup(recipeMap)} (recreating one fresh every tick, the way
 * {@code RecipeMapLookup} is typically used) would rebuild both indices from scratch every tick, which is strictly
 * worse than the linear scan it's meant to replace. Use {@link RecipeMap#getBitflagLookup()} instead, which caches
 * exactly one instance per {@link RecipeMap} and invalidates it automatically when recipes are added or removed.
 * <p>
 * <b>Extending beyond voltage:</b> {@link #registerFilter(RecipeNumericFilter)} adds an additional
 * {@link RecipeNumericFilter}, built into its own {@link RecipeThresholdIndex} alongside the built-in voltage one;
 * {@link #registerFilter(RecipePredicateFilter)} does the same for a non-numeric {@link RecipePredicateFilter},
 * built into a {@link RecipeCategoryIndex}. Registration is per-instance (called on one {@link RecipeMap}'s lookup,
 * typically once during that machine type's setup) rather than through a global registry: GregTech's existing
 * per-machine configuration ({@code RecipeLogicConfig} and friends) is already wired this way, and a global
 * registry would apply a filter to every {@code RecipeMap} in the game regardless of whether its recipes ever
 * declare the relevant {@code RecipeProperty}, which is unnecessary overhead for the vast majority of maps that
 * don't.
 * <p>
 * <b>Thread safety:</b> a read/write lock guards the built indices, since {@link RecipeMap}'s recipe list can
 * (rarely) change concurrently with an in-progress search (e.g. a CraftTweaker/GroovyScript live reload racing an
 * {@code asyncSearchAndSetup} search on another thread). Rebuilds take the write lock; searches take the read lock
 * only around reading the already-built indices (not around the rebuild check itself), so concurrent searches never
 * block each other. {@link #registerFilter} itself is not synchronized against concurrent searches &mdash; call it
 * during setup, before this lookup is ever searched from another thread.
 */
public final class BitflagRecipeLookup implements RecipeLookup {

    private final @NotNull RecipeMap<?> recipeMap;
    private final @NotNull ReadWriteLock lock = new ReentrantReadWriteLock();
    private final @NotNull Set<RecipeNumericFilter<?>> additionalFilters = new ObjectOpenHashSet<>();
    private final @NotNull Set<RecipePredicateFilter<?>> additionalPredicateFilters = new ObjectOpenHashSet<>();

    private volatile boolean dirty = true;
    private @Nullable IngredientBitflagIndex ingredientIndex;
    private @Nullable RecipeThresholdIndex voltageIndex;
    private @NotNull Map<RecipeNumericFilter<?>, RecipeThresholdIndex> additionalIndices = new LinkedHashMap<>();
    private @NotNull Map<RecipePredicateFilter<?>, RecipeCategoryIndex<?>> additionalCategoryIndices = new LinkedHashMap<>();

    public BitflagRecipeLookup(@NotNull RecipeMap<?> recipeMap) {
        this.recipeMap = recipeMap;
    }

    /** Marks the cached indices stale, forcing a rebuild on the next search. See {@link RecipeMap#getBitflagLookup()}. */
    public void invalidate() {
        dirty = true;
    }

    /**
     * Adds an additional pre-filter beyond the built-in voltage check, taking effect from the next search onward
     * (this forces a rebuild, so registering many filters at once is preferable to one at a time). See this class's
     * JavaDoc for why registration is per-instance rather than global.
     */
    public void registerFilter(@NotNull RecipeNumericFilter<?> filter) {
        additionalFilters.add(filter);
        dirty = true;
    }

    /** As {@link #registerFilter(RecipeNumericFilter)}, but for a non-numeric {@link RecipePredicateFilter}. */
    public void registerFilter(@NotNull RecipePredicateFilter<?> filter) {
        additionalPredicateFilters.add(filter);
        dirty = true;
    }

    @Override
    public @NotNull Iterator<Recipe> findRecipes(long maxVoltage, @NotNull List<ItemStack> items,
                                                 @NotNull List<FluidStack> fluids) {
        return findRecipes(maxVoltage, null, items, fluids);
    }

    @Override
    public @NotNull Iterator<Recipe> findRecipes(long maxVoltage, @Nullable RecipePropertySet properties,
                                                 @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids) {
        ensureBuilt();
        lock.readLock().lock();
        try {
            assert ingredientIndex != null && voltageIndex != null; // guaranteed by ensureBuilt()
            BitSet excluded = new BitSet();
            excluded.or(voltageIndex.excluded(maxVoltage));
            if (properties != null) {
                for (Map.Entry<RecipeNumericFilter<?>, RecipeThresholdIndex> entry : additionalIndices.entrySet()) {
                    Long query = entry.getKey().extractQuery(properties);
                    if (query != null) excluded.or(entry.getValue().excluded(query));
                }
                for (Map.Entry<RecipePredicateFilter<?>, RecipeCategoryIndex<?>> entry : additionalCategoryIndices
                        .entrySet()) {
                    excluded.or(orExcludedFromCategoryIndex(entry.getKey(), entry.getValue(), properties));
                }
            }
            return ingredientIndex.match(items, fluids, excluded).iterator();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull String diagnoseNoMatch(long maxVoltage, @Nullable RecipePropertySet properties,
                                           @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids) {
        ensureBuilt();
        lock.readLock().lock();
        try {
            assert ingredientIndex != null && voltageIndex != null; // guaranteed by ensureBuilt()
            StringBuilder sb = new StringBuilder();
            int total = ingredientIndex.recipeCount();
            sb.append(total).append(" recipe(s) in this map");

            BitSet excluded = new BitSet();
            BitSet voltageExcluded = voltageIndex.excluded(maxVoltage);
            sb.append("; voltage<=").append(maxVoltage).append(" excludes ").append(voltageExcluded.cardinality());
            excluded.or(voltageExcluded);

            if (properties == null) {
                if (!additionalIndices.isEmpty() || !additionalCategoryIndices.isEmpty()) {
                    sb.append("; no RecipePropertySet supplied, ").append(additionalIndices.size() +
                            additionalCategoryIndices.size()).append(" registered property filter(s) skipped entirely");
                }
            } else {
                for (Map.Entry<RecipeNumericFilter<?>, RecipeThresholdIndex> entry : additionalIndices.entrySet()) {
                    describeNumericFilter(sb, entry.getKey(), entry.getValue(), properties, excluded);
                }
                for (Map.Entry<RecipePredicateFilter<?>, RecipeCategoryIndex<?>> entry : additionalCategoryIndices
                        .entrySet()) {
                    describeCategoryFilter(sb, entry.getKey(), entry.getValue(), properties, excluded);
                }
            }

            int remaining = total - excluded.cardinality();
            int ingredientMatches = ingredientIndex.match(items, fluids, excluded).size();
            sb.append("; ").append(remaining).append(" remain after property filters, ").append(ingredientMatches)
                    .append(" of those match the given item/fluid inputs");
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Generic helper so {@link #diagnoseNoMatch} can describe one {@code RecipeNumericFilter<?>} despite the wildcard. */
    private static <T> void describeNumericFilter(@NotNull StringBuilder sb, @NotNull RecipeNumericFilter<T> filter,
                                                   @NotNull RecipeThresholdIndex index,
                                                   @NotNull RecipePropertySet properties, @NotNull BitSet excluded) {
        Long query = filter.extractQuery(properties);
        String name = filter.recipeProperty().getKey();
        if (query == null) {
            sb.append("; ").append(name).append(" filter not applicable this search (not tracked)");
            return;
        }
        BitSet filterExcluded = index.excluded(query);
        sb.append("; ").append(name).append(" excludes ").append(filterExcluded.cardinality());
        excluded.or(filterExcluded);
    }

    /** As {@link #describeNumericFilter}, but for a {@link RecipePredicateFilter}/{@link RecipeCategoryIndex}. */
    private static <T> void describeCategoryFilter(@NotNull StringBuilder sb, @NotNull RecipePredicateFilter<T> filter,
                                                    @NotNull RecipeCategoryIndex<?> index,
                                                    @NotNull RecipePropertySet properties, @NotNull BitSet excluded) {
        Predicate<T> query = filter.extractQuery(properties);
        String name = filter.recipeProperty().getKey();
        if (query == null) {
            sb.append("; ").append(name).append(" filter not applicable this search (not tracked)");
            return;
        }
        @SuppressWarnings("unchecked")
        RecipeCategoryIndex<T> typed = (RecipeCategoryIndex<T>) index;
        BitSet filterExcluded = typed.excluded(query);
        sb.append("; ").append(name).append(" excludes ").append(filterExcluded.cardinality());
        excluded.or(filterExcluded);
    }

    private void ensureBuilt() {
        if (!dirty) return;
        lock.writeLock().lock();
        try {
            if (!dirty) return; // another thread may have rebuilt while we were waiting for the write lock
            rebuild();
            dirty = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void rebuild() {
        Collection<Recipe> recipes = recipeMap.getRecipeList();
        IngredientBitflagIndex ingredients = IngredientBitflagIndex.build(recipes);

        List<RecipeThresholdIndex.Entry> voltageEntries = new ObjectArrayList<>(ingredients.recipeCount());
        for (int i = 0; i < ingredients.recipeCount(); i++) {
            // matches RecipeMapLookup's own `recipe.getVoltage() <= maxVoltage` check exactly, including its
            // (pre-existing, out of this class's scope to fix) quirk of never excluding generating recipes: their
            // negative EUt always satisfies "<= maxVoltage" against any positive supply.
            voltageEntries.add(new VoltageEntry(i, ingredients.recipeAt(i).getVoltage()));
        }

        Map<RecipeNumericFilter<?>, RecipeThresholdIndex> additional = new LinkedHashMap<>();
        for (RecipeNumericFilter<?> filter : additionalFilters) {
            additional.put(filter, buildAdditionalIndex(filter, ingredients));
        }

        Map<RecipePredicateFilter<?>, RecipeCategoryIndex<?>> additionalCategory = new LinkedHashMap<>();
        for (RecipePredicateFilter<?> filter : additionalPredicateFilters) {
            additionalCategory.put(filter, buildAdditionalCategoryIndex(filter, ingredients));
        }

        this.ingredientIndex = ingredients;
        this.voltageIndex = RecipeThresholdIndex.build(RecipeThresholdIndex.Comparison.AT_MOST, voltageEntries);
        this.additionalIndices = additional;
        this.additionalCategoryIndices = additionalCategory;
    }

    /** Generic helper so {@link #rebuild} can call this per {@code RecipeNumericFilter<?>} despite the wildcard. */
    private <T> @NotNull RecipeThresholdIndex buildAdditionalIndex(@NotNull RecipeNumericFilter<T> filter,
                                                                   @NotNull IngredientBitflagIndex ingredients) {
        List<RecipeThresholdIndex.Entry> entries = new ObjectArrayList<>();
        for (int i = 0; i < ingredients.recipeCount(); i++) {
            Recipe recipe = ingredients.recipeAt(i);
            if (!recipe.hasProperty(filter.recipeProperty())) continue; // no requirement declared -> never excluded
            T value = recipe.getProperty(filter.recipeProperty(), null);
            entries.add(new GenericEntry(i, filter.extractThreshold(value)));
        }
        return RecipeThresholdIndex.build(filter.comparison(), entries);
    }

    /** As {@link #buildAdditionalIndex}, but for a {@link RecipePredicateFilter}/{@link RecipeCategoryIndex}. */
    private <T> @NotNull RecipeCategoryIndex<T> buildAdditionalCategoryIndex(@NotNull RecipePredicateFilter<T> filter,
                                                                             @NotNull IngredientBitflagIndex ingredients) {
        List<RecipeCategoryIndex.Entry<T>> entries = new ObjectArrayList<>();
        for (int i = 0; i < ingredients.recipeCount(); i++) {
            Recipe recipe = ingredients.recipeAt(i);
            if (!recipe.hasProperty(filter.recipeProperty())) continue; // no requirement declared -> never excluded
            T value = recipe.getProperty(filter.recipeProperty(), null);
            entries.add(new GenericCategoryEntry<>(i, value));
        }
        return RecipeCategoryIndex.build(entries);
    }

    /**
     * Generic helper so {@link #findRecipes} can call {@link RecipePredicateFilter#extractQuery}/
     * {@link RecipeCategoryIndex#excluded} together despite the two wildcards not otherwise capturing to the same
     * {@code T}.
     */
    private static <T> @NotNull BitSet orExcludedFromCategoryIndex(@NotNull RecipePredicateFilter<T> filter,
                                                                    @NotNull RecipeCategoryIndex<?> index,
                                                                    @NotNull RecipePropertySet properties) {
        Predicate<T> query = filter.extractQuery(properties);
        if (query == null) return new BitSet();
        @SuppressWarnings("unchecked")
        RecipeCategoryIndex<T> typed = (RecipeCategoryIndex<T>) index;
        return typed.excluded(query);
    }

    @Desugar
    private record VoltageEntry(int recipeIndex, long threshold) implements RecipeThresholdIndex.Entry {}

    @Desugar
    private record GenericEntry(int recipeIndex, long threshold) implements RecipeThresholdIndex.Entry {}

    @Desugar
    private record GenericCategoryEntry<T>(int recipeIndex, T value) implements RecipeCategoryIndex.Entry<T> {}
}
