package gregtech.api.recipes.output;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.roll.RollInformation;
import gregtech.api.recipes.roll.RollableOutputList;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;

/**
 * The standard {@link ItemOutputProvider}: a fixed set of guaranteed outputs plus a
 * {@link RollableOutputList} of chance-based ones, rolled (and correlated) fresh on every call to
 * {@link #computeOutputs}.
 * <p>
 * See {@link gregtech.api.recipes.Recipe#getItemOutputProvider()} for the standard way to obtain one of these from
 * an existing recipe's guaranteed/chanced output lists.
 */
public final class StandardItemOutput implements ItemOutputProvider {

    private final @NotNull RollableOutputList<ItemStack> outputs;

    public StandardItemOutput(@NotNull RollableOutputList<ItemStack> outputs) {
        this.outputs = outputs;
    }

    public @NotNull RollableOutputList<ItemStack> getOutputs() {
        return outputs;
    }

    @Override
    public @NotNull List<ItemStack> computeOutputsWithBoost(@UnmodifiableView @NotNull List<ItemStack> inputItems,
                                                             @UnmodifiableView @NotNull List<FluidStack> inputFluids,
                                                             @NotNull RecipePropertySet properties, int boostStrength,
                                                             int parallel, int trimLimit) {
        List<ItemStack> result = new ObjectArrayList<>(Math.min(trimLimit, outputs.size()));
        long[] roll = outputs.comprehensiveRoll(boostStrength, trimLimit, parallel);
        for (int i = 0; i < roll.length; i++) {
            addStackToList(result, outputs.get(i), roll[i]);
        }
        return result;
    }

    /**
     * Merges {@code count} copies of {@code stack} into {@code list}, filling existing compatible stacks up to
     * their max stack size before creating new ones. {@code count <= 0} is a no-op.
     */
    public static void addStackToList(@NotNull List<ItemStack> list, @NotNull ItemStack stack, long count) {
        for (ItemStack stackInList : list) {
            if (count <= 0) return;
            int insertable = stackInList.getMaxStackSize() - stackInList.getCount();
            if (insertable > 0 && ItemHandlerHelper.canItemStacksStack(stackInList, stack)) {
                if (insertable >= count) {
                    stackInList.grow((int) count);
                    return;
                } else {
                    stackInList.grow(insertable);
                    count -= insertable;
                }
            }
        }
        int max = stack.getMaxStackSize();
        while (count > 0) {
            ItemStack copy = stack.copy();
            int stackCount = (int) Math.min(max, count);
            copy.setCount(stackCount);
            list.add(copy);
            count -= stackCount;
        }
    }

    @Override
    public @NotNull @UnmodifiableView List<ItemStack> getCompleteOutputs(int parallel, int trimLimit) {
        List<ItemStack> result = new ObjectArrayList<>(Math.min(trimLimit, outputs.size()));
        int limit = Math.min(outputs.size(), trimLimit);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = outputs.get(i);
            addStackToList(result, stack, (long) stack.getCount() * parallel);
        }
        return result;
    }

    @Override
    public @Range(from = 0, to = Integer.MAX_VALUE) int getMaximumOutputs(
                                                                          @Range(from = 1,
                                                                                 to = Integer.MAX_VALUE) int parallel) {
        return outputs.size() * parallel;
    }

    @Override
    public boolean isValid() {
        for (ItemStack stack : outputs) {
            if (stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) return false;
        }
        return true;
    }

    @Override
    public @NotNull ItemOutputProvider trim(int limit) {
        if (limit >= outputs.size()) return this;
        int keepUnrolled = Math.min(outputs.getUnrolled().size(), limit);
        int keepRolled = Math.min(outputs.getRolled().size(), Math.max(0, limit - outputs.getUnrolled().size()));

        List<ItemStack> unrolled = outputs.getUnrolled().subList(0, keepUnrolled);
        List<RollInformation<ItemStack>> rolled = outputs.recomposeRolled().subList(0, keepRolled);
        RollableOutputList<ItemStack> trimmed = new RollableOutputList<>(outputs.getYieldCounter(), unrolled, rolled,
                outputs.getInterpreter(), outputs.getCorrelation());
        return new StandardItemOutput(trimmed);
    }
}
