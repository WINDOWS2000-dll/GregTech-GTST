package gregtech.common.metatileentities.multi.electric;

import gregtech.Bootstrap;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.AssemblyLineRecipeBuilder;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import gregtech.api.recipes.properties.impl.ResearchPropertyData;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaTileEntityAssemblyLineTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20600;

    @AfterEach
    void restoreConfig() {
        ConfigHolder.machines.orderedAssembly = true;
        ConfigHolder.machines.orderedFluidAssembly = false;
        ConfigHolder.machines.enableResearch = true;
    }

    /**
     * A fake structural part exposing exactly one ability, both for pattern-matching purposes
     * ({@link #getPatternAbilities()}) and for aggregation ({@link #registerAbilities}) -- i.e. a genuine Import
     * Item Bus/Fluid Import Hatch stand-in, not a part that only incidentally aggregates into another ability's
     * pool (see {@link FakeFluidHatchWithGhostCircuit} for that case).
     */
    private static class FakeAbilityPart<T> implements IMultiblockAbilityPart<T> {

        private final MultiblockAbility<T> ability;
        private final T instance;

        FakeAbilityPart(MultiblockAbility<T> ability, T instance) {
            this.ability = ability;
            this.instance = instance;
        }

        @Override
        public MultiblockAbility<T> getAbility() {
            return ability;
        }

        @Override
        public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
            if (abilityInstances.isKey(ability)) abilityInstances.add(instance);
        }

        @Override
        public boolean isAttachedToMultiBlock() {
            return true;
        }

        @Override
        public void addToMultiBlock(MultiblockControllerBase controllerBase) {}

        @Override
        public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {}
    }

    private static class FakeFluidHatchWithGhostCircuit implements IMultiblockAbilityPart<Object> {

        private final IFluidTank tank;
        private final IItemHandlerModifiable ghostCircuit;

        FakeFluidHatchWithGhostCircuit(IFluidTank tank, IItemHandlerModifiable ghostCircuit) {
            this.tank = tank;
            this.ghostCircuit = ghostCircuit;
        }

        @Override
        public @NotNull List<MultiblockAbility<?>> getAbilities() {
            return Arrays.asList(MultiblockAbility.IMPORT_FLUIDS, MultiblockAbility.IMPORT_ITEMS);
        }

        @Override
        public @NotNull List<MultiblockAbility<?>> getPatternAbilities() {
            return Collections.singletonList(MultiblockAbility.IMPORT_FLUIDS);
        }

        @Override
        public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
            if (abilityInstances.isKey(MultiblockAbility.IMPORT_FLUIDS)) abilityInstances.add(tank);
            else if (abilityInstances.isKey(MultiblockAbility.IMPORT_ITEMS)) abilityInstances.add(ghostCircuit);
        }

        @Override
        public boolean isAttachedToMultiBlock() {
            return true;
        }

        @Override
        public void addToMultiBlock(MultiblockControllerBase controllerBase) {}

        @Override
        public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {}
    }

    private static class TestAssemblyLine extends MetaTileEntityAssemblyLine {

        List<IMultiblockPart> fakeParts = Collections.emptyList();
        List<IDataAccessHatch> fakeDataHatches = Collections.emptyList();

        TestAssemblyLine(ResourceLocation id) {
            super(id);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestAssemblyLine(metaTileEntityId);
        }

        @Override
        public boolean isStructureFormed() {
            return true;
        }

        @Nullable
        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }

        @Override
        public List<IMultiblockPart> getMultiblockParts() {
            return fakeParts;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
            if (ability == MultiblockAbility.DATA_ACCESS_HATCH) return (List<T>) fakeDataHatches;
            if (ability == MultiblockAbility.OPTICAL_DATA_RECEPTION) return (List<T>) Collections.emptyList();
            return super.getAbilities(ability);
        }
    }

    private static TestAssemblyLine newHost() {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestAssemblyLine(GTUtility.gregtechId("assembly_line_test_" + testId)));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestAssemblyLine) holder;
    }

    private static IItemHandlerModifiable busOf(@NotNull ItemStack stack) {
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, stack);
        return handler;
    }

    private static IMultiblockPart itemBusPart(@NotNull ItemStack stack) {
        return new FakeAbilityPart<>(MultiblockAbility.IMPORT_ITEMS, busOf(stack));
    }

    private static IFluidTank tankOf(@Nullable FluidStack stack) {
        FluidTank tank = new FluidTank(Integer.MAX_VALUE);
        tank.setFluid(stack);
        return tank;
    }

    private static IMultiblockPart fluidHatchPart(@Nullable FluidStack stack) {
        return new FakeAbilityPart<>(MultiblockAbility.IMPORT_FLUIDS, tankOf(stack));
    }

    /** A fluid hatch whose ghost circuit slot is always empty, exactly like a real one nobody put a circuit in. */
    private static IMultiblockPart fluidHatchWithGhostCircuitPart(@Nullable FluidStack stack) {
        return new FakeFluidHatchWithGhostCircuit(tankOf(stack), new ItemStackHandler(1));
    }

    private static IDataAccessHatch hatch(boolean creative, boolean recipeAvailable) {
        return new IDataAccessHatch() {

            @Override
            public boolean isRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen) {
                return recipeAvailable;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
    }

    private static RecipeMap<AssemblyLineRecipeBuilder> newRecipeMap() {
        return new RecipeMapBuilder<>("assembly_line_test_" + testId, new AssemblyLineRecipeBuilder())
                .itemInputs(2).itemOutputs(1).fluidInputs(1).build();
    }

    @Test
    void acceptsANonOrderedRecipeRegardlessOfBusLayoutWhenOrderedAssemblyIsDisabled() {
        ConfigHolder.machines.orderedAssembly = false;
        ConfigHolder.machines.enableResearch = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        Recipe recipe = map.recipeBuilder().inputs(new ItemStack(Blocks.IRON_ORE), new ItemStack(Blocks.GOLD_ORE))
                .outputs(new ItemStack(Blocks.STONE)).duration(100).EUt(30).build().getResult();

        TestAssemblyLine al = newHost();
        // buses in the "wrong" order relative to the recipe's own input order
        al.fakeParts = Arrays.asList(itemBusPart(new ItemStack(Blocks.GOLD_ORE)),
                itemBusPart(new ItemStack(Blocks.IRON_ORE)));

        assertTrue(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }

    @Test
    void rejectsARecipeWhoseBusOrderDoesNotMatchWhenOrderedAssemblyIsEnabled() {
        ConfigHolder.machines.enableResearch = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        Recipe recipe = map.recipeBuilder().inputs(new ItemStack(Blocks.IRON_ORE), new ItemStack(Blocks.GOLD_ORE))
                .outputs(new ItemStack(Blocks.STONE)).duration(100).EUt(30).build().getResult();

        TestAssemblyLine al = newHost();
        // bus 0 holds input 1's item and vice versa: wrong order
        al.fakeParts = Arrays.asList(itemBusPart(new ItemStack(Blocks.GOLD_ORE)),
                itemBusPart(new ItemStack(Blocks.IRON_ORE)));

        assertFalse(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }

    @Test
    void acceptsARecipeWhoseBusOrderMatchesWhenOrderedAssemblyIsEnabled() {
        ConfigHolder.machines.enableResearch = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        Recipe recipe = map.recipeBuilder().inputs(new ItemStack(Blocks.IRON_ORE), new ItemStack(Blocks.GOLD_ORE))
                .outputs(new ItemStack(Blocks.STONE)).duration(100).EUt(30).build().getResult();

        TestAssemblyLine al = newHost();
        al.fakeParts = Arrays.asList(itemBusPart(new ItemStack(Blocks.IRON_ORE)),
                itemBusPart(new ItemStack(Blocks.GOLD_ORE)));

        assertTrue(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }

    @Test
    void rejectsARecipeWhenFewerBusesArePresentThanTheRecipeHasInputs() {
        ConfigHolder.machines.enableResearch = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        Recipe recipe = map.recipeBuilder().inputs(new ItemStack(Blocks.IRON_ORE), new ItemStack(Blocks.GOLD_ORE))
                .outputs(new ItemStack(Blocks.STONE)).duration(100).EUt(30).build().getResult();

        TestAssemblyLine al = newHost();
        al.fakeParts = Collections.singletonList(itemBusPart(new ItemStack(Blocks.IRON_ORE))); // only 1, needs 2

        assertFalse(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }

    @Test
    void rejectsARecipeWhoseFluidHatchOrderDoesNotMatchWhenOrderedFluidAssemblyIsEnabled() {
        // orderedFluidAssembly is only consulted when orderedAssembly is also enabled -- legacy's checkRecipe
        // nests the fluid check inside the item check's own if-block, and this migration preserves that verbatim.
        ConfigHolder.machines.enableResearch = false;
        ConfigHolder.machines.orderedAssembly = true;
        ConfigHolder.machines.orderedFluidAssembly = true;
        Fluid fluidA = new Fluid("assembly_line_test_fluid_a", null, null);
        Fluid fluidB = new Fluid("assembly_line_test_fluid_b", null, null);
        FluidRegistry.registerFluid(fluidA);
        FluidRegistry.registerFluid(fluidB);
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        Recipe recipe = map.recipeBuilder().inputs(new ItemStack(Blocks.IRON_ORE))
                .fluidInputs(new FluidStack(fluidA, 1000))
                .outputs(new ItemStack(Blocks.STONE)).duration(100).EUt(30).build().getResult();

        TestAssemblyLine al = newHost();
        al.fakeParts = Arrays.asList(itemBusPart(new ItemStack(Blocks.IRON_ORE)), // item order matches
                fluidHatchPart(new FluidStack(fluidB, 1000))); // wrong fluid

        assertFalse(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }

    @Test
    void ignoresFluidHatchGhostCircuitSlotsWhenComputingOrderedItemBusPositions() {
        ConfigHolder.machines.enableResearch = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        Recipe recipe = map.recipeBuilder().inputs(new ItemStack(Blocks.IRON_ORE), new ItemStack(Blocks.GOLD_ORE))
                .outputs(new ItemStack(Blocks.STONE)).duration(100).EUt(30).build().getResult();

        TestAssemblyLine al = newHost();
        // mirrors "FIF" per aisle row: a ghost-circuit-capable fluid hatch on both sides of every real bus
        al.fakeParts = new ArrayList<>(Arrays.asList(
                fluidHatchWithGhostCircuitPart(null),
                itemBusPart(new ItemStack(Blocks.IRON_ORE)),
                fluidHatchWithGhostCircuitPart(null),
                fluidHatchWithGhostCircuitPart(null),
                itemBusPart(new ItemStack(Blocks.GOLD_ORE)),
                fluidHatchWithGhostCircuitPart(null)));

        assertTrue(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe),
                "the interleaved ghost circuit slots must not shift bus indices out of alignment");
    }

    @Test
    void rejectsAResearchGatedRecipeWhenNoHatchReportsItAvailable() {
        ConfigHolder.machines.orderedAssembly = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        ResearchPropertyData data = new ResearchPropertyData();
        data.add(new ResearchPropertyData.ResearchEntry("assembly_line_test_research",
                new ItemStack(Items.PAPER)));
        AssemblyLineRecipeBuilder builder = map.recipeBuilder().outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(30);
        builder.applyProperty(ResearchProperty.getInstance(), data);
        Recipe recipe = builder.build().getResult();

        TestAssemblyLine al = newHost();
        al.fakeDataHatches = Collections.singletonList(hatch(false, false));

        assertFalse(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }

    @Test
    void acceptsAResearchGatedRecipeWhenAHatchReportsItAvailable() {
        ConfigHolder.machines.orderedAssembly = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        ResearchPropertyData data = new ResearchPropertyData();
        data.add(new ResearchPropertyData.ResearchEntry("assembly_line_test_research",
                new ItemStack(Items.PAPER)));
        AssemblyLineRecipeBuilder builder = map.recipeBuilder().outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(30);
        builder.applyProperty(ResearchProperty.getInstance(), data);
        Recipe recipe = builder.build().getResult();

        TestAssemblyLine al = newHost();
        al.fakeDataHatches = Collections.singletonList(hatch(false, true));

        assertTrue(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }

    @Test
    void acceptsAResearchGatedRecipeWhenAHatchIsCreative() {
        ConfigHolder.machines.orderedAssembly = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        ResearchPropertyData data = new ResearchPropertyData();
        data.add(new ResearchPropertyData.ResearchEntry("assembly_line_test_research",
                new ItemStack(Items.PAPER)));
        AssemblyLineRecipeBuilder builder = map.recipeBuilder().outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(30);
        builder.applyProperty(ResearchProperty.getInstance(), data);
        Recipe recipe = builder.build().getResult();

        TestAssemblyLine al = newHost();
        al.fakeDataHatches = Collections.singletonList(hatch(true, false));

        assertTrue(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }

    @Test
    void ignoresTheResearchGateWhenResearchIsDisabled() {
        ConfigHolder.machines.orderedAssembly = false;
        ConfigHolder.machines.enableResearch = false;
        RecipeMap<AssemblyLineRecipeBuilder> map = newRecipeMap();
        ResearchPropertyData data = new ResearchPropertyData();
        data.add(new ResearchPropertyData.ResearchEntry("assembly_line_test_research",
                new ItemStack(Items.PAPER)));
        AssemblyLineRecipeBuilder builder = map.recipeBuilder().outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(30);
        builder.applyProperty(ResearchProperty.getInstance(), data);
        Recipe recipe = builder.build().getResult();

        TestAssemblyLine al = newHost();
        al.fakeDataHatches = Collections.emptyList(); // no hatch at all, would fail if the gate were still applied

        assertTrue(al.getWorkable().getConfig().hooks.recipeSearchPredicate.test(recipe));
    }
}
