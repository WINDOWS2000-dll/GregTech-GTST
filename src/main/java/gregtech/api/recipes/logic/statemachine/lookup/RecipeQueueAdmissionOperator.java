package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.PreparedRecipeQueue;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.statemachine.GTStateMachineOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The standard admission operator, satisfying {@code RecipeProgressTrackBuilder}'s {@code admissionOperator}
 * contract: drains {@link PreparedRecipeQueue} into {@link ActiveRecipeList}, in order, for as long as each head
 * entry's (already-rolled) outputs currently fit.
 * <p>
 * Real inventory consumption happens here, for the first time in this entry's lifecycle (see
 * {@code RecipeLookupTrackBuilder}'s JavaDoc on why it's deferred this far). Because the reservation could
 * theoretically have gone stale despite {@link PreparedRecipeQueue}'s own safeguards (true external interference
 * &mdash; another mod, a player, a shared distinct-bus machine), extraction is attempted in two passes (simulate,
 * then commit) so a partially-available entry is never half-consumed; if the simulate pass fails, that one entry
 * is discarded and the next one is tried, rather than blocking the whole queue on a single corrupted entry.
 * <p>
 * Registered as a plain (non-transient) {@link GTStateMachineOperator}, since moving entries between the two
 * queues and consuming real inventory are persistent effects that must be captured in a snapshot promptly (see
 * {@code RecipeProgressTrackBuilder}'s JavaDoc on why its own operators are non-transient for the same reason).
 * <p>
 * <b>{@code config.callbacks.onRecipeStarted}:</b> this is the only point in the whole
 * pipeline where a queued candidate becomes a genuinely active recipe with its inputs actually, physically
 * consumed -- the correct place to notify a machine that needs to react to "a recipe just started" with something
 * that must not happen any earlier (e.g. Research Station locking its Object Holder: locking any earlier, such as
 * in {@code config.hooks.finalCheck}, blocks this operator's own {@link #tryConsume} from ever extracting the
 * item it just reserved, silently discarding every candidate as "stale" forever).
 */
public final class RecipeQueueAdmissionOperator implements GTStateMachineOperator {

    private final @NotNull RecipeLogicConfig config;

    public RecipeQueueAdmissionOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data) {
        while (!PreparedRecipeQueue.isEmpty(data)) {
            NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);

            List<ItemStack> itemsOut = ActiveRecipeList.itemsOut(entry);
            List<FluidStack> fluidsOut = ActiveRecipeList.fluidsOut(entry);
            if (!config.io.itemOutputSpace.test(itemsOut) || !config.io.fluidOutputSpace.test(fluidsOut)) {
                // blocked; leave this (and everything after it) queued for next tick, preserving order.
                return;
            }

            List<ItemStack> itemsConsumed = PreparedRecipeQueue.itemsConsumed(entry);
            List<FluidStack> fluidsConsumed = PreparedRecipeQueue.fluidsConsumed(entry);
            IItemHandlerModifiable itemHandler = config.io.itemInput == null ? null : config.io.itemInput.get();
            IMultipleTankHandler fluidHandler = config.io.fluidInput == null ? null : config.io.fluidInput.get();

            if (!tryConsume(itemHandler, itemsConsumed, fluidHandler, fluidsConsumed)) {
                // stale: something external took the reserved items out from under this entry. Discard just this
                // one and keep draining -- a single corrupted entry must not block everything behind it.
                PreparedRecipeQueue.removeFirst(data);
                continue;
            }

            PreparedRecipeQueue.removeFirst(data);
            NBTTagCompound activeEntry = PreparedRecipeQueue.toActiveEntry(entry);
            ActiveRecipeList.append(data, activeEntry);
            if (config.callbacks.onRecipeStarted != null) config.callbacks.onRecipeStarted.accept(activeEntry);
        }
    }

    private static boolean tryConsume(@Nullable IItemHandlerModifiable itemHandler,
                                      @NotNull List<ItemStack> itemsNeeded,
                                      @Nullable IMultipleTankHandler fluidHandler,
                                      @NotNull List<FluidStack> fluidsNeeded) {
        if (!itemsNeeded.isEmpty() && itemHandler == null) return false;
        if (!fluidsNeeded.isEmpty() && fluidHandler == null) return false;

        for (ItemStack needed : itemsNeeded) {
            if (!canExtractItem(itemHandler, needed)) return false;
        }
        for (FluidStack needed : fluidsNeeded) {
            FluidStack drained = fluidHandler.drain(needed, false);
            if (drained == null || drained.amount < needed.amount) return false;
        }

        for (ItemStack needed : itemsNeeded) extractItem(itemHandler, needed);
        for (FluidStack needed : fluidsNeeded) fluidHandler.drain(needed, true);
        return true;
    }

    private static boolean canExtractItem(@NotNull IItemHandlerModifiable handler, @NotNull ItemStack needed) {
        int remaining = needed.getCount();
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (!matches(inSlot, needed)) continue;
            remaining -= handler.extractItem(slot, remaining, true).getCount();
        }
        return remaining <= 0;
    }

    private static void extractItem(@NotNull IItemHandlerModifiable handler, @NotNull ItemStack needed) {
        int remaining = needed.getCount();
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (!matches(inSlot, needed)) continue;
            remaining -= handler.extractItem(slot, remaining, false).getCount();
        }
    }

    private static boolean matches(@NotNull ItemStack inSlot, @NotNull ItemStack needed) {
        return !inSlot.isEmpty() && ItemStack.areItemsEqual(inSlot, needed) &&
                ItemStack.areItemStackTagsEqual(inSlot, needed);
    }
}
