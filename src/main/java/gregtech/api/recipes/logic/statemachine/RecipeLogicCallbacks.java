package gregtech.api.recipes.logic.statemachine;

import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Pure notification hooks for a {@link RecipeLogicConfig}: fired when something happens, with no return value and
 * no effect on control flow. Distinct from {@link RecipeLogicHooks}, whose fields are predicates/overrides that
 * shape the control flow itself.
 */
public final class RecipeLogicCallbacks {

    /** Run whenever this logic's tracked inputs are notified of a change (e.g. to trigger a UI refresh). */
    public final List<Runnable> onInputsUpdate = new ObjectArrayList<>();

    /**
     * Notified with a newly-started recipe's finalized data, if set. Fired from
     * {@code RecipeQueueAdmissionOperator} the instant a queued candidate's inputs are actually, physically
     * consumed and it becomes a genuinely active recipe -- the correct hook for anything that must happen no
     * earlier than real consumption (e.g. locking a resource the recipe now holds; locking it any earlier risks
     * holding it against a candidate that never actually gets admitted).
     */
    public @Nullable Consumer<NBTTagCompound> onRecipeStarted;

    /** Notified with a just-completed recipe's finalized data, if set. */
    public @Nullable Consumer<NBTTagCompound> onRecipeCompleted;
}
