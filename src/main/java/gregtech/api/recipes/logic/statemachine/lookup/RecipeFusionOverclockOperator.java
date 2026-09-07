package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.logic.RecipeView;
import gregtech.api.recipes.logic.statemachine.OverclockFactory;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeOverclockConfig;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerCapacityProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * The Fusion Reactor's overclock calculation. A full {@link OverclockFactory} replacement rather than the
 * standard {@code RecipeOverclockConfig#ocAmountCalculator} seam, for the same structural reason
 * {@link RecipeCoilOverclockOperator} needed one: that seam's signature is a plain
 * {@code (recipeVoltage, maxVoltage) -> int}, with no room to carry a <i>per-recipe</i> value like
 * {@link FusionEUToStartProperty}'s required starting energy (which the reactor-MK clamp needs to derive a
 * "fusion tier" from) through to the calculation. A recipe-specific property always forces the full-operator seam,
 * not just {@code Predicate}/two-scalar-parameter customization.
 * <p>
 * Otherwise mirrors {@link RecipeOverclockOperator} (reusing its {@link RecipeOverclockOperator#SUCCESS_KEY}/
 * {@link RecipeOverclockOperator#RESULT_KEY} contract, ceiling computation, and the two pure static helpers it
 * exposes package-private for this purpose) with only one real addition: after the usual overclock-count derivation
 * (respecting {@link gregtech.api.recipes.logic.statemachine.RecipeOverclockConfig#ocAmountCalculator} if set, else
 * {@link RecipeOverclockOperator#standardOcAmount}), the count is clamped by the difference between this reactor's
 * own MK tier and the recipe's required fusion-start tier (legacy: "a MK2 reactor can overclock a MK1 recipe once, a
 * MK3 reactor can overclock a MK2 recipe once, or a MK1 recipe twice"). {@code costFactor}/{@code speedFactor}
 * themselves need no per-machine override here (unlike the OC count): the reactor's gentler voltage growth per
 * overclock (legacy {@code PERFECT_HALF_VOLTAGE_FACTOR}, PR's {@code FUSION_OVERCLOCK_VOLTAGE_FACTOR}) is just
 * {@code config.overclock.costFactor}, set directly in {@code MetaTileEntityFusionReactor#createConfig()} the same
 * way any other machine would.
 * <p>
 * <b>A construction-order trap the {@code reactorTier} parameter is deliberately an {@link IntSupplier} to avoid:</b>
 * {@code createConfig()} (and therefore this operator's construction, via {@code overclockFactory}) runs
 * synchronously from inside the host machine's own constructor &mdash; specifically from
 * {@code RecipeWorkableMultiblockController}'s {@code super(...)} call, which necessarily executes <i>before</i> the
 * host subclass's own field-assignment statements (like {@code this.tier = tier;}) run. A plain {@code int} captured
 * at that point would silently read Java's default {@code 0} for the not-yet-assigned field, permanently baking a
 * wrong tier into this operator (found via a failing regression test: both an MK1- and an MK3-tier reactor produced
 * identical, zero-overclock results). Deferring the read to {@link #operate} via a supplier &mdash; which only ever
 * runs on a later tick, long after construction has finished &mdash; sidesteps the trap entirely.
 */
public final class RecipeFusionOverclockOperator implements GTStateMachineTransientOperator {

    public static final String RESULT_KEY = RecipeOverclockOperator.RESULT_KEY;
    public static final String SUCCESS_KEY = RecipeOverclockOperator.SUCCESS_KEY;
    public static final Predicate<NBTTagCompound> SUCCESS_PREDICATE = RecipeOverclockOperator.SUCCESS_PREDICATE;

    private final @NotNull RecipeLogicConfig config;
    private final @NotNull IntSupplier reactorTier;

    /**
     * @param reactorTier supplies this reactor's own MK tier (e.g. {@code GTValues.LuV}), read lazily rather than
     *                    taken as a plain {@code int} &mdash; {@code createConfig()} (and therefore this factory
     *                    call) runs synchronously from inside the host's own constructor, via
     *                    {@code RecipeWorkableMultiblockController}'s {@code super(...)} call, which happens
     *                    <i>before</i> the host subclass's own field-assignment statements (like
     *                    {@code this.tier = tier;}) run. A plain {@code int} captured at that point would silently
     *                    read Java's default {@code 0} instead of the real tier. Deferring the read to
     *                    {@link #operate} (which only ever runs on a later tick, long after construction has
     *                    finished) sidesteps the trap.
     */
    public RecipeFusionOverclockOperator(@NotNull RecipeLogicConfig config, @NotNull IntSupplier reactorTier) {
        this.config = config;
        this.reactorTier = reactorTier;
    }

    @Override
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        RecipeView view = (RecipeView) transientData.get(RecipeViewBuildOperator.VIEW_KEY);
        if (view == null) throw new IllegalStateException("RecipeFusionOverclockOperator ran without a recipe view");

        long baseEUt = view.getActualEUt();
        int baseDuration = applyDurationBonusPreOverclock(view.getActualDuration());
        long requiredAmperage = view.getActualAmperage();

        if (baseEUt == 0) {
            transientData.put(RESULT_KEY,
                    new OverclockOutcome(0, applyDurationDiscount(baseDuration), 0, requiredAmperage));
            data.setBoolean(SUCCESS_KEY, true);
            return;
        }

        long maxVoltage = config.power.getMaxVoltage();
        long magnitude = Math.abs(baseEUt);
        boolean upTransform = config.overclock.upTransformForOverclocks;
        long ceiling;
        if (upTransform) {
            ceiling = availableEUt(view);
        } else if (config.power.downTransformForParallels) {
            ceiling = Math.max(1, config.power.getMaxSearchVoltage() / requiredAmperage);
        } else {
            ceiling = maxVoltage;
        }
        if (magnitude > ceiling) {
            data.setBoolean(SUCCESS_KEY, false);
            return;
        }

        int ocAmount = config.overclock.ocAmountCalculator != null ?
                config.overclock.ocAmountCalculator.calculate(magnitude, maxVoltage) :
                RecipeOverclockOperator.standardOcAmount(magnitude, maxVoltage);

        // Legacy FusionRecipeLogic#modifyOverclockPre, verbatim: a MK2 reactor can overclock a MK1 recipe once, a
        // MK3 reactor can overclock a MK2 recipe once, or a MK1 recipe twice. getFusionTier(0) (no EUToStart set)
        // returns 0, left unclamped, matching legacy's own "fusionTier != 0" guard.
        long euToStart = view.getRecipe().getProperty(FusionEUToStartProperty.getInstance(), 0L);
        int fusionTier = FusionEUToStartProperty.getFusionTier(euToStart);
        if (fusionTier != 0) {
            fusionTier = reactorTier.getAsInt() - fusionTier;
            ocAmount = Math.min(fusionTier, ocAmount);
        }

        OCResult result = new OCResult();
        if (ocAmount <= 0) {
            result.init(magnitude, baseDuration);
        } else {
            OCParams params = new OCParams();
            params.initialize(magnitude, baseDuration, ocAmount);
            RecipeOverclockConfig.OcAlgorithm algorithm = config.overclock.ocAlgorithm != null ?
                    config.overclock.ocAlgorithm : OverclockingLogic::standardOC;
            algorithm.apply(params, result, ceiling, 1.0 / config.overclock.speedFactor, config.overclock.costFactor);
        }

        long requiredVoltage = applyVoltageDiscount(result.eut());
        double duration = applyDurationDiscount(result.duration());
        int overclocksApplied = RecipeOverclockOperator.countOverclocks(magnitude, result.eut(),
                config.overclock.costFactor);

        transientData.put(RESULT_KEY,
                new OverclockOutcome(overclocksApplied, duration, requiredVoltage, requiredAmperage));
        data.setBoolean(SUCCESS_KEY, true);
    }

    /**
     * As {@code RecipeOverclockOperator#availableEUt}, verbatim (duplicated: that method is a private instance
     * helper reading the same config fields, not worth cross-instance sharing for three lines).
     */
    private long availableEUt(@NotNull RecipeView view) {
        if (config.power.properties == null) return 0;
        RecipePropertySet set = config.power.properties.get();
        return view.getRecipe().isGenerating() ?
                set.getOrDefault(PowerCapacityProperty.EMPTY).getEUt() :
                set.getOrDefault(PowerSupplyProperty.EMPTY).getEUt();
    }

    private int applyDurationBonusPreOverclock(int duration) {
        return config.overclock.durationBonusPreOverclock == null ? duration :
                (int) Math.round(duration * config.overclock.durationBonusPreOverclock.getAsDouble());
    }

    private double applyDurationDiscount(double duration) {
        return config.overclock.durationDiscount == null ? duration :
                duration * config.overclock.durationDiscount.getAsDouble();
    }

    private long applyVoltageDiscount(long voltage) {
        return config.overclock.voltageDiscount == null ? voltage :
                (long) (voltage * config.overclock.voltageDiscount.getAsDouble());
    }
}
