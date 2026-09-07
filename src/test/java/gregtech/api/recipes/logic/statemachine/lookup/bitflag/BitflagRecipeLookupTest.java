package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.util.ValidationResult;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitflagRecipeLookupTest {

    private static final RecipeProperty<Integer> TEST_PROPERTY = new RecipeProperty<Integer>("bitflag_test_property",
            Integer.class) {

        @Override
        public @NotNull NBTBase serialize(@NotNull Object value) {
            return new NBTTagInt((Integer) value);
        }

        @Override
        public @NotNull Object deserialize(@NotNull NBTBase nbt) {
            return ((NBTTagInt) nbt).getInt();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawInfo(Minecraft minecraft, int x, int y, int color, Object value) {}
    };

    /** An AT_MOST filter on {@link #TEST_PROPERTY} against a fixed query value, for testing {@code registerFilter}. */
    private static RecipeNumericFilter<Integer> testFilter(long query) {
        return new RecipeNumericFilter<Integer>() {

            @Override
            public @NotNull RecipeProperty<Integer> recipeProperty() {
                return TEST_PROPERTY;
            }

            @Override
            public RecipeThresholdIndex.@NotNull Comparison comparison() {
                return RecipeThresholdIndex.Comparison.AT_MOST;
            }

            @Override
            public long extractThreshold(@NotNull Integer value) {
                return value;
            }

            @Override
            public Long extractQuery(@NotNull RecipePropertySet properties) {
                return query;
            }
        };
    }

    /** A predicate filter on {@link #TEST_PROPERTY} against a fixed test, for testing {@code registerFilter}. */
    private static RecipePredicateFilter<Integer> testPredicateFilter(java.util.function.Predicate<Integer> test) {
        return new RecipePredicateFilter<Integer>() {

            @Override
            public @NotNull RecipeProperty<Integer> recipeProperty() {
                return TEST_PROPERTY;
            }

            @Override
            public java.util.function.Predicate<Integer> extractQuery(@NotNull RecipePropertySet properties) {
                return test;
            }
        };
    }

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("bitflag_recipe_lookup_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                .itemInputs(3).itemOutputs(3).build();
    }

    /** Registers {@code builder}'s recipe into {@code map} and returns it, since {@code buildAndRegister()} itself returns {@code void}. */
    private static Recipe register(RecipeMap<SimpleRecipeBuilder> map, SimpleRecipeBuilder builder) {
        ValidationResult<Recipe> result = builder.build();
        map.addRecipe(result);
        return result.getResult();
    }

    @Test
    void findRecipesThreeArgOverloadMatchesLikeRecipeMapLookup() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        Recipe recipe = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30));

        RecipeLookup lookup = map.getBitflagLookup();
        Iterator<Recipe> found = lookup.findRecipes(30, Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)),
                Collections.emptyList());

        assertTrue(found.hasNext());
        assertThat(found.next(), is(recipe));
    }

    @Test
    void voltageFilterExcludesRecipesRequiringMoreThanTheSuppliedMaxVoltage() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(1).EUt(120).buildAndRegister();

        RecipeLookup lookup = map.getBitflagLookup();
        Iterator<Recipe> found = lookup.findRecipes(30, Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)),
                Collections.emptyList());

        assertFalse(found.hasNext());
    }

    @Test
    void findRecipesFourArgOverloadAlsoWorks() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        Recipe recipe = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30));

        RecipeLookup lookup = map.getBitflagLookup();
        RecipePropertySet properties = RecipePropertySet.empty();
        properties.add(new PowerSupplyProperty(30, 1));
        List<ItemStack> items = Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1));

        Iterator<Recipe> found = lookup.findRecipes(30, properties, items, Collections.emptyList());

        assertTrue(found.hasNext());
        assertThat(found.next(), is(recipe));
    }

    @Test
    void getBitflagLookupReturnsTheSameCachedInstanceEveryCall() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();

        assertThat(map.getBitflagLookup(), sameInstance(map.getBitflagLookup()));
    }

    @Test
    void invalidatesAndRediscoversRecipesAddedAfterTheFirstSearch() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        BitflagRecipeLookup lookup = map.getBitflagLookup();

        // first search builds and caches the index while the map is still empty
        assertFalse(lookup.findRecipes(30, Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)),
                Collections.emptyList()).hasNext());

        Recipe recipe = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30));

        Iterator<Recipe> found = lookup.findRecipes(30, Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)),
                Collections.emptyList());

        assertTrue(found.hasNext());
        assertThat(found.next(), is(recipe));
    }

    @Test
    void registerFilterExcludesARecipeFailingTheAdditionalPropertyCheck() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30);
        builder.applyProperty(TEST_PROPERTY, 100);
        register(map, builder);

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(testFilter(50)); // AT_MOST: recipe's 100 <= query 50 is false

        Iterator<Recipe> found = lookup.findRecipes(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertFalse(found.hasNext());
    }

    @Test
    void registerFilterIncludesARecipePassingTheAdditionalPropertyCheck() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30);
        builder.applyProperty(TEST_PROPERTY, 100);
        Recipe recipe = register(map, builder);

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(testFilter(200)); // AT_MOST: recipe's 100 <= query 200 is true

        Iterator<Recipe> found = lookup.findRecipes(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertTrue(found.hasNext());
        assertThat(found.next(), is(recipe));
    }

    @Test
    void registerFilterNeverExcludesARecipeThatDoesNotDeclareTheProperty() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        // deliberately does not call applyProperty(TEST_PROPERTY, ...)
        Recipe recipe = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30));

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(testFilter(0)); // would exclude any recipe requiring > 0, if it declared the property

        Iterator<Recipe> found = lookup.findRecipes(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertTrue(found.hasNext());
        assertThat(found.next(), is(recipe));
    }

    @Test
    void registerPredicateFilterExcludesARecipeFailingTheAdditionalPropertyCheck() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30);
        builder.applyProperty(TEST_PROPERTY, 100);
        register(map, builder);

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(testPredicateFilter(value -> value != 100)); // recipe's own value (100) fails this test

        Iterator<Recipe> found = lookup.findRecipes(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertFalse(found.hasNext());
    }

    @Test
    void registerPredicateFilterIncludesARecipePassingTheAdditionalPropertyCheck() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30);
        builder.applyProperty(TEST_PROPERTY, 100);
        Recipe recipe = register(map, builder);

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(testPredicateFilter(value -> value == 100)); // recipe's own value (100) passes this test

        Iterator<Recipe> found = lookup.findRecipes(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertTrue(found.hasNext());
        assertThat(found.next(), is(recipe));
    }

    @Test
    void registerPredicateFilterNeverExcludesARecipeThatDoesNotDeclareTheProperty() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        // deliberately does not call applyProperty(TEST_PROPERTY, ...)
        Recipe recipe = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30));

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(testPredicateFilter(value -> false)); // would exclude any recipe, if it declared the property

        Iterator<Recipe> found = lookup.findRecipes(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertTrue(found.hasNext());
        assertThat(found.next(), is(recipe));
    }

    @Test
    void registerPredicateFilterDoesNothingWhenExtractQueryReturnsNull() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30);
        builder.applyProperty(TEST_PROPERTY, 100);
        Recipe recipe = register(map, builder);

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(new RecipePredicateFilter<Integer>() {

            @Override
            public @NotNull RecipeProperty<Integer> recipeProperty() {
                return TEST_PROPERTY;
            }

            @Override
            public java.util.function.Predicate<Integer> extractQuery(@NotNull RecipePropertySet properties) {
                return null; // filter doesn't apply this search
            }
        });

        Iterator<Recipe> found = lookup.findRecipes(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertTrue(found.hasNext());
        assertThat(found.next(), is(recipe));
    }

    @Test
    void diagnoseNoMatchReportsVoltageExclusionCount() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(1).EUt(120).buildAndRegister();

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        String breakdown = lookup.diagnoseNoMatch(30, null,
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertThat(breakdown, containsString("1 recipe(s)"));
        assertThat(breakdown, containsString("voltage<=30 excludes 1"));
    }

    @Test
    void diagnoseNoMatchReportsRegisteredNumericFilterExclusionByPropertyKey() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30);
        builder.applyProperty(TEST_PROPERTY, 100);
        register(map, builder);

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(testFilter(50)); // AT_MOST: recipe's 100 <= query 50 is false

        String breakdown = lookup.diagnoseNoMatch(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertThat(breakdown, containsString(TEST_PROPERTY.getKey() + " excludes 1"));
    }

    @Test
    void diagnoseNoMatchReportsRegisteredPredicateFilterExclusionByPropertyKey() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(30);
        builder.applyProperty(TEST_PROPERTY, 100);
        register(map, builder);

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(testPredicateFilter(value -> value != 100));

        String breakdown = lookup.diagnoseNoMatch(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertThat(breakdown, containsString(TEST_PROPERTY.getKey() + " excludes 1"));
    }

    @Test
    void diagnoseNoMatchReportsFilterNotApplicableWhenExtractQueryReturnsNull() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        SimpleRecipeBuilder builder = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(120); // excluded by voltage regardless
        builder.applyProperty(TEST_PROPERTY, 100);
        register(map, builder);

        BitflagRecipeLookup lookup = map.getBitflagLookup();
        lookup.registerFilter(new RecipeNumericFilter<Integer>() {

            @Override
            public @NotNull RecipeProperty<Integer> recipeProperty() {
                return TEST_PROPERTY;
            }

            @Override
            public RecipeThresholdIndex.@NotNull Comparison comparison() {
                return RecipeThresholdIndex.Comparison.AT_MOST;
            }

            @Override
            public long extractThreshold(@NotNull Integer value) {
                return value;
            }

            @Override
            public Long extractQuery(@NotNull RecipePropertySet properties) {
                return null; // filter doesn't apply this search
            }
        });

        String breakdown = lookup.diagnoseNoMatch(30, RecipePropertySet.empty(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertThat(breakdown, containsString(TEST_PROPERTY.getKey() + " filter not applicable"));
    }

    @Test
    void diagnoseNoMatchOnRecipeMapLookupDelegatesToTheBitflagLookup() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(1).EUt(120).buildAndRegister();

        RecipeLookup lookup = new RecipeMapLookup(map);
        String breakdown = lookup.diagnoseNoMatch(30, null,
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList());

        assertThat(breakdown, containsString("voltage<=30 excludes 1"));
    }

    @Test
    void diagnoseNoMatchDefaultImplementationReportsDiagnosticsUnsupported() {
        RecipeLookup lookup = (maxVoltage, items, fluids) -> Collections.emptyIterator();

        String breakdown = lookup.diagnoseNoMatch(30, null, Collections.emptyList(), Collections.emptyList());

        assertThat(breakdown, containsString("not supported"));
    }
}
