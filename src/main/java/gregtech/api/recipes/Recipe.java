package gregtech.api.recipes;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.chance.output.impl.ChancedFluidOutput;
import gregtech.api.recipes.chance.output.impl.ChancedItemOutput;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.match.IngredientMatchHelper;
import gregtech.api.recipes.ingredients.match.MatchCalculation;
import gregtech.api.recipes.ingredients.match.Matcher;
import gregtech.api.recipes.output.FluidOutputProvider;
import gregtech.api.recipes.output.ItemOutputProvider;
import gregtech.api.recipes.output.StandardFluidOutput;
import gregtech.api.recipes.output.StandardItemOutput;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.recipes.roll.RollInformation;
import gregtech.api.recipes.roll.RollInterpreter;
import gregtech.api.util.GTUtility;
import gregtech.api.util.ItemStackHashStrategy;
import gregtech.integration.groovy.GroovyScriptModule;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.oredict.OreDictionary;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class that represent machine recipe.
 * <p>
 * <p>
 * Recipes are created using {@link RecipeBuilder} or its subclasses in builder-alike pattern. To get RecipeBuilder use
 * {@link RecipeMap#recipeBuilder()}.
 * <p>
 * <p>
 * Example:
 * RecipeMap.POLARIZER_RECIPES.recipeBuilder().inputs(new ItemStack(Items.APPLE)).outputs(new
 * ItemStack(Items.GOLDEN_APPLE)).duration(256).EUt(480).buildAndRegister();
 * <p>
 * This will create and register Polarizer recipe with Apple as input and Golden apple as output, duration - 256 ticks
 * and energy consumption of 480 EU/t.
 * <p>
 * To get example for particular RecipeMap see {@link RecipeMap}
 * <p>
 * <p>
 * Recipes are immutable.
 */
public class Recipe {

    /**
     * This method was deprecated in 2.8 and will be removed in 2.9
     *
     * @deprecated use {@link ChancedOutputLogic#getMaxChancedValue()}
     */
    @ApiStatus.ScheduledForRemoval(inVersion = "2.9")
    @Deprecated
    public static int getMaxChancedValue() {
        return ChancedOutputLogic.getMaxChancedValue();
    }

    private final List<GTRecipeInput> inputs;

    /**
     * This recipe's item outputs (guaranteed and chance-based alike) &mdash; the actual source of truth. Use this
     * provider's own API ({@link ItemOutputProvider#computeOutputs}, {@link ItemOutputProvider#getCompleteOutputs},
     * etc.) for anything that needs to read them.
     */
    private final @NotNull ItemOutputProvider itemOutputProvider;
    private final List<GTRecipeInput> fluidInputs;

    /** As {@link #itemOutputProvider}, but for fluids. */
    private final @NotNull FluidOutputProvider fluidOutputProvider;

    private final int duration;

    /**
     * The voltage (per amp) this recipe requires or produces, always as a magnitude (see {@link #generating} for
     * direction). GregTech recipes have always been registered with a positive value regardless of direction (e.g.
     * fuel recipes' {@code .EUt(V[LV])}); this field's sign carries no meaning of its own.
     */
    private final long EUt;

    /**
     * The amperage this recipe requires (or, for a generating recipe, produces) at {@link #EUt}'s voltage.
     * Defaults to {@code 1} for recipes that don't specify one, which is every recipe registered before this field
     * was added &mdash; {@link #getEUt()}'s value and meaning are completely unchanged for those (see
     * {@link #getVoltage()}'s JavaDoc for how the two relate once amperage is not {@code 1}).
     * <p>
     * Represents the recipe's amperage as a first-class value, separate from voltage (see {@link #getVoltage()}'s
     * JavaDoc for how the two relate).
     */
    private final long amperage;

    /**
     * Whether this recipe produces power rather than consuming it. Deliberately independent of
     * {@link #EUt}'s sign: GregTech's own recipe data (e.g. {@code FuelRecipes}) has always registered fuel recipes
     * with a positive {@code EUt}, with direction carried entirely by this flag,
     * so deriving this from {@code EUt < 0} (as an earlier, unexercised design of this field assumed) would treat
     * every existing generating recipe as a consuming one. Set via {@link RecipeBuilder#setGenerating()}, mirroring
     * PR's {@code FuelRecipeBuilder}'s constructor calling it unconditionally.
     */
    private final boolean generating;

    /**
     * If this Recipe is hidden from JEI
     */
    private final boolean hidden;
    private final GTRecipeCategory recipeCategory;

    /**
     * If this Recipe is a Crafttweaker recipe. Used for logging purposes
     */
    // TODO YEET
    private final boolean isCTRecipe;
    private final boolean groovyRecipe;
    private final RecipePropertyStorage recipePropertyStorage;

    private final int hashCode;

    public Recipe(@NotNull List<GTRecipeInput> inputs,
                  @NotNull ItemOutputProvider itemOutputProvider,
                  List<GTRecipeInput> fluidInputs,
                  @NotNull FluidOutputProvider fluidOutputProvider,
                  int duration,
                  long EUt,
                  long amperage,
                  boolean generating,
                  boolean hidden,
                  boolean isCTRecipe,
                  @NotNull RecipePropertyStorage recipePropertyStorage,
                  @NotNull GTRecipeCategory recipeCategory) {
        this.recipePropertyStorage = recipePropertyStorage;
        this.inputs = GTRecipeInputCache.deduplicateInputs(inputs);
        this.itemOutputProvider = itemOutputProvider;
        this.fluidInputs = GTRecipeInputCache.deduplicateInputs(fluidInputs);
        this.fluidOutputProvider = fluidOutputProvider;
        this.duration = duration;
        this.EUt = EUt;
        this.amperage = amperage;
        this.generating = generating;
        this.hidden = hidden;
        this.recipeCategory = recipeCategory;
        this.isCTRecipe = isCTRecipe;
        this.hashCode = makeHashCode();
        this.groovyRecipe = GroovyScriptModule.isCurrentlyRunning();
    }

    @NotNull
    public Recipe copy() {
        return new Recipe(this.inputs, this.itemOutputProvider, this.fluidInputs, this.fluidOutputProvider,
                this.duration, this.EUt, this.amperage, this.generating, this.hidden, this.isCTRecipe,
                this.recipePropertyStorage, this.recipeCategory);
    }

    /**
     * Trims the recipe outputs, chanced outputs, and fluid outputs based on the performing MetaTileEntity's trim limit.
     *
     * @param currentRecipe  The recipe to perform the output trimming upon
     * @param recipeMap      The RecipeMap that the recipe is from
     * @param itemTrimLimit  The Limit to which item outputs should be trimmed to, -1 for no trimming
     * @param fluidTrimLimit The Limit to which fluid outputs should be trimmed to, -1 for no trimming
     * @return A new Recipe whose outputs have been trimmed.
     */
    public static Recipe trimRecipeOutputs(Recipe currentRecipe, RecipeMap<?> recipeMap, int itemTrimLimit,
                                           int fluidTrimLimit) {
        // Fast return early if no trimming desired
        if (itemTrimLimit == -1 && fluidTrimLimit == -1) {
            return currentRecipe;
        }

        ItemOutputProvider items = itemTrimLimit == -1 ? currentRecipe.itemOutputProvider :
                currentRecipe.itemOutputProvider.trim(itemTrimLimit);
        FluidOutputProvider fluids = fluidTrimLimit == -1 ? currentRecipe.fluidOutputProvider :
                currentRecipe.fluidOutputProvider.trim(fluidTrimLimit);

        return new Recipe(currentRecipe.inputs, items, currentRecipe.fluidInputs, fluids, currentRecipe.duration,
                currentRecipe.EUt, currentRecipe.amperage, currentRecipe.generating, currentRecipe.hidden,
                currentRecipe.isCTRecipe, currentRecipe.recipePropertyStorage, currentRecipe.recipeCategory);
    }

    public final boolean matches(boolean consumeIfSuccessful, IItemHandlerModifiable inputs,
                                 IMultipleTankHandler fluidInputs) {
        Pair<Boolean, int[]> fluids = null;
        Pair<Boolean, int[]> items = null;

        if (fluidInputs.getFluidTanks().size() > 0) {
            fluids = matchesFluid(GTUtility.fluidHandlerToList(fluidInputs));
            if (!fluids.getKey()) {
                return false;
            }
        }

        if (inputs.getSlots() > 0) {
            items = matchesItems(GTUtility.itemHandlerToList(inputs));
            if (!items.getKey()) {
                return false;
            }
        }

        if (consumeIfSuccessful) {
            if (fluids != null) {
                int[] fluidAmountInTank = fluids.getValue();
                var backedList = fluidInputs.getFluidTanks();

                for (int i = 0; i < fluidAmountInTank.length; i++) {
                    var tank = backedList.get(i);
                    FluidStack fluidStack = tank.getFluid();
                    int fluidAmount = fluidAmountInTank[i];

                    if (fluidStack == null || fluidStack.amount == fluidAmount) {
                        continue;
                    }
                    tank.drain(Math.abs(fluidAmount - fluidStack.amount), true);
                }
            }
            if (items != null) {
                int[] itemAmountInSlot = items.getValue();
                for (int i = 0; i < itemAmountInSlot.length; i++) {
                    ItemStack itemInSlot = inputs.getStackInSlot(i);
                    int itemAmount = itemAmountInSlot[i];

                    if (itemInSlot.isEmpty() || itemInSlot.getCount() == itemAmount) {
                        continue;
                    }
                    inputs.extractItem(i, Math.abs(itemAmount - itemInSlot.getCount()), false);
                }
            }
        }

        return true;
    }

    /**
     * This methods aim to verify if the current recipe matches the given inputs according to matchingMode mode.
     *
     * @param consumeIfSuccessful if true will consume the inputs of the recipe.
     * @param inputs              Items input or Collections.emptyList() if none.
     * @param fluidInputs         Fluids input or Collections.emptyList() if none.
     * @return true if the recipe matches the given inputs false otherwise.
     */
    public boolean matches(boolean consumeIfSuccessful, List<ItemStack> inputs, List<FluidStack> fluidInputs) {
        if (inputs.size() == 0 && fluidInputs.size() == 0)
            return false;

        Pair<Boolean, int[]> fluids = matchesFluid(fluidInputs);
        if (!fluids.getKey()) {
            return false;
        }

        Pair<Boolean, int[]> items = matchesItems(inputs);
        if (!items.getKey()) {
            return false;
        }

        if (consumeIfSuccessful) {
            int[] fluidAmountInTank = fluids.getValue();

            for (int i = 0; i < fluidAmountInTank.length; i++) {
                FluidStack fluidStack = fluidInputs.get(i);
                int fluidAmount = fluidAmountInTank[i];
                if (fluidStack == null || fluidStack.amount == fluidAmount)
                    continue;
                fluidStack.amount = fluidAmount;
                if (fluidStack.amount == 0)
                    fluidInputs.set(i, null);
            }

            int[] itemAmountInSlot = items.getValue();

            for (int i = 0; i < itemAmountInSlot.length; i++) {
                ItemStack itemInSlot = inputs.get(i);
                int itemAmount = itemAmountInSlot[i];
                if (itemInSlot.isEmpty() || itemInSlot.getCount() == itemAmount)
                    continue;
                itemInSlot.setCount(itemAmountInSlot[i]);
            }
        }

        return true;
    }

    /**
     * Matches {@link #inputs} against {@code inputs} as a bipartite maximum-flow problem (StateMachine migration
     * roadmap, Ingredients/Matching engine phase 2; see {@code gregtech.api.recipes.ingredients.match}'s package
     * JavaDoc for what this solves that a naive greedy pass over the ingredient list can get wrong). Each
     * consumable {@link GTRecipeInput} is wrapped as a {@link Matcher} inline here rather than having
     * {@link GTRecipeInput} itself implement {@link Matcher} (a deliberately narrow adapter, not a redesign of the
     * ingredient type hierarchy: this confines the whole migration to this file, since {@code Matcher}'s only other
     * use today is right here and in {@link #matchesFluid}).
     * <p>
     * Non-consumable ingredients (see {@link GTRecipeInput#isNonConsumable()}) are deliberately excluded from the
     * flow graph: they don't compete for exclusive claim on specific items the way a real consumption does; they
     * only need to verify enough of a matching item is still present after every consumable ingredient's draw is
     * accounted for, matching the legacy algorithm's own "read the running remainder, but never write it back"
     * behavior for non-consumable entries.
     *
     * @return whether every ingredient (consumable and non-consumable alike) is satisfied, and if so, how much of
     *         each slot in {@code inputs} (by position) would remain after consumption.
     */
    private Pair<Boolean, int[]> matchesItems(List<ItemStack> inputs) {
        List<GTRecipeInput> consumable = new ArrayList<>(this.inputs.size());
        List<GTRecipeInput> nonConsumable = new ArrayList<>();
        for (GTRecipeInput ingredient : this.inputs) {
            (ingredient.isNonConsumable() ? nonConsumable : consumable).add(ingredient);
        }

        List<Matcher<ItemStack>> matchers = new ArrayList<>(consumable.size());
        for (GTRecipeInput ingredient : consumable) {
            matchers.add(Matcher.simpleMatcher(ingredient::acceptsStack, ingredient.getAmount()));
        }

        MatchCalculation<ItemStack> calculation = IngredientMatchHelper.matchItems(matchers, inputs);
        long[] consumed = calculation.getMatchResultsForScale(1);
        if (consumed == null) return Pair.of(false, new int[0]); // never read by any caller on failure

        int[] remaining = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack stack = inputs.get(i);
            int available = stack.isEmpty() ? 0 : stack.getCount();
            long drawn = i < consumed.length ? consumed[i] : 0; // EmptyMatchCalculation returns a 0-length array
            remaining[i] = (int) (available - drawn);
        }

        for (GTRecipeInput ingredient : nonConsumable) {
            long needed = ingredient.getAmount();
            for (int i = 0; i < inputs.size() && needed > 0; i++) {
                ItemStack stack = inputs.get(i);
                if (stack.isEmpty() || !ingredient.acceptsStack(stack)) continue;
                needed -= Math.min(remaining[i], needed);
            }
            if (needed > 0) return Pair.of(false, new int[0]);
        }

        return Pair.of(true, remaining);
    }

    /** As {@link #matchesItems}, but for fluids. */
    private Pair<Boolean, int[]> matchesFluid(List<FluidStack> fluidInputs) {
        List<GTRecipeInput> consumable = new ArrayList<>(this.fluidInputs.size());
        List<GTRecipeInput> nonConsumable = new ArrayList<>();
        for (GTRecipeInput ingredient : this.fluidInputs) {
            (ingredient.isNonConsumable() ? nonConsumable : consumable).add(ingredient);
        }

        List<Matcher<FluidStack>> matchers = new ArrayList<>(consumable.size());
        for (GTRecipeInput ingredient : consumable) {
            matchers.add(Matcher.simpleMatcher(ingredient::acceptsFluid, ingredient.getAmount()));
        }

        MatchCalculation<FluidStack> calculation = IngredientMatchHelper.matchFluids(matchers, fluidInputs);
        long[] consumed = calculation.getMatchResultsForScale(1);
        if (consumed == null) return Pair.of(false, new int[0]); // never read by any caller on failure

        int[] remaining = new int[fluidInputs.size()];
        for (int i = 0; i < fluidInputs.size(); i++) {
            FluidStack stack = fluidInputs.get(i);
            int available = stack == null ? 0 : stack.amount;
            long drawn = i < consumed.length ? consumed[i] : 0; // EmptyMatchCalculation returns a 0-length array
            remaining[i] = (int) (available - drawn);
        }

        for (GTRecipeInput ingredient : nonConsumable) {
            long needed = ingredient.getAmount();
            for (int i = 0; i < fluidInputs.size() && needed > 0; i++) {
                FluidStack stack = fluidInputs.get(i);
                if (stack == null || !ingredient.acceptsFluid(stack)) continue;
                needed -= Math.min(remaining[i], needed);
            }
            if (needed > 0) return Pair.of(false, new int[0]);
        }

        return Pair.of(true, remaining);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Recipe recipe = (Recipe) o;
        return hasSameInputs(recipe) && hasSameFluidInputs(recipe);
    }

    private int makeHashCode() {
        int hash = 31 * hashInputs();
        hash = 31 * hash + hashFluidList(this.fluidInputs);
        return hash;
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    private int hashInputs() {
        int hash = 0;
        for (GTRecipeInput recipeIngredient : this.inputs) {
            if (!recipeIngredient.isOreDict()) {
                for (ItemStack is : recipeIngredient.getInputStacks()) {
                    hash = 31 * hash + ItemStackHashStrategy.comparingAll().hashCode(is);
                }
            } else {
                hash = 31 * hash + recipeIngredient.getOreDict();
            }
        }
        return hash;
    }

    private boolean hasSameInputs(Recipe otherRecipe) {
        List<ItemStack> otherStackList = new ObjectArrayList<>(otherRecipe.inputs.size());
        for (GTRecipeInput otherInputs : otherRecipe.inputs) {
            otherStackList.addAll(Arrays.asList(otherInputs.getInputStacks()));
        }
        if (!this.matchesItems(otherStackList).getLeft()) {
            return false;
        }

        List<ItemStack> thisStackList = new ObjectArrayList<>(this.inputs.size());
        for (GTRecipeInput thisInputs : this.inputs) {
            thisStackList.addAll(Arrays.asList(thisInputs.getInputStacks()));
        }
        return otherRecipe.matchesItems(thisStackList).getLeft();
    }

    public static int hashFluidList(@NotNull List<GTRecipeInput> fluids) {
        int hash = 0;
        for (GTRecipeInput fluidInput : fluids) {
            hash = 31 * hash + fluidInput.hashCode();
        }
        return hash;
    }

    private boolean hasSameFluidInputs(Recipe otherRecipe) {
        List<FluidStack> otherFluidList = new ObjectArrayList<>(otherRecipe.fluidInputs.size());
        for (GTRecipeInput otherInputs : otherRecipe.fluidInputs) {
            FluidStack fluidStack = otherInputs.getInputFluidStack();
            otherFluidList.add(fluidStack);
        }
        if (!this.matchesFluid(otherFluidList).getLeft()) {
            return false;
        }

        List<FluidStack> thisFluidsList = new ObjectArrayList<>(this.fluidInputs.size());
        for (GTRecipeInput thisFluidInputs : this.fluidInputs) {
            FluidStack fluidStack = thisFluidInputs.getInputFluidStack();
            thisFluidsList.add(fluidStack);
        }
        return otherRecipe.matchesFluid(thisFluidsList).getLeft();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("inputs", inputs)
                .append("itemOutputProvider", itemOutputProvider)
                .append("fluidInputs", fluidInputs)
                .append("fluidOutputProvider", fluidOutputProvider)
                .append("duration", duration)
                .append("EUt", EUt)
                .append("hidden", hidden)
                .append("CTRecipe", isCTRecipe)
                .append("GSRecipe", groovyRecipe)
                .toString();
    }

    ///////////////////
    // Getters //
    ///////////////////

    public List<GTRecipeInput> getInputs() {
        return inputs;
    }

    /**
     * @return this recipe's item outputs (guaranteed and chance-based alike), the actual source of truth. Use this
     *         provider's own API ({@link ItemOutputProvider#computeOutputs}
     *         to roll a run's actual outputs, {@link ItemOutputProvider#getCompleteOutputs} for a worst-case/JEI
     *         view assuming every chance succeeds, {@link ItemOutputProvider#trim} to cap distinct output count);
     *         those methods are gone, superseded by this provider.
     */
    @NotNull
    public ItemOutputProvider getItemOutputProvider() {
        return itemOutputProvider;
    }

    /**
     * @return this recipe's guaranteed (non-chance-based) item outputs. A convenience view onto
     *         {@link #getItemOutputProvider()} for callers that specifically want the guaranteed/chanced
     *         breakdown (e.g. JEI, CraftTweaker) rather than a rolled or worst-case result; prefer
     *         {@link #getItemOutputProvider()}'s own API when a breakdown isn't what's actually needed.
     */
    @NotNull
    public List<ItemStack> getGuaranteedItemOutputs() {
        return standardItemOutput().getOutputs().getUnrolled();
    }

    /**
     * @return this recipe's chance-based item outputs, as their own concrete entries. See
     *         {@link #getGuaranteedItemOutputs()} for the rationale of this convenience view.
     */
    @NotNull
    public List<ChancedItemOutput> getChancedItemOutputs() {
        List<RollInformation<ItemStack>> rolled = standardItemOutput().getOutputs().recomposeRolled();
        List<ChancedItemOutput> result = new ArrayList<>(rolled.size());
        for (RollInformation<ItemStack> info : rolled) {
            result.add(new ChancedItemOutput(info.value(), (int) info.rollValue(), (int) info.rollBoost()));
        }
        return result;
    }

    /** @return how this recipe's {@link #getChancedItemOutputs()} entries correlate against each other. */
    @NotNull
    public ChancedOutputLogic getItemOutputChanceLogic() {
        return standardItemOutput().getOutputs().getCorrelation();
    }

    /**
     * @return how this recipe's {@link #getChancedItemOutputs()} entries are individually rolled (independent of
     *         {@link #getItemOutputChanceLogic()}, which instead correlates the rolled results against each other;
     *         see {@link RollInterpreter}'s JavaDoc for how the two compose).
     */
    @NotNull
    public RollInterpreter getItemOutputRollInterpreter() {
        return standardItemOutput().getOutputs().getInterpreter();
    }

    private @NotNull StandardItemOutput standardItemOutput() {
        // every Recipe's provider is built by RecipeBuilder as a StandardItemOutput; see that class's JavaDoc.
        return (StandardItemOutput) itemOutputProvider;
    }

    public List<GTRecipeInput> getFluidInputs() {
        return fluidInputs;
    }

    /**
     * @return a {@link FluidOutputProvider} view of this recipe's fluid outputs. See
     *         {@link #getItemOutputProvider()} (its exact item counterpart) for the design rationale and which
     *         legacy methods this supersedes.
     */
    @NotNull
    public FluidOutputProvider getFluidOutputProvider() {
        return fluidOutputProvider;
    }

    /** As {@link #getGuaranteedItemOutputs()}, but for fluids. */
    @NotNull
    public List<FluidStack> getGuaranteedFluidOutputs() {
        return standardFluidOutput().getOutputs().getUnrolled();
    }

    /** As {@link #getChancedItemOutputs()}, but for fluids. */
    @NotNull
    public List<ChancedFluidOutput> getChancedFluidOutputs() {
        List<RollInformation<FluidStack>> rolled = standardFluidOutput().getOutputs().recomposeRolled();
        List<ChancedFluidOutput> result = new ArrayList<>(rolled.size());
        for (RollInformation<FluidStack> info : rolled) {
            result.add(new ChancedFluidOutput(info.value(), (int) info.rollValue(), (int) info.rollBoost()));
        }
        return result;
    }

    /** As {@link #getItemOutputChanceLogic()}, but for fluids. */
    @NotNull
    public ChancedOutputLogic getFluidOutputChanceLogic() {
        return standardFluidOutput().getOutputs().getCorrelation();
    }

    /** As {@link #getItemOutputRollInterpreter()}, but for fluids. */
    @NotNull
    public RollInterpreter getFluidOutputRollInterpreter() {
        return standardFluidOutput().getOutputs().getInterpreter();
    }

    private @NotNull StandardFluidOutput standardFluidOutput() {
        return (StandardFluidOutput) fluidOutputProvider;
    }

    public boolean hasInputFluid(FluidStack fluid) {
        for (GTRecipeInput fluidInput : fluidInputs) {
            FluidStack fluidStack = fluidInput.getInputFluidStack();
            if (fluid.getFluid() == fluidStack.getFluid()) {
                return fluidStack.isFluidEqual(fluid);
            }
        }
        return false;
    }

    public int getDuration() {
        return duration;
    }

    public long getEUt() {
        return EUt;
    }

    /**
     * @return the voltage this recipe requires (or produces) per amp, i.e. exactly {@link #getEUt()}. This is the
     *         preferred name now that {@link #EUt} no
     *         longer necessarily means "total EU/t" for recipes with {@link #getAmperage()} {@code > 1}. Kept as a
     *         separate method from {@link #getEUt()} (rather than replacing it) so the many existing call sites
     *         that only ever dealt with amperage-1 recipes don't need to change: for those, voltage and EU/t are
     *         numerically identical, as they always were.
     */
    public long getVoltage() {
        return EUt;
    }

    /**
     * @return the amperage this recipe requires (or, for a generating recipe, produces) at {@link #getVoltage()}.
     *         {@code 1} for every recipe that doesn't specify otherwise (which, as of this field's introduction,
     *         is all of them). Total power is {@link #getVoltage()} {@code * getAmperage()}.
     */
    public long getAmperage() {
        return amperage;
    }

    /**
     * @return whether this recipe produces power rather than consuming it. See {@link #generating}'s JavaDoc for
     *         why this is an explicit flag rather than derived from {@link #getEUt()}'s sign.
     */
    public boolean isGenerating() {
        return generating;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean getIsCTRecipe() {
        return isCTRecipe;
    }

    public boolean isGroovyRecipe() {
        return groovyRecipe;
    }

    public boolean hasValidInputsForDisplay() {
        for (GTRecipeInput ingredient : inputs) {
            if (ingredient.isOreDict()) {
                if (OreDictionary.getOres(OreDictionary.getOreName(ingredient.getOreDict())).stream()
                        .anyMatch(s -> !s.isEmpty())) {
                    return true;
                }
            } else if (Arrays.stream(ingredient.getInputStacks()).anyMatch(s -> !s.isEmpty())) {
                return true;
            }
        }
        for (GTRecipeInput fluidInput : fluidInputs) {
            FluidStack fluidIngredient = fluidInput.getInputFluidStack();
            if (fluidIngredient != null && fluidIngredient.amount > 0) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public GTRecipeCategory getRecipeCategory() {
        return this.recipeCategory;
    }

    ///////////////////////////////////////////////////////////
    // Property Helper Methods //
    ///////////////////////////////////////////////////////////

    /**
     * @see RecipePropertyStorageImpl#get(RecipeProperty, Object)
     */
    @Contract("_, !null -> !null")
    public <T> @Nullable T getProperty(@NotNull RecipeProperty<T> property, @Nullable T defaultValue) {
        return recipePropertyStorage.get(property, defaultValue);
    }

    /**
     * @see RecipePropertyStorageImpl#contains(RecipeProperty)
     */
    public boolean hasProperty(@NotNull RecipeProperty<?> property) {
        return recipePropertyStorage.contains(property);
    }

    /**
     * @return the property storage
     */
    public @NotNull RecipePropertyStorage propertyStorage() {
        return recipePropertyStorage;
    }
}
