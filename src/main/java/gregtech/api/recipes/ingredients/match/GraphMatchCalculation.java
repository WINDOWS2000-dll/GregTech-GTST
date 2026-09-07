package gregtech.api.recipes.ingredients.match;

import org.jgrapht.Graph;
import org.jgrapht.alg.flow.PushRelabelMFImpl;
import org.jgrapht.graph.DefaultWeightedEdge;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Solves ingredient matching as a bipartite maximum-flow problem:
 * {@code source -> each matchable -> every matcher it satisfies -> sink}, with edge
 * weights set to available/required amounts. A maximum flow reaching every matcher's full requirement is exactly an
 * assignment of matchables to matchers that satisfies all of them simultaneously &mdash; this correctly resolves
 * cases a naive linear/greedy consumption pass can get wrong, e.g. two requirements ("a copper ingot" and "any
 * metal ingot") that could both be satisfied by the same physical stack, where a greedy pass's requirement
 * <i>order</i> would otherwise determine (and could get wrong) whether a match is found at all.
 * <p>
 * Built once per {@link IngredientMatchHelper#match} call and reused across every {@link #attemptScale} at
 * different scales: only the requirement-side edge weights change between scales (see {@link #rescale}), so the
 * underlying {@link PushRelabelMFImpl} solver instance and graph structure are both kept rather than rebuilt.
 */
final class GraphMatchCalculation<T> extends AbstractMatchCalculation<T> {

    private final Object source;
    private final Object sink;
    private final Graph<Object, DefaultWeightedEdge> graph;
    private final PushRelabelMFImpl<Object, DefaultWeightedEdge> flow;

    private final DefaultWeightedEdge[] matcherEdges;
    private final List<? extends Matcher<? super T>> matchers;
    /** One entry per {@link #matchables} position; {@code null} where that matchable was skipped (empty/zero). */
    private final DefaultWeightedEdge[] matchableEdges;
    private final List<T> matchables;
    private final Counter<T> counter;

    private long required;

    GraphMatchCalculation(@NotNull Object source, @NotNull Object sink,
                          @NotNull Graph<Object, DefaultWeightedEdge> graph,
                          @NotNull DefaultWeightedEdge[] matcherEdges, @NotNull DefaultWeightedEdge[] matchableEdges,
                          @NotNull List<? extends Matcher<? super T>> matchers, @NotNull List<T> matchables,
                          @NotNull Counter<T> counter, long required) {
        this.source = source;
        this.sink = sink;
        this.graph = graph;
        // matches the scale-1 requirement the graph was already built with; rescale() only recomputes this when
        // moving away from scale 1, so it must start correct for the (common) case of never leaving scale 1.
        this.required = required;
        this.flow = new PushRelabelMFImpl<>(graph);
        this.matcherEdges = matcherEdges;
        this.matchableEdges = matchableEdges;
        this.matchers = matchers;
        this.matchables = matchables;
        this.counter = counter;
    }

    @Override
    protected void rescale(int oldScale, int newScale) {
        required = 0;
        for (int i = 0; i < matcherEdges.length; i++) {
            long req = matchers.get(i).getRequiredCount() * newScale;
            graph.setEdgeWeight(matcherEdges[i], req);
            required += req;
        }
        // the "webbing" edges between a matchable and every matcher it satisfies must never themselves be the
        // bottleneck: only each matcher's own required-count edge (into the sink) should be able to constrain flow.
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            if (graph.getEdgeSource(edge) != source && graph.getEdgeTarget(edge) != sink) {
                graph.setEdgeWeight(edge, required);
            }
        }
    }

    @Override
    protected long @Nullable [] attemptScaleInternal() {
        if (flow.calculateMaximumFlow(source, sink) < required) return null;
        long[] results = new long[matchableEdges.length];
        Map<DefaultWeightedEdge, Double> flowMap = flow.getFlowMap();
        for (int i = 0; i < matchableEdges.length; i++) {
            DefaultWeightedEdge edge = matchableEdges[i];
            if (edge == null) continue;
            Double amount = flowMap.get(edge);
            if (amount != null) results[i] = amount.longValue();
        }
        return results;
    }

    @Override
    protected @NotNull List<T> mapResults(long @NotNull [] results) {
        List<T> list = new ObjectArrayList<>(matchables.size());
        for (int i = 0; i < matchables.size(); i++) {
            list.add(counter.withCount(matchables.get(i), results[i]));
        }
        return list;
    }
}
