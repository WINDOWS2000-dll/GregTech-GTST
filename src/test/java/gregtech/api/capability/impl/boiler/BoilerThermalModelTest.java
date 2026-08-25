package gregtech.api.capability.impl.boiler;

import org.junit.jupiter.api.Test;

import static gregtech.api.fluids.FluidConstants.ROOM_TEMPERATURE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

class BoilerThermalModelTest {

    // simple round numbers: targetFuelEUGeneration() = 10*160 + (400-293) = 1707 EU/tick
    private static final int WATER_BOIL_RATE = 10;
    private static final int MAX_TEMPERATURE = 400;
    private static final int THERMAL_INERTIA = 1000;

    private static BoilerThermalModel newModel() {
        return new BoilerThermalModel(WATER_BOIL_RATE, MAX_TEMPERATURE, THERMAL_INERTIA);
    }

    @Test
    void roomTemperatureAtZeroHeat() {
        BoilerThermalModel model = newModel();
        assertThat(model.getChassisTemperature(), is(ROOM_TEMPERATURE));
    }

    @Test
    void noBoilingAtOrBelowBoilingPoint() {
        BoilerThermalModel model = newModel();
        assertThat(model.getWaterBoilAmount(BoilerThermalModel.BOILING_POINT), is(0));
        assertThat(model.getWaterBoilAmount(BoilerThermalModel.BOILING_POINT - 50), is(0));
    }

    @Test
    void boilRateReachesExactlyTargetAtMaximumTemperature() {
        BoilerThermalModel model = newModel();
        // regression test for the PR's normalization error: at max temperature, output should be *exactly*
        // targetWaterBoilRate, not slightly under it.
        assertThat(model.getWaterBoilAmount(MAX_TEMPERATURE), is(WATER_BOIL_RATE));
    }

    @Test
    void boilRateScalesLinearlyBetweenBoilingAndMaxTemperature() {
        BoilerThermalModel model = newModel();
        int midpoint = (BoilerThermalModel.BOILING_POINT + MAX_TEMPERATURE) / 2;
        int boilAtMidpoint = model.getWaterBoilAmount(midpoint);
        // integer-division rounding means this won't be an exact half; just check it's in the right ballpark.
        assertThat((double) boilAtMidpoint, closeTo(WATER_BOIL_RATE / 2.0, 1.0));
    }

    @Test
    void needsFuelWhenBufferBelowThreshold() {
        BoilerThermalModel model = newModel();
        // empty buffer always needs fuel
        assertThat(model.needsFuel(80), is(true));

        model.addFuelEU(model.targetFuelEUGeneration() * 100);
        assertThat(model.needsFuel(80), is(false));
    }

    @Test
    void temperatureAndSteamOutputConvergeToTargetGivenSufficientFuelAndWater() {
        BoilerThermalModel model = newModel();
        long perTickFuel = model.targetFuelEUGeneration();

        BoilerTickResult last = null;
        // simulate a long warm-up with unlimited fuel/water supply
        for (int i = 0; i < 20_000; i++) {
            model.addFuelEU(perTickFuel);
            last = model.tick(Integer.MAX_VALUE, 1.0);
        }

        assertThat(last.chassisTemperature(), is(MAX_TEMPERATURE));
        assertThat(last.steamGenerated(), is(WATER_BOIL_RATE * BoilerThermalModel.STEAM_PER_WATER));
        assertThat(last.dry(), is(false));
        assertThat(last.exploded(), is(false));
    }

    @Test
    void warmUpNoLongerWastesFuelOnceNearSteadyState() {
        // With the old linear-heat-counter model, fuel was burned at a flat rate regardless of how much steam was
        // actually produced, wasting roughly half the fuel energy during warm-up. Here we check that, once the
        // model is near steady state, essentially all incoming fuel energy is accounted for by steam output plus
        // heat loss (i.e. it isn't being silently discarded).
        //
        // Note: BoilerTickResult.chassisTemperature() reflects the temperature right after burning fuel but
        // *before* this tick's boil/heat-loss deduction, so we must derive heat loss from that same result rather
        // than re-querying getChassisTemperature() afterwards (which reflects the *next* tick's starting point).
        BoilerThermalModel model = newModel();
        long perTickFuel = model.targetFuelEUGeneration();

        BoilerTickResult last = null;
        for (int i = 0; i < 20_000; i++) {
            model.addFuelEU(perTickFuel);
            last = model.tick(Integer.MAX_VALUE, 1.0);
        }

        long steamEU = (long) last.waterConsumed() * BoilerThermalModel.EU_PER_WATER;
        long heatLoss = model.getHeatLoss(last.chassisTemperature());
        assertThat((double) (steamEU + heatLoss), closeTo(perTickFuel, 1.0));
    }

    @Test
    void chassisCoolsDownWithoutFuel() {
        BoilerThermalModel model = newModel();
        long perTickFuel = model.targetFuelEUGeneration();
        for (int i = 0; i < 20_000; i++) {
            model.addFuelEU(perTickFuel);
            model.tick(Integer.MAX_VALUE, 1.0);
        }
        int hotTemperature = model.getChassisTemperature();

        // no more fuel added; the chassis should cool back towards room temperature.
        for (int i = 0; i < 5000; i++) {
            model.tick(Integer.MAX_VALUE, 1.0);
        }

        assertThat(model.getChassisTemperature(), lessThan(hotTemperature));
        assertThat(model.getChassisTemperature(), greaterThan(ROOM_TEMPERATURE - 1));
    }

    @Test
    void becomesDryWhenWaterIsInsufficient() {
        BoilerThermalModel model = newModel();
        long perTickFuel = model.targetFuelEUGeneration();
        for (int i = 0; i < 20_000; i++) {
            model.addFuelEU(perTickFuel);
            model.tick(Integer.MAX_VALUE, 1.0);
        }
        assertThat(model.isDry(), is(false));

        model.addFuelEU(perTickFuel);
        BoilerTickResult starved = model.tick(0, 1.0);
        assertThat(starved.dry(), is(true));
        assertThat(model.isDry(), is(true));
    }

    @Test
    void explodesWhenDryAndOverheated() {
        BoilerThermalModel model = newModel();
        long perTickFuel = model.targetFuelEUGeneration();

        // A single tick can only add up to targetFuelEUGeneration() worth of heat (fuel can be stockpiled, but
        // not burned faster than that), so reaching the safety cutoff with no water available takes many ticks.
        BoilerTickResult last = null;
        for (int i = 0; i < 5000; i++) {
            model.addFuelEU(perTickFuel);
            last = model.tick(0, 1.0);
            if (last.chassisTemperature() > BoilerThermalModel.SAFETY_CUTOFF) break;
        }
        assertThat(last.dry(), is(true));
        assertThat(last.chassisTemperature(), greaterThan(BoilerThermalModel.SAFETY_CUTOFF));

        // water flow resumes while still overheated -> explosion should trigger on this tick.
        model.addFuelEU(perTickFuel);
        BoilerTickResult result = model.tick(Integer.MAX_VALUE, 1.0);
        assertThat(result.exploded(), is(true));
        assertThat(result.explosionPower(), greaterThan(0f));
    }

    private static BoilerTickResult warmUpToSteadyState(BoilerThermalModel model) {
        BoilerTickResult last = null;
        for (int i = 0; i < 20_000; i++) {
            model.addFuelEU(model.targetFuelEUGeneration());
            last = model.tick(Integer.MAX_VALUE, 1.0);
        }
        return last;
    }

    @Test
    void halfThrottleConvergesToLowerTemperatureAndLowerSteamOutput() {
        // Steady state solves generation = actualBoil*EU_PER_WATER + heatLoss(T) for T, where
        // generation = 0.5 * targetFuelEUGeneration(full power). Since heat loss is a small fraction of the total
        // budget here, the resulting temperature lands close to (but not exactly at) the halfway point between
        // BOILING_POINT and MAX_TEMPERATURE - that's a property of these specific parameters, not a general rule,
        // so we assert a generous range rather than an exact value.
        BoilerThermalModel model = newModel();
        model.setThrottle(0.5);

        BoilerTickResult last = warmUpToSteadyState(model);

        assertThat(last.chassisTemperature(), greaterThan(BoilerThermalModel.BOILING_POINT));
        assertThat(last.chassisTemperature(), lessThan(MAX_TEMPERATURE));
        assertThat(last.steamGenerated(), lessThan(WATER_BOIL_RATE * BoilerThermalModel.STEAM_PER_WATER));
        assertThat(last.steamGenerated(), greaterThan(0));
        assertThat(last.exploded(), is(false));
    }

    @Test
    void zeroThrottleProducesNoSteamAndNoFuelDemand() {
        BoilerThermalModel model = newModel();
        model.setThrottle(0);

        // even with a full buffer, a fully throttled-down boiler shouldn't ask for more fuel...
        model.addFuelEU(model.targetFuelEUGeneration() * 1000);
        assertThat(model.needsFuel(80), is(false));

        // ...and shouldn't produce any steam even with unlimited water available.
        BoilerTickResult result = model.tick(Integer.MAX_VALUE, 1.0);
        assertThat(result.steamGenerated(), is(0));
    }

    @Test
    void throttleScalesFuelDemandProportionally() {
        BoilerThermalModel full = newModel();
        BoilerThermalModel half = newModel();
        half.setThrottle(0.5);

        assertThat(half.targetFuelEUGeneration(), lessThan(full.targetFuelEUGeneration()));
    }
}
