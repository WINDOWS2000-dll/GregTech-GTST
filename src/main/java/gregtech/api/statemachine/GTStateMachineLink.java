package gregtech.api.statemachine;

import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * Describes which operator a {@link GTStateMachine} should move to next from a given operator, as a chain of
 * conditions evaluated in order, falling back to a default ("else") link if none match.
 * <p>
 * Each condition may optionally carry a human-readable description, used by {@link GTStateMachine#dumpGraph()} to
 * explain *why* a branch is taken. Providing a description is recommended but not required &mdash; unlike operator
 * debug names (see {@link GTStateMachineOperator}), which are mandatory, since conditions are often small inline
 * lambdas and requiring a description for every one of them would add more friction than it's worth.
 */
public final class GTStateMachineLink {

    public static final GTStateMachineLink UNKNOWN_LINK = new GTStateMachineLink();

    protected final List<Predicate<NBTTagCompound>> conditions;
    protected final List<@Nullable String> conditionDescriptions;
    protected final IntList linkIfs;
    protected final int linkElse;

    protected GTStateMachineLink() {
        this.conditions = new ObjectArrayList<>(1);
        this.conditionDescriptions = new ObjectArrayList<>(1);
        this.linkIfs = new IntArrayList(1);
        this.linkElse = -1;
    }

    protected GTStateMachineLink(GTStateMachineLink link, int linkElse) {
        this.conditions = new ObjectArrayList<>(link.conditions.size() + 1);
        this.conditions.addAll(link.conditions);
        this.conditionDescriptions = new ObjectArrayList<>(link.conditionDescriptions.size() + 1);
        this.conditionDescriptions.addAll(link.conditionDescriptions);
        this.linkIfs = new IntArrayList(link.linkIfs.size() + 1);
        this.linkIfs.addAll(link.linkIfs);
        this.linkElse = linkElse;
    }

    public static GTStateMachineLink link(int link) {
        return UNKNOWN_LINK.elseLink(link);
    }

    public static GTStateMachineLink linkIf(@NotNull Predicate<NBTTagCompound> predicate, int link) {
        return UNKNOWN_LINK.elseIf(predicate, link);
    }

    public static GTStateMachineLink linkIf(@NotNull Predicate<NBTTagCompound> predicate,
                                            @Nullable String description, int link) {
        return UNKNOWN_LINK.elseIf(predicate, description, link);
    }

    public static GTStateMachineLink linkIfElse(@NotNull Predicate<NBTTagCompound> predicate, int link,
                                                int linkElse) {
        return UNKNOWN_LINK.elseIfElse(predicate, null, link, linkElse);
    }

    /**
     * @see #elseIf(Predicate, String, int)
     */
    public GTStateMachineLink elseIf(@NotNull Predicate<NBTTagCompound> predicate, int link) {
        return elseIf(predicate, null, link);
    }

    /**
     * Appends a condition to this link: if {@code predicate} matches, move to {@code link}. Conditions are tested
     * in the order they were appended, and the first match wins; if none match, the existing else-link is used.
     *
     * @param description a human-readable explanation of the condition, shown in {@link GTStateMachine#dumpGraph()}.
     *                    Recommended but optional &mdash; pass {@code null} to omit it.
     */
    public GTStateMachineLink elseIf(@NotNull Predicate<NBTTagCompound> predicate, @Nullable String description,
                                     int link) {
        return elseIfElse(predicate, description, link, this.linkElse);
    }

    public GTStateMachineLink elseIfElse(@NotNull Predicate<NBTTagCompound> predicate, @Nullable String description,
                                         int link, int linkElse) {
        GTStateMachineLink created = new GTStateMachineLink(this, linkElse);
        created.conditions.add(predicate);
        created.conditionDescriptions.add(description);
        created.linkIfs.add(link);
        return created;
    }

    public GTStateMachineLink elseLink(int link) {
        return new GTStateMachineLink(this, link);
    }

    public int getLink(NBTTagCompound data) {
        for (int i = 0; i < conditions.size(); i++) {
            if (conditions.get(i).test(data)) {
                return linkIfs.get(i);
            }
        }
        return linkElse;
    }

    /**
     * @param debugNameResolver resolves an operator ID to its debug name (see {@link GTStateMachine#getDebugName}),
     *                          so a dump reads e.g. {@code -> #4 (resetSearchBudget)} rather than a bare index.
     *                          Destination IDs are still shown alongside the name, since indices are what a
     *                          {@code /gtst dumpstatemachine} reader would cross-reference against the rest of the
     *                          dump.
     * @return a human-readable, multi-line description of this link's branches, one per line, indented for
     *         inclusion under an operator entry in {@link GTStateMachine#dumpGraph()}. Conditions without a
     *         description are shown as {@code condition #<i>}.
     */
    @NotNull
    String describe(@NotNull IntFunction<String> debugNameResolver) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            String description = conditionDescriptions.get(i);
            sb.append("    if (").append(description != null ? description : "condition #" + i).append(") -> ")
                    .append(destinationLabel(linkIfs.get(i), debugNameResolver)).append('\n');
        }
        sb.append("    else -> ").append(destinationLabel(linkElse, debugNameResolver)).append('\n');
        return sb.toString();
    }

    @NotNull
    private static String destinationLabel(int operatorID, @NotNull IntFunction<String> debugNameResolver) {
        return operatorID < 0 ? "<unknown>" : "#" + operatorID + " (" + debugNameResolver.apply(operatorID) + ")";
    }
}
