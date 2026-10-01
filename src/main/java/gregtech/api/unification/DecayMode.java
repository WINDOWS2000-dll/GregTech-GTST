package gregtech.api.unification;

import org.jetbrains.annotations.Nullable;

/**
 * One decay branch of a radioactive {@link Element}: the mechanism, what fraction of this element's decays take
 * this path, and (if applicable) which {@link Element} it decays into. An {@link Element} typically has one
 * {@link DecayMode} per branch (most isotopes have exactly one; some, like Bi-212, split across two).
 * <p>
 * {@link #daughterElementKey} is resolved lazily via {@link Elements#get(String)} (not stored as a direct
 * {@link Element} reference) so decay chains can be declared in any order without static-initialization ordering
 * concerns -- {@code null} for decay types that don't produce a single well-defined daughter nuclide (e.g.
 * spontaneous fission).
 */
public final class DecayMode {

    public final DecayType type;
    public final double branchingRatio;
    @Nullable
    public final String daughterElementKey;
    public final double decayEnergyMeV;

    public DecayMode(DecayType type, double branchingRatio, @Nullable String daughterElementKey,
                     double decayEnergyMeV) {
        this.type = type;
        this.branchingRatio = branchingRatio;
        this.daughterElementKey = daughterElementKey;
        this.decayEnergyMeV = decayEnergyMeV;
    }

    /** @return the daughter {@link Element}, resolved via {@link Elements#get(String)}, or {@code null} if none. */
    @Nullable
    public Element getDaughterElement() {
        return daughterElementKey == null ? null : Elements.get(daughterElementKey);
    }
}
