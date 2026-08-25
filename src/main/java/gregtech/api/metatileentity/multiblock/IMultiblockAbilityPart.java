package gregtech.api.metatileentity.multiblock;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface IMultiblockAbilityPart<T> extends IMultiblockPart {

    /**
     * Returns only one ability for this multiblock part.
     * If you need more than one, override {@link #getAbilities()} instead.
     * 
     * @return The MultiblockAbility this part has
     */
    default @Nullable MultiblockAbility<T> getAbility() {
        return null;
    }

    /**
     * Returns a list of abilities that this multiblock part may have.
     *
     * @return a list of MultiblockAbilities
     */
    default @NotNull List<MultiblockAbility<?>> getAbilities() {
        return getAbility() == null ? Collections.emptyList() : Collections.singletonList(getAbility());
    }

    /**
     * Returns the subset of {@link #getAbilities()} that should be considered when matching structure patterns
     * (i.e. what {@link MultiblockControllerBase#abilities(MultiblockAbility[])} predicates test against).
     * <p>
     * Defaults to {@link #getAbilities()}. Override this instead of narrowing {@link #getAbilities()} if a part
     * needs to expose an ability for {@link #registerAbilities(AbilityInstances)} purposes (e.g. contributing an
     * inventory to the multiblock once formed) without that ability being matchable as a standalone structure
     * requirement — for example, a fluid hatch that also carries a ghost circuit slot should still be aggregated
     * as an {@code IMPORT_ITEMS} provider, but shouldn't itself satisfy a pattern's "at most one Input Bus"
     * requirement.
     *
     * @return a list of MultiblockAbilities considered for structure pattern matching
     */
    default @NotNull List<MultiblockAbility<?>> getPatternAbilities() {
        return getAbilities();
    }

    /**
     * Register abilities to the multiblock here
     * <br />
     * Check {@link AbilityInstances#isKey(MultiblockAbility) AbiliteInstances.isKey()} if you override
     * {@link IMultiblockAbilityPart#getAbilities()}
     * 
     * @param abilityInstances list to register abilities to
     */
    void registerAbilities(@NotNull AbilityInstances abilityInstances);
}
