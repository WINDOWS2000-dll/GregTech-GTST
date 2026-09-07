package gregtech.api.recipes.logic.statemachine.experimental;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown by {@link ExperimentalRecipeLogicRegistry}'s registration methods when the two-tier gate isn't fully
 * open: either the master switch ({@code ConfigHolder.dev.enableExperimentalAddonExtensions}) is off, or it's on
 * but the calling {@code modid} isn't explicitly listed in
 * {@code ConfigHolder.dev.experimentalAddonExtensionsAllowlist}. Both conditions are required (an empty allowlist
 * permits nobody, even with the master switch on) &mdash; see that field's own JavaDoc for why this deliberately
 * strict interpretation was chosen over a more permissive "empty allowlist means allow everyone" default.
 */
public final class ExperimentalExtensionsNotPermittedException extends ExperimentalExtensionException {

    private final @NotNull String modid;

    private ExperimentalExtensionsNotPermittedException(@NotNull String modid, @NotNull String reason) {
        super("modid '" + modid + "' is not permitted to register experimental GTST extensions: " + reason);
        this.modid = modid;
    }

    static @NotNull ExperimentalExtensionsNotPermittedException masterSwitchDisabled(@NotNull String modid) {
        return new ExperimentalExtensionsNotPermittedException(modid,
                "ConfigHolder.dev.enableExperimentalAddonExtensions is disabled");
    }

    static @NotNull ExperimentalExtensionsNotPermittedException notInAllowlist(@NotNull String modid) {
        return new ExperimentalExtensionsNotPermittedException(modid,
                "it is not present in ConfigHolder.dev.experimentalAddonExtensionsAllowlist");
    }

    /** @return the modid that was rejected. */
    public @NotNull String getModid() {
        return modid;
    }
}
