package gregtech.api.recipes.logic.statemachine.experimental;

/**
 * Coarse-grained execution order for {@link ExperimentalRecipeLogicRegistry#addPostProcessor}, modeled after
 * Forge's own {@code EventPriority} (familiar to any addon developer, and avoids the bikeshedding a raw magic
 * number invites). Applicable post-processors run in descending priority order ({@link #HIGHEST} first), tied
 * broken by registration order.
 * <p>
 * Each constant's underlying value ({@link #getValue()}) is spaced a million apart specifically so a caller
 * needing finer control can bypass this enum entirely and call the {@code int}-priority overload of {@code
 * addPostProcessor} directly with a value between two of these steps (e.g. {@code 500_000} runs after {@link
 * #NORMAL} but before {@link #HIGH}) &mdash; this enum is a convenience for the common case, not the actual
 * mechanism.
 */
public enum ExperimentalPriority {

    HIGHEST(2_000_000),
    HIGH(1_000_000),
    NORMAL(0),
    LOW(-1_000_000),
    LOWEST(-2_000_000);

    private final int value;

    ExperimentalPriority(int value) {
        this.value = value;
    }

    /** @return the raw priority value this constant maps to, for the {@code int}-priority overload to consume. */
    public int getValue() {
        return value;
    }
}
