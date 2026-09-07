package gregtech.api.recipes.logic.statemachine.experimental;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown by {@link ExperimentalRecipeLogicRegistry}'s registration methods once the registry has frozen: the first
 * machine has already been constructed this session, and no further registrations can take effect (every
 * subsequently-constructed machine's graph is resolved from whatever was registered before that point).
 * <p>
 * The freeze is tied to actual first use rather than to any specific FML lifecycle event ({@code
 * FMLLoadCompleteEvent} and similar were considered and rejected) &mdash; see {@link ExperimentalRecipeLogicRegistry}'s
 * own JavaDoc for why an FML-event-based freeze would silently and systematically reject every addon that
 * correctly declares a load-after dependency on GTST. Registering during your mod's own {@code preInit}/{@code
 * init}/{@code postInit} is always safe regardless of mod load order, since no machine is ever constructed before
 * every mod has finished loading and a world begins loading.
 */
public final class ExperimentalRegistryFrozenException extends ExperimentalExtensionException {

    private final @NotNull String modid;

    ExperimentalRegistryFrozenException(@NotNull String modid) {
        super("modid '" + modid + "' attempted to register an experimental GTST extension after the registry " +
                "was frozen (the first machine has already been constructed this session). Register during mod " +
                "loading (preInit/init/postInit), before any world is loaded.");
        this.modid = modid;
    }

    /** @return the modid that was rejected. */
    public @NotNull String getModid() {
        return modid;
    }
}
