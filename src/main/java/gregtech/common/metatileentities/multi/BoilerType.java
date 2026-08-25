package gregtech.common.metatileentities.multi;

import gregtech.api.capability.impl.boiler.BoilerThermalModel;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.block.state.IBlockState;

import org.jetbrains.annotations.NotNull;

import static gregtech.common.blocks.BlockBoilerCasing.BoilerCasingType.*;
import static gregtech.common.blocks.BlockFireboxCasing.FireboxCasingType.*;
import static gregtech.common.blocks.BlockMetalCasing.MetalCasingType.*;
import static gregtech.common.blocks.MetaBlocks.*;

public enum BoilerType {

    // targetWaterBoilRate is derived from the legacy steamPerTick / BoilerThermalModel.STEAM_PER_WATER.
    // maximumChassisTemperature and thermalInertia are new tuning values with no legacy equivalent; see
    // C:\MinecraftModding\GTST-recipe-rework-design\README.md for how these initial figures were derived.
    // They are expected to need adjustment after playtesting.
    BRONZE(800, 1200, 5, 393, 10_800,
            METAL_CASING.getState(BRONZE_BRICKS),
            BOILER_FIREBOX_CASING.getState(BRONZE_FIREBOX),
            BOILER_CASING.getState(BRONZE_PIPE),
            Textures.BRONZE_PLATED_BRICKS,
            Textures.BRONZE_FIREBOX,
            Textures.BRONZE_FIREBOX_ACTIVE,
            Textures.LARGE_BRONZE_BOILER),

    STEEL(1800, 1800, 11, 413, 28_200,
            METAL_CASING.getState(STEEL_SOLID),
            BOILER_FIREBOX_CASING.getState(STEEL_FIREBOX),
            BOILER_CASING.getState(STEEL_PIPE),
            Textures.SOLID_STEEL_CASING,
            Textures.STEEL_FIREBOX,
            Textures.STEEL_FIREBOX_ACTIVE,
            Textures.LARGE_STEEL_BOILER),

    TITANIUM(3200, 2400, 20, 433, 57_257,
            METAL_CASING.getState(TITANIUM_STABLE),
            BOILER_FIREBOX_CASING.getState(TITANIUM_FIREBOX),
            BOILER_CASING.getState(TITANIUM_PIPE),
            Textures.STABLE_TITANIUM_CASING,
            Textures.TITANIUM_FIREBOX,
            Textures.TITANIUM_FIREBOX_ACTIVE,
            Textures.LARGE_TITANIUM_BOILER),

    TUNGSTENSTEEL(6400, 3000, 40, 453, 123_000,
            METAL_CASING.getState(TUNGSTENSTEEL_ROBUST),
            BOILER_FIREBOX_CASING.getState(TUNGSTENSTEEL_FIREBOX),
            BOILER_CASING.getState(TUNGSTENSTEEL_PIPE),
            Textures.ROBUST_TUNGSTENSTEEL_CASING,
            Textures.TUNGSTENSTEEL_FIREBOX,
            Textures.TUNGSTENSTEEL_FIREBOX_ACTIVE,
            Textures.LARGE_TUNGSTENSTEEL_BOILER);

    // Workable Data (legacy; consumed by BoilerRecipeLogic, the AbstractRecipeLogic-based implementation)
    private final int steamPerTick;
    private final int ticksToBoiling;

    // Workable Data (BoilerThermalModel / BoilerLogic, the standalone thermal-capacity replacement)
    private final int targetWaterBoilRate;
    private final int maximumChassisTemperature;
    private final int thermalInertia;

    // Structure Data
    public final IBlockState casingState;
    public final IBlockState fireboxState;
    public final IBlockState pipeState;

    // Rendering Data
    public final ICubeRenderer casingRenderer;
    public final ICubeRenderer fireboxIdleRenderer;
    public final ICubeRenderer fireboxActiveRenderer;
    public final ICubeRenderer frontOverlay;

    BoilerType(int steamPerTick, int ticksToBoiling,
               int targetWaterBoilRate, int maximumChassisTemperature, int thermalInertia,
               IBlockState casingState,
               IBlockState fireboxState,
               IBlockState pipeState,
               ICubeRenderer casingRenderer,
               ICubeRenderer fireboxIdleRenderer,
               ICubeRenderer fireboxActiveRenderer,
               ICubeRenderer frontOverlay) {
        this.steamPerTick = steamPerTick;
        this.ticksToBoiling = ticksToBoiling;

        this.targetWaterBoilRate = targetWaterBoilRate;
        this.maximumChassisTemperature = maximumChassisTemperature;
        this.thermalInertia = thermalInertia;

        this.casingState = casingState;
        this.fireboxState = fireboxState;
        this.pipeState = pipeState;

        this.casingRenderer = casingRenderer;
        this.fireboxIdleRenderer = fireboxIdleRenderer;
        this.fireboxActiveRenderer = fireboxActiveRenderer;
        this.frontOverlay = frontOverlay;
    }

    /**
     * @deprecated legacy parameter for {@code BoilerRecipeLogic}. Use {@link #getTargetWaterBoilRate()} for the
     *             {@link BoilerThermalModel}-based implementation.
     */
    @Deprecated
    public int steamPerTick() {
        return steamPerTick;
    }

    /**
     * @deprecated legacy parameter for {@code BoilerRecipeLogic}. There is no direct equivalent in
     *             {@link BoilerThermalModel}; warm-up time emerges from {@link #getThermalInertia()} instead.
     */
    @Deprecated
    public int getTicksToBoiling() {
        return ticksToBoiling;
    }

    public int getTargetWaterBoilRate() {
        return targetWaterBoilRate;
    }

    public int getMaximumChassisTemperature() {
        return maximumChassisTemperature;
    }

    public int getThermalInertia() {
        return thermalInertia;
    }

    /**
     * @return a new {@link BoilerThermalModel} configured for this boiler type. Should be created once per boiler
     *         instance (e.g. in the owning multiblock's constructor) and not shared between instances.
     */
    @NotNull
    public BoilerThermalModel createThermalModel() {
        return new BoilerThermalModel(targetWaterBoilRate, maximumChassisTemperature, thermalInertia);
    }

    public int runtimeBoost(int ticks) {
        switch (this) {
            case BRONZE:
                return ticks * 2;
            case STEEL:
                return ticks * 150 / 100;
            case TITANIUM:
                return ticks * 120 / 100;
            case TUNGSTENSTEEL:
                return ticks;
        }
        return 0;
    }
}
