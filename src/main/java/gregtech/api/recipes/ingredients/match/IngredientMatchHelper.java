package gregtech.api.recipes.ingredients.match;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.util.List;

/**
 * Entry point for bipartite-maximum-flow ingredient matching (see {@link GraphMatchCalculation}'s JavaDoc for what
 * problem this actually solves and why). Builds a fresh graph
 * per call: unlike {@code gregtech.api.recipes.logic.statemachine.lookup.bitflag}'s indices (built once, queried
 * many times against a stable {@code RecipeMap}), a match here is inherently one-shot &mdash; specific to one
 * recipe's requirements against one snapshot of available items/fluids.
 */
public final class IngredientMatchHelper {

    private static final Counter<ItemStack> ITEM_COUNTER = new Counter<ItemStack>() {

        @Override
        public long count(@NotNull ItemStack value) {
            return value.getCount();
        }

        @Override
        public ItemStack withCount(ItemStack value, long count) {
            if (value == null) return null;
            ItemStack copy = value.copy();
            copy.setCount((int) count);
            return copy;
        }
    };

    private static final Counter<FluidStack> FLUID_COUNTER = new Counter<FluidStack>() {

        @Override
        public long count(@NotNull FluidStack value) {
            return value.amount;
        }

        @Override
        public FluidStack withCount(FluidStack value, long count) {
            if (value == null) return null;
            FluidStack copy = value.copy();
            copy.amount = (int) count;
            return copy;
        }
    };

    /** @see #match(List, List, Counter) */
    @NotNull
    public static MatchCalculation<ItemStack> matchItems(@NotNull List<? extends Matcher<ItemStack>> matchers,
                                                         @NotNull List<@Nullable ItemStack> matchables) {
        return match(matchers, matchables, ITEM_COUNTER);
    }

    /** @see #match(List, List, Counter) */
    @NotNull
    public static MatchCalculation<FluidStack> matchFluids(@NotNull List<? extends Matcher<FluidStack>> matchers,
                                                           @NotNull List<@Nullable FluidStack> matchables) {
        return match(matchers, matchables, FLUID_COUNTER);
    }

    /**
     * Builds a {@link MatchCalculation} answering whether/how {@code matchables} can simultaneously satisfy every
     * one of {@code matchers}' requirements.
     *
     * @param matchers   the requirements to satisfy, e.g. a recipe's ingredient list.
     * @param matchables the available values by original position (e.g. an inventory's slot contents); {@code null}
     *                   or zero-quantity entries are simply skipped, not required to be removed from the list.
     * @param counter    reads/writes {@code H}'s quantity.
     * @return {@link EmptyMatchCalculation} if {@code matchers} is empty (trivially satisfied at any scale, without
     *         building a graph at all), otherwise a {@link GraphMatchCalculation}.
     */
    @NotNull
    public static <T, H extends T> MatchCalculation<H> match(@NotNull List<? extends Matcher<T>> matchers,
                                                             @NotNull List<@Nullable H> matchables,
                                                             @NotNull Counter<H> counter) {
        if (matchers.isEmpty()) return EmptyMatchCalculation.get();

        Object source = new Object();
        Object sink = new Object();
        SimpleDirectedWeightedGraph<Object, DefaultWeightedEdge> graph = new SimpleDirectedWeightedGraph<>(
                DefaultWeightedEdge.class);
        graph.addVertex(source);
        graph.addVertex(sink);

        DefaultWeightedEdge[] matcherEdges = new DefaultWeightedEdge[matchers.size()];
        Object[] matcherNodes = new Object[matchers.size()];
        long required = 0;
        for (int i = 0; i < matchers.size(); i++) {
            matcherNodes[i] = new Object();
            graph.addVertex(matcherNodes[i]);
            matcherEdges[i] = graph.addEdge(matcherNodes[i], sink);
            long requiredCount = matchers.get(i).getRequiredCount();
            graph.setEdgeWeight(matcherEdges[i], requiredCount);
            required += requiredCount;
        }

        DefaultWeightedEdge[] matchableEdges = new DefaultWeightedEdge[matchables.size()];
        for (int i = 0; i < matchables.size(); i++) {
            H matchable = matchables.get(i);
            if (matchable == null) continue;
            long count = counter.count(matchable);
            if (count <= 0) continue;

            Object matchableNode = new Object();
            graph.addVertex(matchableNode);
            matchableEdges[i] = graph.addEdge(source, matchableNode);
            graph.setEdgeWeight(matchableEdges[i], count);

            for (int j = 0; j < matchers.size(); j++) {
                if (matchers.get(j).matches(matchable)) {
                    graph.setEdgeWeight(graph.addEdge(matchableNode, matcherNodes[j]), required);
                }
            }
        }

        return new GraphMatchCalculation<>(source, sink, graph, matcherEdges, matchableEdges, matchers, matchables,
                counter, required);
    }

    private IngredientMatchHelper() {}
}
