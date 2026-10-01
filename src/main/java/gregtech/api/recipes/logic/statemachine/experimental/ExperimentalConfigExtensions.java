package gregtech.api.recipes.logic.statemachine.experimental;

import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The read-only view backing {@link RecipeLogicConfig#experimental}: a type-keyed bag of per-instance values
 * produced by whichever {@link ExperimentalRecipeLogicRegistry#putExtensionFactory} entries applied to this
 * particular machine, resolved exactly once (see {@link ExperimentalRecipeLogicRegistry#resolveExtensionsFor})
 * during graph construction.
 * <p>
 * <b>Read-only from every caller except GTST's own construction path:</b> {@link #put} is {@link
 * ApiStatus.Internal} specifically because writing happens exactly once, at a specific point in a machine's
 * lifecycle that only {@code RecipeLogicGraphBuilder} is responsible for driving. An addon obtains its own values
 * exclusively via {@link #getExtension}, never by writing here directly &mdash; all registration happens ahead of
 * time, globally, through {@link ExperimentalRecipeLogicRegistry} instead (see that class's JavaDoc for why a
 * per-instance write API here couldn't work: this object doesn't exist yet at the point an addon would need to
 * register something for it).
 */
public final class ExperimentalConfigExtensions {

    private final Map<Class<?>, Object> resolved = new ConcurrentHashMap<>();

    @ApiStatus.Internal
    public <T> void put(@NotNull Class<T> type, @NotNull T value) {
        resolved.put(type, value);
    }

    /**
     * @return the value resolved for {@code type} on this specific machine instance, or {@code null} if no
     *         registered factory's target type matched this machine's class (or none was ever registered for
     *         {@code type} at all).
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getExtension(@NotNull Class<T> type) {
        return (T) resolved.get(type);
    }
}
