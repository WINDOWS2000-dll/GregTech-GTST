package gregtech.api.capability.impl.boiler;

import gregtech.api.fluids.FluidConstants;

import org.jetbrains.annotations.Range;

/**
 * A pure, Minecraft-independent thermal simulation for Large Boilers.
 * <p>
 * This class holds no reference to any in-world state (tile entities, inventories, etc). It models the boiler's
 * chassis as a heat reservoir: burning fuel adds heat, water evaporation and ambient heat loss remove it, and the
 * chassis temperature (derived from the stored heat) determines how much water can be boiled into steam this tick.
 * <p>
 * Callers are responsible for all side effects (draining fluids from tanks, filling steam output, triggering
 * explosions, searching for and consuming fuel items). This class only computes what *should* happen; see
 * {@link #tick(int, double)} and {@link #addFuelEU(long)}.
 */
public final class BoilerThermalModel {

    /** mB of steam produced per mB of water boiled. */
    public static final int STEAM_PER_WATER = 160;
    /** EU of heat consumed to boil 1 mB of water. */
    public static final int EU_PER_WATER = 160;
    /** The temperature (K) at which water begins to boil. */
    public static final int BOILING_POINT = 373;
    /** The chassis temperature (K), while dry, above which the boiler explodes. */
    public static final int SAFETY_CUTOFF = 473;

    private final int targetWaterBoilRate;
    private final int maximumChassisTemperature;
    private final int thermalInertia;

    /** Accumulated heat (EU) stored in the chassis. */
    private long chassisHeat;
    /** Fuel heat (EU) queued up but not yet converted into chassis heat. */
    private long remainingBurnEU;
    /** Whether the boiler was starved of water on its most recent tick with a nonzero boil demand. */
    private boolean dry;
    /**
     * Throttle, from 0.0 (off) to 1.0 (full power). Scales {@link #targetFuelEUGeneration()} as a whole (both the
     * water-boiling and heat-loss components), so that throttling down reduces both how much fuel heat is drawn
     * from the buffer per tick and, as a consequence, the chassis temperature the boiler settles at. At throttle
     * 0.0, no fuel heat is drawn at all and the chassis simply cools back towards room temperature.
     */
    private double throttle = 1.0;

    /**
     * @param targetWaterBoilRate       the water boil rate (mB/tick) reached at {@code maximumChassisTemperature}.
     * @param maximumChassisTemperature the chassis temperature (K) this boiler is designed to run at.
     * @param thermalInertia            controls how much chassis heat (EU) is needed per degree of temperature.
     *                                  Larger values mean slower heating and cooling.
     */
    public BoilerThermalModel(@Range(from = 1, to = Integer.MAX_VALUE) int targetWaterBoilRate,
                              @Range(from = BOILING_POINT + 1, to = SAFETY_CUTOFF - 1) int maximumChassisTemperature,
                              @Range(from = 1, to = Integer.MAX_VALUE) int thermalInertia) {
        this.targetWaterBoilRate = targetWaterBoilRate;
        this.maximumChassisTemperature = maximumChassisTemperature;
        this.thermalInertia = thermalInertia;
    }

    /**
     * @return the current chassis temperature (K), derived from the stored chassis heat.
     */
    public int getChassisTemperature() {
        return (int) (chassisHeat / thermalInertia) + FluidConstants.ROOM_TEMPERATURE;
    }

    /**
     * @param temperature a chassis temperature (K)
     * @return the passive heat loss (EU/tick) to the environment at that temperature.
     */
    public int getHeatLoss(int temperature) {
        return temperature - FluidConstants.ROOM_TEMPERATURE;
    }

    /**
     * @param throttle 0.0 (off) to 1.0 (full power). See {@link #throttle}.
     */
    public void setThrottle(@Range(from = 0, to = 1) double throttle) {
        this.throttle = Math.max(0, Math.min(1, throttle));
    }

    public double getThrottle() {
        return throttle;
    }

    /**
     * @return the fuel heat generation (EU/tick) required to sustain {@link #maximumChassisTemperature}
     *         indefinitely at full power (covering both boiling at {@link #targetWaterBoilRate} and passive heat
     *         loss), scaled down by the current {@link #throttle}. Because this directly limits how much heat
     *         {@link #tick(int, double)} can draw from the fuel buffer each tick, throttling down naturally lowers
     *         both the chassis's steady-state temperature and its steam output together.
     */
    public long targetFuelEUGeneration() {
        long fullPower = (long) targetWaterBoilRate * EU_PER_WATER + getHeatLoss(maximumChassisTemperature);
        return (long) (fullPower * throttle);
    }

    /**
     * @param temperature a chassis temperature (K)
     * @return the water boil rate (mB/tick) at that temperature. Zero at or below {@link #BOILING_POINT}, scaling
     *         linearly up to {@link #targetWaterBoilRate} at {@link #maximumChassisTemperature}. Not directly
     *         scaled by {@link #throttle}; throttling instead limits how much fuel heat is available to reach a
     *         given temperature in the first place (see {@link #targetFuelEUGeneration()}).
     */
    public int getWaterBoilAmount(int temperature) {
        if (temperature <= BOILING_POINT) return 0;
        int excess = temperature - BOILING_POINT;
        int maxExcess = maximumChassisTemperature - BOILING_POINT;
        return targetWaterBoilRate * Math.min(excess, maxExcess) / maxExcess;
    }

    /**
     * @param bufferTicks how many ticks of fuel the buffer should be kept stocked with.
     * @return whether the fuel buffer has fallen below that threshold and more fuel should be sought out.
     */
    public boolean needsFuel(@Range(from = 1, to = Integer.MAX_VALUE) int bufferTicks) {
        return remainingBurnEU < targetFuelEUGeneration() * bufferTicks;
    }

    /**
     * Adds fuel heat to the buffer. Callers are responsible for finding and consuming an appropriate fuel source
     * and converting its value into EU before calling this method.
     *
     * @param eu the amount of fuel heat (EU) to add to the buffer.
     */
    public void addFuelEU(long eu) {
        this.remainingBurnEU += eu;
    }

    public long getRemainingBurnEU() {
        return remainingBurnEU;
    }

    /**
     * @return the current chassis heat as a percentage (0-100) of {@link #maximumChassisTemperature}, for UI
     *         display. Note that throttling down will keep this below 100% at steady state, since a throttled
     *         boiler settles at a lower temperature; that's intentional, matching the reduced steam output.
     */
    public int getHeatPercentage() {
        int range = maximumChassisTemperature - FluidConstants.ROOM_TEMPERATURE;
        if (range <= 0) return 0;
        return (int) Math.round(100.0 * (getChassisTemperature() - FluidConstants.ROOM_TEMPERATURE) / range);
    }

    public boolean isDry() {
        return dry;
    }

    /**
     * @return the raw accumulated chassis heat (EU). Exposed only for persistence/UI; prefer
     *         {@link #getChassisTemperature()} for anything gameplay-relevant.
     */
    public long getChassisHeat() {
        return chassisHeat;
    }

    /**
     * Restores internal state, e.g. when loading from NBT. Not part of normal simulation flow.
     */
    public void loadState(long chassisHeat, long remainingBurnEU, boolean dry) {
        this.chassisHeat = chassisHeat;
        this.remainingBurnEU = remainingBurnEU;
        this.dry = dry;
    }

    /**
     * Advances the simulation by one tick.
     * <p>
     * This method is pure aside from mutating this object's own fields: it performs no I/O and expects the caller
     * to have already determined how much water is actually available this tick (e.g. via a simulated drain).
     *
     * @param availableWater the maximum water (mB) the caller is able to supply this tick.
     * @param burnEfficiency a multiplier (0.0-1.0) applied to fuel-derived heat generation, e.g. for maintenance
     *                       penalties. Pass 1.0 for no penalty.
     * @return the result of this tick: steam produced, water consumed, resulting temperature, and whether the
     *         boiler exploded.
     */
    public BoilerTickResult tick(@Range(from = 0, to = Integer.MAX_VALUE) int availableWater,
                                 @Range(from = 0, to = 1) double burnEfficiency) {
        // 1. burn fuel from the buffer towards the target generation rate, capped by what's actually queued.
        long generation = Math.min(remainingBurnEU, targetFuelEUGeneration());
        remainingBurnEU -= generation;
        chassisHeat += (long) (generation * burnEfficiency);

        // 2. derive the temperature from the (now updated) chassis heat.
        int temperature = getChassisTemperature();

        // 3. boil as much water as the temperature allows, limited by what's actually available.
        int desiredBoil = getWaterBoilAmount(temperature);
        int actualBoil = Math.min(desiredBoil, availableWater);

        // 4. remove the heat spent boiling water and lost to the environment.
        chassisHeat -= (long) actualBoil * EU_PER_WATER + getHeatLoss(temperature);
        if (chassisHeat < 0) chassisHeat = 0;

        // 5. dry/explosion hysteresis: becoming dry is noted immediately, but the explosion check (and clearing the
        // dry flag) only happens once water flow resumes above the safety cutoff, matching the boiler's intent of
        // punishing a dry boiler that was allowed to overheat rather than one that is merely starved right now.
        boolean exploded = false;
        float explosionPower = 0;
        if (desiredBoil > 0 && actualBoil < desiredBoil) {
            dry = true;
        } else if (dry) {
            if (temperature > SAFETY_CUTOFF) {
                exploded = true;
                explosionPower = (float) Math.pow(temperature, 0.3);
            }
            dry = false;
        }

        int steamGenerated = actualBoil * STEAM_PER_WATER;
        return new BoilerTickResult(steamGenerated, actualBoil, temperature, dry, exploded, explosionPower);
    }
}
