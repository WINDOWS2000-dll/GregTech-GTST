package gregtech.api.recipes.logic.statemachine.experimental;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.RecipeWorkableTieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Exercises the whole path an addon actually goes through: register against {@link ExperimentalRecipeLogicRegistry}
 * before any machine exists, then construct a real {@code RecipeWorkableTieredMetaTileEntity} (which drives
 * {@code RecipeLogicGraphBuilder.build(config, owner)} exactly as production code does, via
 * {@link RecipeWorkable}'s constructor) and confirm both the resolved extension value and the post-processor's
 * effect are visible on that specific instance. Unlike {@link ExperimentalRecipeLogicRegistryTest}, which calls the
 * registry's resolution methods directly, this test never touches {@link ExperimentalRecipeLogicRegistry} or
 * {@code RecipeLogicGraphBuilder} itself after registering &mdash; everything past that point is exactly what an
 * addon and a player placing the machine would experience.
 */
class ExperimentalExtensionEndToEndTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 41000;

    private static ResourceLocation nextId() {
        return GTUtility.gregtechId("experimental_e2e_test_" + testId++);
    }

    @BeforeEach
    void openTheGate() {
        // See ExperimentalRecipeLogicRegistryTest's own JavaDoc for why this also resets on entry, not just exit.
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

    /**
     * Mirrors {@code RecipeWorkableTieredMetaTileEntityTest}'s own minimal concrete subclass, plus one addition:
     * exposing the inherited {@code protected workable} field so this test (in a different package) can inspect
     * it, exactly as {@code RecipeWorkableTieredMetaTileEntity} itself does internally.
     */
    private static class TestMachine extends RecipeWorkableTieredMetaTileEntity {

        TestMachine(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap, null, 1, GTUtility.defaultTankSizeFunction);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestMachine(metaTileEntityId, recipeMap);
        }

        RecipeWorkable exposedWorkable() {
            return workable;
        }
    }

    /**
     * The kind of plain data class an addon is recommended to register wholesale as a single {@code extensionType}
     * (see {@link ExperimentalRecipeLogicRegistry}'s JavaDoc on recommended addon-side patterns): public mutable
     * fields, no builder, exactly GTST's own {@code config.io}-style convention.
     */
    private static class FakeAddonConfig {

        public int customField = 0;
    }

    private static RecipeMap<SimpleRecipeBuilder> newRecipeMap() {
        return new RecipeMapBuilder<>("experimental_e2e_test_map_" + testId, new SimpleRecipeBuilder()).build();
    }

    @Test
    void addonRegisteredExtensionFactoryAndPostProcessorBothApplyToAReallyConstructedMachine() {
        List<String> postProcessorRan = new ArrayList<>();

        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, TestMachine.class, FakeAddonConfig.class,
                m -> {
                    FakeAddonConfig cfg = new FakeAddonConfig();
                    cfg.customField = 99;
                    return cfg;
                });
        ExperimentalRecipeLogicRegistry.addPostProcessor(GTValues.MODID, TestMachine.class,
                ExperimentalPriority.NORMAL, b -> postProcessorRan.add("ran"));

        // This single call is the entire production path: TestMachine's constructor -> RecipeWorkable's constructor
        // -> RecipeLogicGraphBuilder.build(config, this) -> the registry's resolve/apply methods. Nothing in this
        // test drives any of that machinery directly.
        TestMachine mte = new TestMachine(nextId(), newRecipeMap());

        FakeAddonConfig resolved = mte.exposedWorkable().getConfig().experimental.getExtension(FakeAddonConfig.class);
        assertThat(resolved, is(notNullValue()));
        assertThat(resolved.customField, is(99));
        assertThat(postProcessorRan, is(Collections.singletonList("ran")));
    }

    @Test
    void aMachineOfAnUnregisteredTypeConstructsNormallyWithNoExtensionsResolved() {
        // MetaTileEntity itself is not in the fixed whitelist (see ExperimentalRecipeLogicRegistry's JavaDoc), so
        // this can only ever exact-match MetaTileEntity.class itself -- never TestMachine's concrete class.
        ExperimentalRecipeLogicRegistry.putExtensionFactory(GTValues.MODID, MetaTileEntity.class,
                FakeAddonConfig.class, m -> new FakeAddonConfig());

        TestMachine mte = new TestMachine(nextId(), newRecipeMap());

        assertThat(mte.exposedWorkable().getConfig().experimental.getExtension(FakeAddonConfig.class),
                is(nullValue()));
    }

    @Test
    void withNothingRegisteredAtAllTheMachineConstructsExactlyAsBefore() {
        TestMachine mte = new TestMachine(nextId(), newRecipeMap());

        assertThat(mte.exposedWorkable().getConfig().experimental.getExtension(FakeAddonConfig.class),
                is(nullValue()));
        assertThat(mte.exposedWorkable().getStateMachine(), is(notNullValue()));
    }
}
