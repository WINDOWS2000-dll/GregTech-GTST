package gregtech.api.recipes.output;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.roll.RollInformation;
import gregtech.api.recipes.roll.RollableOutputList;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;

/**
 * The standard {@link FluidOutputProvider}. See {@link StandardItemOutput} (its exact item counterpart) for the
 * design rationale.
 */
public final class StandardFluidOutput implements FluidOutputProvider {

    private final @NotNull RollableOutputList<FluidStack> outputs;

    public StandardFluidOutput(@NotNull RollableOutputList<FluidStack> outputs) {
        this.outputs = outputs;
    }

    public @NotNull RollableOutputList<FluidStack> getOutputs() {
        return outputs;
    }

    @Override
    public @NotNull List<FluidStack> computeOutputsWithBoost(@UnmodifiableView @NotNull List<ItemStack> inputItems,
                                                             @UnmodifiableView @NotNull List<FluidStack> inputFluids,
                                                             @NotNull RecipePropertySet properties,
                                                             int boostStrength, int parallel, int trimLimit) {
        List<FluidStack> result = new ObjectArrayList<>(Math.min(trimLimit, outputs.size()));
        long[] roll = outputs.comprehensiveRoll(boostStrength, trimLimit, parallel);
        for (int i = 0; i < roll.length; i++) {
            addStackToList(result, outputs.get(i), roll[i]);
        }
        return result;
    }

    /**
     * Merges {@code amount} of {@code stack} into {@code list}, adding to an existing stack of the same fluid if
     * one is present, or appending a new one otherwise. {@code amount <= 0} is a no-op.
     */
    public static void addStackToList(@NotNull List<FluidStack> list, @NotNull FluidStack stack, long amount) {
        if (amount <= 0) return;
        for (FluidStack stackInList : list) {
            if (stackInList.isFluidEqual(stack)) {
                stackInList.amount += (int) Math.min(amount, Integer.MAX_VALUE - stackInList.amount);
                return;
            }
        }
        FluidStack copy = stack.copy();
        copy.amount = (int) Math.min(amount, Integer.MAX_VALUE);
        list.add(copy);
    }

    @Override
    public @NotNull @UnmodifiableView List<FluidStack> getCompleteOutputs(int parallel, int trimLimit) {
        List<FluidStack> result = new ObjectArrayList<>(Math.min(trimLimit, outputs.size()));
        int limit = Math.min(outputs.size(), trimLimit);
        for (int i = 0; i < limit; i++) {
            FluidStack stack = outputs.get(i);
            addStackToList(result, stack, (long) stack.amount * parallel);
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
        for (FluidStack stack : outputs) {
            if (stack == null || stack.getFluid() == null || stack.amount <= 0) return false;
        }
        return true;
    }

    @Override
    public @NotNull FluidOutputProvider trim(int limit) {
        if (limit >= outputs.size()) return this;
        int keepUnrolled = Math.min(outputs.getUnrolled().size(), limit);
        int keepRolled = Math.min(outputs.getRolled().size(), Math.max(0, limit - outputs.getUnrolled().size()));

        List<FluidStack> unrolled = outputs.getUnrolled().subList(0, keepUnrolled);
        List<RollInformation<FluidStack>> rolled = outputs.recomposeRolled().subList(0, keepRolled);
        RollableOutputList<FluidStack> trimmed = new RollableOutputList<>(outputs.getYieldCounter(), unrolled, rolled,
                outputs.getInterpreter(), outputs.getCorrelation());
        return new StandardFluidOutput(trimmed);
    }
}
