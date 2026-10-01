package gregtech.api.recipes.logic.statemachine.experimental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common base for every exception thrown by {@link ExperimentalRecipeLogicRegistry} and the rest of this package.
 * Extends {@link IllegalStateException} (matching {@code RecipePropertyRegistry}'s own precedent for a rejected
 * registration) so existing generic catch sites still work, while letting a caller that specifically wants to
 * handle "something about this experimental machinery went wrong" catch just this type instead of every possible
 * {@code IllegalStateException} in the JVM.
 * <p>
 * See this package's own JavaDoc for why every API here is explicitly unstable: no method in this package makes
 * any compatibility promise across GTST versions.
 */
public abstract class ExperimentalExtensionException extends IllegalStateException {

    protected ExperimentalExtensionException(@NotNull String message) {
        super(message);
    }

    protected ExperimentalExtensionException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
