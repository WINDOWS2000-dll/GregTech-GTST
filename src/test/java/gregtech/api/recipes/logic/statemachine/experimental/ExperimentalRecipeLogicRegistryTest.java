package gregtech.api.recipes.logic.statemachine.experimental;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.RecipeWorkableTieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.statemachine.GTStateMachineBuilder;
import gregtech.api.statemachine.GTStateMachineOperator;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link ExperimentalRecipeLogicRegistry}'s registration gate, overlap detection, exact/assignable target
 * resolution, freeze timing, and the post-processor priority ordering + rollback/circuit-breaker behavior. Every
 * test registers under {@link GTValues#MODID} itself, since {@link Bootstrap#perform()} is the only mod
 * {@code Loader.isModLoaded} will recognize as loaded in this test harness.
 */
class ExperimentalRecipeLogicRegistryTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 40000;

    private static ResourceLocation nextId() {
        return GTUtility.gregtechId("experimental_registry_test_" + testId++);
    }

    @BeforeEach
    void openTheGate() {
        // Also reset here, not just in @AfterEach below: this registry is process-wide static state, and other
        // test classes construct real RecipeWorkable machines (which freeze it -- see ExperimentalRecipeLogicRegistry's
        // JavaDoc on why) without knowing this class exists. Whichever order Gradle/JUnit happens to run classes
        // in must not affect these tests' outcome.
        ExperimentalRecipeLogicRegistry.resetForTesting();
        ConfigHolder.dev.enableExperimentalAddonExtensions = true;
        ConfigHolder.dev.experimentalAddonExtensionsAllowlist = new String[] { GTValues.MODID };
    }

    @AfterEach
    void resetEverything() {
        ExperimentalRecipeLogicRegistry.resetForTesting();
        ConfigHolder.dev.enableExperimentalAddonExtensions = false;
        ConfigHolder.dev.experimentalAddonExtensionsAllowlist = new String[0];
    }

    /** An ordinary machine class unrelated to any of the registry's whitelisted broad target types. */
    private static class PlainTestMachine extends MetaTileEntity {

        PlainTestMachine(ResourceLocation id) {
            super(id);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new PlainTestMachine(metaTileEntityId);
        }
    }

    /** A second, independent ordinary machine class, used to prove non-overlapping registrations coexist. */
    private static class AnotherPlainTestMachine extends MetaTileEntity {

        AnotherPlainTestMachine(ResourceLocation id) {
            super(id);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new AnotherPlainTestMachine(metaTileEntityId);
        }
    }

    /** A concrete subclass of one of the registry's whitelisted broad target types (see class JavaDoc). */
    private static class TieredTestMachine extends RecipeWorkableTieredMetaTileEntity {

        TieredTestMachine(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap, null, 1, GTUtility.defaultTankSizeFunction);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TieredTestMachine(metaTileEntityId, recipeMap);
        }
    }

    private static RecipeMap<SimpleRecipeBuilder> newRecipeMap() {
        return new RecipeMapBuilder<>("experimental_registry_test_map_" + testId, new SimpleRecipeBuilder()).build();
    }

    // ================================================================================================================
    // Gate
    // ================================================================================================================

    @Test
    void blankModidIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentalRecipeLogicRegistry.putExtensionFactory("", PlainTestMachine.class, String.class,
                        m -> "x"));
    }

    @Test
    void unloadedModidIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentalRecipeLogicRegistry.putExtensionFactory("definitely_not_a_loaded_mod",
                        PlainTestMachine.class, String.class, m -> "x"));
    }

    @Test
    void masterSwitchDisabledRejectsRegistration() {
        ConfigHolder.dev.enableExperimentalAddonExtensions = false;

        assertThrows(ExperimentalExtensionsNotPermittedException.class,
                () -> ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class,
                        String.class, m -> "x"));
    }

    @Test
    void modidNotInAllowlistRejectsRegistrationEvenWithMasterSwitchOn() {
        ConfigHolder.dev.experimentalAddonExtensionsAllowlist = new String[0];

        assertThrows(ExperimentalExtensionsNotPermittedException.class,
                () -> ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class,
                        String.class, m -> "x"));
    }

    @Test
    void frozenRegistryRejectsFurtherRegistration() {
        // Freezing is tied to first actual resolution, exactly as a real machine's construction would trigger it.
        ExperimentalRecipeLogicRegistry.resolveExtensionsFor(new PlainTestMachine(nextId()),
                new ExperimentalConfigExtensions());

        ExperimentalRegistryFrozenException ex = assertThrows(ExperimentalRegistryFrozenException.class,
                () -> ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class,
                        String.class, m -> "x"));
        assertThat(ex.getModid(), is(GTValues.MODID));
    }

    // ================================================================================================================
    // putExtensionFactory: overlap detection
    // ================================================================================================================

    @Test
    void overlappingExactTargetTypesForSameExtensionTypeThrowsConflict() {
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class, String.class,
                m -> "first");

        ExperimentalExtensionConflictException ex = assertThrows(ExperimentalExtensionConflictException.class,
                () -> ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class,
                        String.class, m -> "second"));

        assertThat(ex.getExtensionType(), is(String.class));
        assertThat(ex.getConflictingModid(), is(GTValues.MODID));
        assertThat("the cause carries the original registration's stack trace", ex.getCause(), is(notNullValue()));
    }

    @Test
    void overlappingViaAssignabilityForSameExtensionTypeThrowsConflict() {
        // RecipeWorkableTieredMetaTileEntity is whitelisted, so a narrower registration for its own subclass
        // overlaps: any TieredTestMachine instance would match both.
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, RecipeWorkableTieredMetaTileEntity.class,
                String.class, m -> "broad");

        assertThrows(ExperimentalExtensionConflictException.class,
                () -> ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, TieredTestMachine.class,
                        String.class, m -> "narrow"));
    }

    @Test
    void nonOverlappingTargetTypesForSameExtensionTypeCoexist() {
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class, String.class,
                m -> "plain");
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, AnotherPlainTestMachine.class,
                String.class, m -> "another");

        ExperimentalConfigExtensions targetA = new ExperimentalConfigExtensions();
        ExperimentalRecipeLogicRegistry.resolveExtensionsFor(new PlainTestMachine(nextId()), targetA);
        assertThat(targetA.getExtension(String.class), is("plain"));

        ExperimentalConfigExtensions targetB = new ExperimentalConfigExtensions();
        ExperimentalRecipeLogicRegistry.resolveExtensionsFor(new AnotherPlainTestMachine(nextId()), targetB);
        assertThat(targetB.getExtension(String.class), is("another"));
    }

    @Test
    void differentExtensionTypesForTheSameTargetTypeDoNotConflict() {
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class, String.class,
                m -> "a string");
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class, Integer.class,
                m -> 42);

        ExperimentalConfigExtensions target = new ExperimentalConfigExtensions();
        ExperimentalRecipeLogicRegistry.resolveExtensionsFor(new PlainTestMachine(nextId()), target);

        assertThat(target.getExtension(String.class), is("a string"));
        assertThat(target.getExtension(Integer.class), is(42));
    }

    // ================================================================================================================
    // resolveExtensionsFor: exact vs. whitelisted-assignable matching
    // ================================================================================================================

    @Test
    void exactTargetTypeMatchResolvesTheRegisteredFactory() {
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, PlainTestMachine.class, String.class,
                m -> "resolved");

        ExperimentalConfigExtensions target = new ExperimentalConfigExtensions();
        ExperimentalRecipeLogicRegistry.resolveExtensionsFor(new PlainTestMachine(nextId()), target);

        assertThat(target.getExtension(String.class), is("resolved"));
    }

    @Test
    void nonMatchingTargetTypeDoesNotResolve() {
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, AnotherPlainTestMachine.class,
                String.class, m -> "should not apply");

        ExperimentalConfigExtensions target = new ExperimentalConfigExtensions();
        ExperimentalRecipeLogicRegistry.resolveExtensionsFor(new PlainTestMachine(nextId()), target);

        assertThat(target.getExtension(String.class), is(nullValue()));
    }

    @Test
    void assignableMatchAppliesForWhitelistedBroadTargetType() {
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, RecipeWorkableTieredMetaTileEntity.class,
                String.class, m -> "broad match");

        MetaTileEntity owner = new TieredTestMachine(nextId(), newRecipeMap());
        ExperimentalConfigExtensions target = new ExperimentalConfigExtensions();
        ExperimentalRecipeLogicRegistry.resolveExtensionsFor(owner, target);

        assertThat(target.getExtension(String.class), is("broad match"));
    }

    @Test
    void assignableMatchDoesNotApplyForNonWhitelistedBroadTargetType() {
        // MetaTileEntity itself is not in the fixed whitelist, so this registration can only ever exact-match
        // MetaTileEntity.class itself -- which no concrete machine's getClass() ever equals.
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, MetaTileEntity.class, String.class,
                m -> "should never resolve");

        ExperimentalConfigExtensions target = new ExperimentalConfigExtensions();
        ExperimentalRecipeLogicRegistry.resolveExtensionsFor(new PlainTestMachine(nextId()), target);

        assertThat(target.getExtension(String.class), is(nullValue()));
    }

    // ================================================================================================================
    // addPostProcessor: priority ordering
    // ================================================================================================================

    @Test
    void postProcessorsRunInDescendingPriorityOrder() {
        List<String> order = new ArrayList<>();
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.LOW, b -> order.add("low"));
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.HIGHEST, b -> order.add("highest"));
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.NORMAL, b -> order.add("normal"));

        ExperimentalRecipeLogicRegistry.applyPostProcessorsFor(new PlainTestMachine(nextId()),
                new GTStateMachineBuilder());

        assertThat(order, is(Arrays.asList("highest", "normal", "low")));
    }

    @Test
    void rawIntPriorityCanInterleaveBetweenEnumSteps() {
        List<String> order = new ArrayList<>();
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.NORMAL, b -> order.add("normal"));
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class, 500_000,
                b -> order.add("between normal and high"));
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.HIGH, b -> order.add("high"));

        ExperimentalRecipeLogicRegistry.applyPostProcessorsFor(new PlainTestMachine(nextId()),
                new GTStateMachineBuilder());

        assertThat(order, is(Arrays.asList("high", "between normal and high", "normal")));
    }

    @Test
    void equalPriorityRunsInRegistrationOrder() {
        List<String> order = new ArrayList<>();
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class, 0,
                b -> order.add("first"));
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class, 0,
                b -> order.add("second"));
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class, 0,
                b -> order.add("third"));

        ExperimentalRecipeLogicRegistry.applyPostProcessorsFor(new PlainTestMachine(nextId()),
                new GTStateMachineBuilder());

        assertThat(order, is(Arrays.asList("first", "second", "third")));
    }

    @Test
    void postProcessorTargetingAnUnrelatedTypeDoesNotRun() {
        List<String> order = new ArrayList<>();
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, AnotherPlainTestMachine.class,
                ExperimentalPriority.NORMAL, b -> order.add("should not run"));

        ExperimentalRecipeLogicRegistry.applyPostProcessorsFor(new PlainTestMachine(nextId()),
                new GTStateMachineBuilder());

        assertThat(order, is(Collections.emptyList()));
    }

    // ================================================================================================================
    // addPostProcessor: rollback + circuit breaker
    // ================================================================================================================

    @Test
    void postProcessorThrowingRollsBackOnlyItsOwnPartialChangesLeavingEarlierOnesIntact() {
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.HIGH,
                b -> b.newOperator(GTStateMachineOperator.emptyOp(), false, "survives"));
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.NORMAL, b -> {
                    b.newOperator(GTStateMachineOperator.emptyOp(), false, "partialEditBeforeThrow");
                    throw new RuntimeException("boom");
                });

        GTStateMachineBuilder builder = new GTStateMachineBuilder();
        int before = builder.getConstructing().operatorCount();

        GTStateMachineBuilder result = ExperimentalRecipeLogicRegistry.applyPostProcessorsFor(
                new PlainTestMachine(nextId()), builder);

        // Only the first (successful) post-processor's node survives; the second's partial edit never happened.
        assertThat(result.getConstructing().operatorCount(), is(before + 1));
    }

    @Test
    void postProcessorThatThrowsIsPermanentlyDisabledAfterItsFirstFailure() {
        List<String> ranEachTime = new ArrayList<>();
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.HIGH, b -> ranEachTime.add("good"));
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, PlainTestMachine.class,
                ExperimentalPriority.NORMAL, b -> {
                    ranEachTime.add("bad");
                    throw new RuntimeException("boom");
                });

        // First machine construction: both attempt to run, the bad one fails and trips the breaker.
        ExperimentalRecipeLogicRegistry.applyPostProcessorsFor(new PlainTestMachine(nextId()),
                new GTStateMachineBuilder());
        assertThat(ranEachTime, is(Arrays.asList("good", "bad")));

        // Second machine construction of the same type: the disabled entry must not be invoked again.
        ranEachTime.clear();
        ExperimentalRecipeLogicRegistry.applyPostProcessorsFor(new PlainTestMachine(nextId()),
                new GTStateMachineBuilder());
        assertThat(ranEachTime, is(Collections.singletonList("good")));
    }

    @Test
    void applyPostProcessorsForReturnsTheOriginalBuilderUnchangedWhenNoneApply() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder();

        GTStateMachineBuilder result = ExperimentalRecipeLogicRegistry.applyPostProcessorsFor(
                new PlainTestMachine(nextId()), builder);

        assertThat(result, is(builder));
    }
}
