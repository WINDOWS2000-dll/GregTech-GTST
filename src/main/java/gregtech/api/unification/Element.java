package gregtech.api.unification;

import crafttweaker.annotations.ZenRegister;
import org.jetbrains.annotations.Nullable;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;
import stanhebben.zenscript.annotations.ZenProperty;

import java.util.Collections;
import java.util.List;

/**
 * This is some kind of Periodic Table, which can be used to determine Properties of the Materials.
 */
@ZenClass("mods.gregtech.material.Element")
@ZenRegister
public class Element {

    public final String name;
    public final String symbol;
    public final long protons;
    public final long neutrons;

    @ZenProperty("isotope")
    public final boolean isIsotope;
    @ZenProperty("halfLifeSeconds")
    public final double halfLifeSeconds;
    /**
     * This {@link Element}'s decay branches (empty if stable or not yet modeled). Not exposed to CraftTweaker/
     * GroovyScript -- {@code decayTo}, the single-{@code String} predecessor of this field, used to be; addons
     * wanting script-visible decay data should expose their own accessor.
     */
    public final List<DecayMode> decayModes;
    /**
     * Neutron interaction cross-sections; {@code null} unless this {@link Element} is a fissile/fertile isotope
     * something has explicitly supplied data for.
     */
    @Nullable
    public final NeutronCrossSections crossSections;

    /**
     * @param protons         Amount of Protons
     * @param neutrons        Amount of Neutrons (I could have made mistakes with the Neutron amount calculation, please
     *                        tell me if I did something wrong)
     * @param halfLifeSeconds Amount of Half Life this Material has in Seconds. -1 for stable Materials
     * @param decayModes      This Element's decay branches. Empty (or null) for stable/not-yet-modeled Elements.
     * @param crossSections   Neutron interaction cross-sections, or null if not applicable/not modeled.
     * @param name            Name of the Element
     * @param symbol          Symbol of the Element
     */
    protected Element(long protons, long neutrons, double halfLifeSeconds, @Nullable List<DecayMode> decayModes,
                      @Nullable NeutronCrossSections crossSections, String name, String symbol, boolean isIsotope) {
        this.protons = protons;
        this.neutrons = neutrons;
        this.halfLifeSeconds = halfLifeSeconds;
        this.decayModes = decayModes == null ? Collections.emptyList() : decayModes;
        this.crossSections = crossSections;
        this.name = name;
        this.symbol = symbol;
        this.isIsotope = isIsotope;
    }

    @ZenGetter("name")
    public String getName() {
        return name;
    }

    @ZenGetter("symbol")
    public String getSymbol() {
        return symbol;
    }

    @ZenGetter("protons")
    public long getProtons() {
        return protons;
    }

    @ZenGetter("neutrons")
    public long getNeutrons() {
        return neutrons;
    }

    @ZenGetter("mass")
    public long getMass() {
        return protons + neutrons;
    }

    @Override
    @ZenMethod
    public String toString() {
        return name;
    }
}
