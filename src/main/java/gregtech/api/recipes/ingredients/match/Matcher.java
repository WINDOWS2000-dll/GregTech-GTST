package gregtech.api.recipes.ingredients.match;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.Range;

import java.util.function.Predicate;

/**
 * One recipe ingredient requirement {@link IngredientMatchHelper} can match against a pool of available items or
 * fluids: how many are needed, and a yes/no test for whether a given value counts toward it. {@code T} is
 * contravariant in practice ({@code List<? extends Matcher<? super T>>} at call sites) so a single matcher type can
 * serve both items and fluids if it only cares about a shared supertype.
 */
public interface Matcher<T> {

    /** @return whether {@code value} counts toward this requirement at all, independent of how much is needed. */
    boolean matches(T value);

    /** @return how much of a matching value this requirement needs. */
    @Range(from = 1, to = Long.MAX_VALUE)
    long getRequiredCount();

    /** A {@link Matcher} built from a plain predicate, for callers that don't need a dedicated type. */
    static <T> Matcher<T> simpleMatcher(Predicate<T> predicate, @Range(from = 1, to = Long.MAX_VALUE) long count) {
        return new SimpleMatcher<>(predicate, count);
    }

    @Desugar
    record SimpleMatcher<T> (Predicate<T> predicate, @Range(from = 1, to = Long.MAX_VALUE) long count)
            implements Matcher<T> {

        @Override
        public boolean matches(T value) {
            return predicate.test(value);
        }

        @Override
        public @Range(from = 1, to = Long.MAX_VALUE) long getRequiredCount() {
            return count;
        }
    }
}
