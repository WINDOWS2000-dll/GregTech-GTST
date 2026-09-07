package gregtech.common.metatileentities.electric;

import gregtech.api.metatileentity.AdjacentBlockGate;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.RecipeWorkableSimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * As {@code MetaTileEntityRockBreaker} (legacy), but backed by {@link RecipeWorkableSimpleMachineMetaTileEntity}.
 * <p>
 * The legacy class's hand-written lava/water adjacency scan is generalized into the reusable
 * {@link AdjacentBlockGate}, wired into {@code RecipeLogicHooks#shouldStartRecipeLookup} &mdash; a hook that exists
 * for exactly this "too many problems, don't even search" purpose but had no real consumer until now (see that
 * field's JavaDoc). Like the legacy {@code shouldSearchForRecipes()} override it replaces, this only gates
 * <i>starting</i> a new search while idle; an already-queued/active recipe keeps progressing even if the fluids
 * become momentarily invalid.
 */
public class RecipeWorkableRockBreakerMetaTileEntity extends RecipeWorkableSimpleMachineMetaTileEntity {

    private static final String HAS_VALID_FLUIDS_KEY = "hasValidFluids";

    private static final List<Predicate<IBlockState>> REQUIRED_FLUID_CATEGORIES = Arrays.asList(
            state -> state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.FLOWING_LAVA,
            state -> state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.FLOWING_WATER);

    // Excludes the front facing and vertical sides, exactly like the legacy hand-written scan did.
    private final AdjacentBlockGate fluidsGate = new AdjacentBlockGate(this,
            side -> side != getFrontFacing() && !side.getAxis().isVertical(), REQUIRED_FLUID_CATEGORIES);

    public RecipeWorkableRockBreakerMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                                   ICubeRenderer renderer, int tier) {
        super(metaTileEntityId, recipeMap, renderer, tier, true);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new RecipeWorkableRockBreakerMetaTileEntity(metaTileEntityId, recipeMap, renderer, getTier());
    }

    // Safe despite running before this.fluidsGate is assigned (see MetaTileEntity's constructor-ordering JavaDoc
    // elsewhere in this migration): the lambda only reads the field when actually invoked, well after construction.
    @Override
    protected @NotNull RecipeLogicConfig createConfig(@NotNull RecipeMap<?> recipeMap) {
        RecipeLogicConfig config = super.createConfig(recipeMap);
        // AND, not replace: super's idle-only gate (workable.getActiveRecipeCount() == 0) is a real-machine
        // performance fix (2026-08-31, see RecipeWorkableTieredMetaTileEntity.createConfig()) that every machine
        // needs, not just the ones without their own gate -- overwriting it here would silently lose it and go
        // back to scanning the whole RecipeMap every tick even while a recipe is already active.
        Predicate<NBTTagCompound> idleOnly = config.hooks.shouldStartRecipeLookup;
        config.hooks.shouldStartRecipeLookup = data -> idleOnly.test(data) && fluidsGate.isSatisfied();
        return config;
    }

    @Override
    public void onNeighborChanged() {
        super.onNeighborChanged();
        fluidsGate.recompute();
    }

    @Override
    public void addNotifiedInput(Object input) {
        super.addNotifiedInput(input);
        fluidsGate.recompute();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        fluidsGate.writeToNBT(data, HAS_VALID_FLUIDS_KEY);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        fluidsGate.readFromNBT(data, HAS_VALID_FLUIDS_KEY);
    }

    @Override
    public boolean getIsWeatherOrTerrainResistant() {
        return true;
    }
}
