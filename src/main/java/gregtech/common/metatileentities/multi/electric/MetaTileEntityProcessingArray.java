package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.IMachineHatchMultiblock;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.DummyCleanroom;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.recipes.logic.statemachine.lookup.DynamicRecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.EnergyContainerProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Replaces the legacy {@code ProcessingArrayWorkable} (a
 * {@code MultiblockRecipeLogic} subclass) with plain {@code createConfig()}/{@code createWorkable()} wiring, exactly
 * like every other migrated machine (dynamic {@link RecipeMap} switching, the reactor-MK-style voltage clamp, and
 * the Machine Hatch compatibility fix below).
 * <p>
 * <b>Dynamic {@link RecipeMap} (the inserted machine's own):</b> unlike every other machine so far, this class has
 * no {@link RecipeMap} of its own at all -- {@code super(metaTileEntityId, null)} -- and instead reports whichever
 * child machine currently occupies its Machine Hatch slot via {@link #activeRecipeMap}, re-derived only when the
 * slot's contents actually change ({@link #machineChanged}, mirroring legacy's own lazy-recompute pattern).
 * {@link #createConfig()} wires this in two places: {@code config.lookup} (via {@link DynamicRecipeMapLookup}, so
 * every tick's search targets the right map) and {@link #createWorkable} (overriding
 * {@link RecipeWorkable#getRecipeMap()} itself, since the JEI/external-facing {@link
 * gregtech.api.capability.IHasRecipeMap#getRecipeMap()} contract can't reflect a moving target through the
 * constructor-fixed field the default implementation uses -- see that method's own JavaDoc for why it anticipated
 * exactly this).
 * <p>
 * <b>No dedicated overclock operator needed (simpler than Fusion Reactor/Electric Blast Furnace):</b> legacy's
 * {@code getNumberOfOCs}/{@code getOverclockForTier} manually clamped the achievable overclock tier to the inserted
 * machine's own voltage tier, and separately compensated for GregTech's old parallel model (dividing the candidate's
 * EUt by {@code parallelRecipesPerformed} before computing its tier) -- a correction Stage 3's voltage/amperage
 * separation made structurally obsolete (a candidate's own per-unit voltage is never inflated by parallel scaling
 * in this engine to begin with). The
 * remaining clamp (don't overclock past whichever is lower, the inserted machine's own tier or this array's actual
 * supply) is expressed entirely through {@code config.power.properties} advertising a pre-clamped voltage below --
 * {@link gregtech.api.recipes.logic.statemachine.lookup.RecipeOverclockOperator}'s standard tier-difference
 * calculation does the rest with no customization at all. Keeping both halves of the clamp (inserted machine's
 * tier and this array's own actual supply), like legacy did, is a deliberate improvement over trusting the
 * inserted machine's tier alone.
 * <p>
 * <b>Cleanroom/dimension requirements:</b> legacy delegated a candidate's cleanroom/
 * dimension check to the inserted machine's own {@code AbstractRecipeLogic#checkRecipe}, which is exactly what made
 * this machine the concrete trigger for the "Machine Hatch fails against any already-migrated machine" regression
 * (that machine's {@code getRecipeLogic()} returns {@code null} once migrated). This class needs no equivalent
 * delegation at all: {@link RecipeWorkableMultiblockController}'s own generic cleanroom/dimension
 * property advertising already describes <i>this array's own</i> current environment, and the inserted machine's
 * {@link RecipeMap} already has the matching filters registered on it (by that machine's own {@code createConfig()}
 * -- see {@link gregtech.api.recipes.logic.statemachine.property.DimensionProperties}/
 * {@link gregtech.api.recipes.logic.statemachine.property.CleanroomProperties}'s JavaDoc), since
 * {@code BitflagRecipeLookup} filter registration is shared per-{@link RecipeMap}, not per-instance. The
 * {@code updateCleanroom()}/{@link ICleanroomReceiver} sync onto the inserted {@link MetaTileEntity} itself is kept
 * verbatim from legacy regardless, purely so that instance's own {@code getCleanroom()} stays consistent for
 * whatever else might query it directly -- the recipe search itself does not depend on it.
 */
public class MetaTileEntityProcessingArray extends RecipeWorkableMultiblockController implements IMachineHatchMultiblock {

    private static final ICleanroomProvider DUMMY_CLEANROOM = DummyCleanroom.createForAllTypes();

    private final int tier;
    private boolean machineChanged = true;

    private ItemStack currentMachineStack = ItemStack.EMPTY;
    private MetaTileEntity mte;
    /** The voltage tier of the machine currently occupying the hatch, from {@link GTValues#V}. */
    private int machineTier;
    private long machineVoltage;
    /** The {@link RecipeMap} of the machine currently occupying the hatch, or {@code null} if none/invalid. */
    private RecipeMap<?> activeRecipeMap;

    public MetaTileEntityProcessingArray(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, null);
        this.tier = tier;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityProcessingArray(metaTileEntityId, tier);
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        findMachineStack();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        // As legacy ProcessingArrayWorkable#invalidate, verbatim: the generic half (discarding queued/in-progress
        // recipe state) is already handled by RecipeWorkableMultiblockController#invalidateStructure's own
        // workable.invalidate() call; this only resets this class's own cached machine-hatch state.
        if (mte instanceof ICleanroomReceiver receiver) {
            receiver.unsetCleanroom();
        }
        currentMachineStack = ItemStack.EMPTY;
        mte = null;
        machineChanged = true;
        machineTier = 0;
        machineVoltage = 0L;
        activeRecipeMap = null;
    }

    @Override
    protected @NotNull RecipeWorkable createWorkable(@NotNull RecipeLogicConfig config) {
        return new RecipeWorkable(this, config, recipeMap) {

            @Override
            public @Nullable RecipeMap<?> getRecipeMap() {
                return activeRecipeMap;
            }
        };
    }

    @Override
    protected @NotNull RecipeLookup createDefaultLookup() {
        return new DynamicRecipeMapLookup(() -> activeRecipeMap);
    }

    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        config.power.properties = () -> {
            RecipePropertySet properties = EnergyContainerProperties.of(getEnergyContainer());
            // Clamp to whichever is lower, the inserted machine's own voltage tier or this array's actual supply --
            // see this class's own JavaDoc for why both halves of this clamp are kept.
            PowerSupplyProperty supply = properties.getOrDefault(PowerSupplyProperty.EMPTY);
            properties.remove(supply);
            properties.add(new PowerSupplyProperty(Math.min(machineVoltage, supply.voltage()), supply.amperage()));
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        // A Processing Array's whole point is to run
        // recipes at whatever tier the *inserted* machine happens to be, almost always a different (usually lower)
        // voltage than this array's own declared supply above -- exactly the "single high-voltage/low-amperage
        // supply powering several lower-voltage/higher-amperage recipes" scenario
        // RecipePowerConfig#downTransformForParallels's own JavaDoc describes (mirroring
        // RecipeWorkableGeneratorMetaTileEntity's identical reasoning for the same flag). Leaving this at the
        // default false silently breaks RecipePowerConfig#getAvailableAmperage's non-down-transform branch, which
        // divides already-consumed EU/t by *this array's own* supply voltage to recover "amps already spoken for":
        // when active entries run at a much lower voltage than that (e.g. 8V entries against a 128V declared
        // supply), each entry's own EU/t floor-divides down to 0 "supply-voltage amps" consumed, so the check keeps
        // reporting the *full* supply amperage as available forever, no matter how many entries are already active
        // -- observed in-game as committed parallel climbing from 0 to the machine-count-based parallelLimit one
        // small batch per tick, fragmented into far more separate entries than necessary, rather than being capped
        // by (and granted in one shot up to) this array's own real EU/t budget.
        config.power.downTransformForParallels = true;
        // Dynamic parallel budget: as many machines as physically sit in the hatch's stack, capped by this array's
        // own tier-based slot limit (legacy ProcessingArrayWorkable#getParallelLimit, verbatim).
        config.parallel.parallelLimit = () -> currentMachineStack.isEmpty() ? getMachineLimit() :
                Math.min(currentMachineStack.getCount(), getMachineLimit());
        // ANDs canWorkWithMachines() onto the base class's own idle-only gate (see that field's JavaDoc for why a
        // subclass must AND rather than replace) -- legacy ProcessingArrayWorkable#shouldSearchForRecipes, verbatim.
        var baseShouldStart = config.hooks.shouldStartRecipeLookup;
        config.hooks.shouldStartRecipeLookup = data -> canWorkWithMachines() && baseShouldStart.test(data);
        return config;
    }

    /** Legacy {@code ProcessingArrayWorkable#canWorkWithMachines}, verbatim. */
    private boolean canWorkWithMachines() {
        if (machineChanged) {
            findMachineStack();
            machineChanged = false;
        }
        return !currentMachineStack.isEmpty() && activeRecipeMap != null;
    }

    @Override
    public void notifyMachineChanged() {
        machineChanged = true;
    }

    /**
     * Re-derives {@link #mte}/{@link #machineTier}/{@link #machineVoltage}/{@link #activeRecipeMap}/
     * {@link #currentMachineStack} from the Machine Hatch's current contents (legacy
     * {@code ProcessingArrayWorkable#findMachineStack}, folding in legacy's separate {@code isRecipeMapValid} check
     * directly here -- this engine has no equivalent "is this candidate RecipeMap allowed" search-time hook to hang
     * that on instead, so {@link #activeRecipeMap} is simply never set to an invalid map to begin with).
     */
    private void findMachineStack() {
        ItemStack machineStack = getMachineHatchStack();
        MetaTileEntity found = resolveMachineHatchMachine(machineStack);

        if (found == null || !isValidMachine(found)) {
            this.mte = null;
            this.activeRecipeMap = null;
        } else {
            this.activeRecipeMap = found.getRecipeMap();
            // Set the world for MTEs, as some need it for checking their recipes.
            MetaTileEntityHolder holder = new MetaTileEntityHolder();
            this.mte = holder.setMetaTileEntity(found);
            holder.setWorld(getWorld());
            updateCleanroom();
        }

        this.machineTier = mte instanceof ITieredMetaTileEntity ? ((ITieredMetaTileEntity) mte).getTier() : 0;
        this.machineVoltage = GTValues.V[this.machineTier];
        this.currentMachineStack = machineStack;
    }

    /**
     * @return the item currently sitting in this array's Machine Hatch slot. Split out from {@link #findMachineStack}
     *         as its own overridable step purely for testability: {@code getAbilities} only ever returns real
     *         ability parts once a structure has actually formed via full block-pattern matching, which -- like
     *         every other {@link RecipeWorkableMultiblockController} test fixture in this codebase (see
     *         {@code RecipeWorkableMultiblockControllerTest}'s {@code TestMultiblock}) -- a unit test fakes by
     *         overriding a single seam rather than forming a real structure in {@code DummyWorld}.
     */
    protected @NotNull ItemStack getMachineHatchStack() {
        return getAbilities(MultiblockAbility.MACHINE_HATCH).get(0).getStackInSlot(0);
    }

    /**
     * @return the {@link MetaTileEntity} {@code machineStack} represents, or {@code null} if it isn't a machine
     *         item at all. Split out from {@link #findMachineStack} as its own overridable step for the same
     *         testability reason as {@link #getMachineHatchStack}: {@link GTUtility#getMetaTileEntity(ItemStack)}
     *         round-trips through the real block/item registry ({@code MTERegistry#getBlock()}), which a unit test
     *         (built only through {@code Bootstrap.perform()}, not full Forge registry event processing) cannot
     *         rely on being wired up.
     */
    protected @Nullable MetaTileEntity resolveMachineHatchMachine(@NotNull ItemStack machineStack) {
        return GTUtility.getMetaTileEntity(machineStack);
    }

    private boolean isValidMachine(@NotNull MetaTileEntity found) {
        RecipeMap<?> map = found.getRecipeMap();
        if (map == null || ArrayUtils.contains(getBlacklist(), map.getUnlocalizedName())) return false;
        // The MetaTileEntity-taking overload, not the ItemStack one: found is already resolved (via the overridable
        // resolveMachineHatchMachine seam above), so re-deriving it again from the raw stack would be redundant and,
        // in a unit test, potentially unreliable (see resolveMachineHatchMachine's own JavaDoc).
        return GTUtility.isMachineValidForMachineHatch(found, getBlacklist());
    }

    /** Legacy {@code ProcessingArrayWorkable#updateCleanroom}, verbatim. */
    private void updateCleanroom() {
        if (mte instanceof ICleanroomReceiver receiver) {
            if (ConfigHolder.machines.cleanMultiblocks) {
                receiver.setCleanroom(DUMMY_CLEANROOM);
            } else {
                ICleanroomProvider provider = getCleanroom();
                if (provider == null) {
                    receiver.unsetCleanroom();
                } else {
                    receiver.setCleanroom(provider);
                }
            }
        }
    }

    @Override
    public void setCleanroom(@NotNull ICleanroomProvider provider) {
        super.setCleanroom(provider);
        updateCleanroom();
    }

    @Override
    public void unsetCleanroom() {
        super.unsetCleanroom();
        updateCleanroom();
    }

    @Override
    public int getMachineLimit() {
        return tier == 0 ? 16 : 64;
    }

    @NotNull
    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("XXX", "XXX", "XXX")
                .aisle("XXX", "X#X", "XXX")
                .aisle("XXX", "XSX", "XXX")
                .where('L', states(getCasingState()))
                .where('S', selfPredicate())
                .where('X', states(getCasingState())
                        .setMinGlobalLimited(tier == 0 ? 11 : 4)
                        .or(autoAbilities(false, true, true, true, true, true, true))
                        .or(abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(4))
                        .or(abilities(MultiblockAbility.MACHINE_HATCH).setExactLimit(1)))
                .where('#', air())
                .build();
    }

    public IBlockState getCasingState() {
        return tier == 0 ? MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.TUNGSTENSTEEL_ROBUST) :
                MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.HSSE_STURDY);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return tier == 0 ? Textures.ROBUST_TUNGSTENSTEEL_CASING : Textures.STURDY_HSSE_CASING;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                .addEnergyUsageLine(this.getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(getEnergyContainer().getInputVoltage()))
                .addCustom((manager, syncer) -> {
                    if (!isStructureFormed()) return;

                    // Machine mode text
                    // Shared text components for both states
                    IKey maxMachinesText = KeyUtil.number(TextFormatting.DARK_PURPLE,
                            syncer.syncInt(getMachineLimit()));
                    maxMachinesText = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.machine.machine_hatch.machines_max", maxMachinesText);

                    if (syncer.syncBoolean(activeRecipeMap == null)) {
                        // No machines in hatch
                        IKey noneText = KeyUtil.lang(TextFormatting.YELLOW,
                                "gregtech.machine.machine_hatch.machines_none");
                        IKey bodyText = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.machine.machine_hatch.machines", noneText);
                        IKey hoverText1 = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.machine.machine_hatch.machines_none_hover");
                        manager.add(KeyUtil.setHover(bodyText, hoverText1, maxMachinesText));
                    } else {
                        // Some amount of machines in hatch
                        String key = syncer.syncString(currentMachineStack.getTranslationKey());
                        IKey mapText = KeyUtil.lang(TextFormatting.DARK_PURPLE,
                                key + ".name");
                        mapText = KeyUtil.string(
                                TextFormatting.DARK_PURPLE,
                                "%sx %s",
                                syncer.syncInt(config().parallel.parallelLimit.getAsInt()), mapText);
                        IKey bodyText = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.machine.machine_hatch.machines", mapText);
                        int tier = syncer.syncInt(machineTier);
                        IKey voltageName = KeyUtil.string(GTValues.VNF[tier]);
                        int amps = syncer.syncInt(currentMachineStack.getCount());
                        String energyFormatted = TextFormattingUtil
                                .formatNumbers(GTValues.V[tier] * amps);
                        IKey hoverText = KeyUtil.lang(
                                TextFormatting.GRAY,
                                "gregtech.machine.machine_hatch.machines_max_eut",
                                energyFormatted, amps, voltageName);
                        manager.add(KeyUtil.setHover(bodyText, hoverText, maxMachinesText));
                    }

                    // Hatch locked status
                    if (syncer.syncBoolean(isActive())) {
                        manager.add(KeyUtil.lang(TextFormatting.DARK_RED,
                                "gregtech.machine.machine_hatch.locked"));
                    }
                })
                .addParallelsLine(config().parallel.parallelLimit.getAsInt())
                .addWorkingStatusLine()
                .addProgressLine(workable.getProgress(0), workable.getMaxProgress(0));
    }

    private RecipeLogicConfig config() {
        return workable.getConfig();
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected OrientedOverlayRenderer getFrontOverlay() {
        return tier == 0 ? Textures.PROCESSING_ARRAY_OVERLAY : Textures.ADVANCED_PROCESSING_ARRAY_OVERLAY;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public String[] getBlacklist() {
        return ConfigHolder.machines.processingArrayBlacklist;
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_MECHANICAL;
    }

    @Override
    public SoundEvent getSound() {
        return GTSoundEvents.ARC;
    }

    @Override
    public TraceabilityPredicate autoAbilities(boolean checkEnergyIn, boolean checkMaintenance, boolean checkItemIn,
                                               boolean checkItemOut, boolean checkFluidIn, boolean checkFluidOut,
                                               boolean checkMuffler) {
        TraceabilityPredicate predicate = super.autoAbilities(checkMaintenance, checkMuffler)
                .or(checkEnergyIn ? abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                        .setMaxGlobalLimited(4).setPreviewCount(1) : new TraceabilityPredicate());

        predicate = predicate.or(abilities(MultiblockAbility.IMPORT_ITEMS).setPreviewCount(1));

        predicate = predicate.or(abilities(MultiblockAbility.EXPORT_ITEMS).setPreviewCount(1));

        predicate = predicate.or(abilities(MultiblockAbility.IMPORT_FLUIDS).setPreviewCount(1));

        predicate = predicate.or(abilities(MultiblockAbility.EXPORT_FLUIDS).setPreviewCount(1));

        return predicate;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.parallel", getMachineLimit()));
    }

    @Override
    public int getItemOutputLimit() {
        return mte == null ? 0 : mte.getItemOutputLimit();
    }
}
