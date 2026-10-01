package gregtech.api.recipes.logic.statemachine.experimental;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown by {@link ExperimentalRecipeLogicRegistry#putExtensionFactory} when the {@code extensionType} being
 * registered overlaps, in target-type hierarchy, with an already-registered factory for that same
 * {@code extensionType} &mdash; whether that's the exact same {@code targetType} registered twice (a bug in a
 * single addon), or two different {@code targetType}s where one is a supertype of the other (two independent
 * addons whose broad and narrow registrations would both apply to the same concrete machine class). See
 * {@link ExperimentalRecipeLogicRegistry}'s own JavaDoc for why this overlap, not just exact-type equality, has to
 * be rejected.
 * <p>
 * The {@linkplain #getCause() cause} is a synthetic {@link Throwable} captured at the time of the <i>original</i>
 * registration (never actually thrown itself, only used to carry its stack trace) rather than anything to do with
 * the failing call itself. This lets whoever hits this exception see two stack traces side by side: their own call
 * site, and the original registration's, so they can immediately tell whether they're conflicting with their own
 * earlier mistake or with another mod entirely.
 */
public final class ExperimentalExtensionConflictException extends ExperimentalExtensionException {

    private final @NotNull Class<?> extensionType;
    private final @NotNull String conflictingModid;

    ExperimentalExtensionConflictException(@NotNull Class<?> extensionType, @NotNull String modid,
                                           @NotNull String conflictingModid,
                                           @NotNull Throwable originalRegistrationSite) {
        super("Experimental extension type " + extensionType.getName() + " overlaps an existing registration " +
                "by modid '" + conflictingModid + "' -- modid '" + modid + "' attempted to register an " +
                "overlapping target type for the same extension type. See the cause for the original " +
                "registration's stack trace.", originalRegistrationSite);
        this.extensionType = extensionType;
        this.conflictingModid = conflictingModid;
    }

    /** @return the extension type both registrations were competing for. */
    public @NotNull Class<?> getExtensionType() {
        return extensionType;
    }

    /** @return the modid of whichever registration got there first. */
    public @NotNull String getConflictingModid() {
        return conflictingModid;
    }
}
