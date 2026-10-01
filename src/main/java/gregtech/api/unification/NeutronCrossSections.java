package gregtech.api.unification;

/**
 * Neutron interaction cross-sections for a fissile/fertile {@link Element}, in barns, plus that isotope's own
 * 6-group delayed neutron data. Only meaningful for actinide-range isotopes -- most {@link Element}s leave
 * {@link Element#crossSections} {@code null}.
 * <p>
 * <b>Delayed neutron data provenance (2026-09-17):</b> {@code delayedNeutronDecayConstants} (lambda_i, s^-1) and
 * the relative group shape used to derive {@code delayedNeutronFractions} (beta_i) both come from the standard
 * Keepin (1965) U-235 thermal-fission six-group measurement, which is shared across all four isotopes below and
 * scaled to each isotope's own well-corroborated total delayed neutron fraction (beta_total) -- see GregTech-
 * Nuclear's design memory for why: precursor decay constants are largely shared physical quantities across
 * fissioning systems (the same underlying precursor nuclides, e.g. Br-87/I-137, appear regardless of which
 * isotope fissioned, just with different relative yields), whereas a reliable independently-measured six-group
 * breakdown per isotope was not available at the time this was written. Each isotope's beta_total used for the
 * scaling is corroborated from multiple sources (Keepin 1965 integral measurements matching the NEA/JEF
 * evaluated νd/ν ratios): U-235 ~0.0065, U-238 (fast) ~0.0140, Pu-239 ~0.0021, Pu-241 ~0.0053.
 */
public final class NeutronCrossSections {

    public final double thermalFissionBarns;
    public final double thermalCaptureBarns;
    public final double fastFissionBarns;
    public final double fastCaptureBarns;
    public final double scatteringBarns;
    /** lambda_i (s^-1) for each of the 6 standard delayed neutron precursor groups. */
    public final double[] delayedNeutronDecayConstants;
    /** beta_i (delayed neutron fraction) for each of the 6 standard groups; sums to this isotope's beta_total. */
    public final double[] delayedNeutronFractions;

    public NeutronCrossSections(double thermalFissionBarns, double thermalCaptureBarns, double fastFissionBarns,
                                double fastCaptureBarns, double scatteringBarns,
                                double[] delayedNeutronDecayConstants, double[] delayedNeutronFractions) {
        this.thermalFissionBarns = thermalFissionBarns;
        this.thermalCaptureBarns = thermalCaptureBarns;
        this.fastFissionBarns = fastFissionBarns;
        this.fastCaptureBarns = fastCaptureBarns;
        this.scatteringBarns = scatteringBarns;
        this.delayedNeutronDecayConstants = delayedNeutronDecayConstants;
        this.delayedNeutronFractions = delayedNeutronFractions;
    }
}
