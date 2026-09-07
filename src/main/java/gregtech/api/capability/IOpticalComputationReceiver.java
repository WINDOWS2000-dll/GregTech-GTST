package gregtech.api.capability;

/**
 * Used in conjunction with the recipe-driven trait that consumes {@link IOpticalComputationProvider} (e.g.
 * Research Station, wired via {@code ComputationRecipeHooks}).
 */
public interface IOpticalComputationReceiver {

    IOpticalComputationProvider getComputationProvider();
}
