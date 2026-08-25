package gregtech.api.capability.impl.boiler;

import com.github.bsideup.jabel.Desugar;

/**
 * The result of a single {@link BoilerThermalModel#tick(int, double)} call.
 *
 * @param steamGenerated    the amount of steam (mB) produced this tick.
 * @param waterConsumed     the amount of water (mB) actually consumed this tick.
 * @param chassisTemperature the chassis temperature (K) after this tick.
 * @param dry               whether the boiler is currently starved of water.
 * @param exploded          whether the boiler exploded this tick (dry and over the safety cutoff).
 * @param explosionPower    the explosion power to use, if {@link #exploded()} is true.
 */
@Desugar
public record BoilerTickResult(int steamGenerated, int waterConsumed, int chassisTemperature, boolean dry,
                               boolean exploded, float explosionPower) {}
