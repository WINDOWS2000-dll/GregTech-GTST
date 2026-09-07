package gregtech.api.recipes.logic.statemachine.property;

import gregtech.api.capability.IMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.recipes.logic.statemachine.property.impl.CleanroomFulfillmentProperty;
import gregtech.common.ConfigHolder;

import org.jetbrains.annotations.NotNull;

/**
 * Builds {@link CleanroomFulfillmentProperty} from a {@link MetaTileEntity}'s own current state. GregTech's
 * {@code RecipeLogicConfig} is a plain data holder rather than a virtual method tied to the class hierarchy, so
 * there is no single chaining point analogous to a machine base class's {@code super} call; this class exists so
 * every {@code createConfig()} site that needs this property (the two base classes' own defaults, plus any machine
 * that replaces {@code config.power.properties} outright rather than adding to it &mdash; see those classes'
 * JavaDoc) can call one shared, correct implementation instead of duplicating the cleanroom-requirement check
 * themselves. Applied at every {@code createConfig()} site that builds a search-time {@link RecipePropertySet}, so
 * every cleanroom-gated recipe (Circuit Assembler/Cutting Machine/Laser Engraver, ...) actually gets checked
 * against it.
 *
 * @see gregtech.api.recipes.logic.statemachine.lookup.bitflag.CleanroomFilter
 * @see DimensionProperties
 */
public final class CleanroomProperties {

    private CleanroomProperties() {}

    /**
     * @return {@code mte}'s current cleanroom fulfillment, for {@link CleanroomFulfillmentProperty} (a predicate
     *         rather than a one-shot check against a single recipe): {@code mte} not implementing
     *         {@link ICleanroomReceiver} at all never fulfills anything; {@code ConfigHolder.machines
     *         .cleanMultiblocks} bypasses the check entirely for a {@link IMultiblockController}; otherwise a
     *         {@link CleanroomType} is fulfilled iff {@code mte}'s current {@link ICleanroomProvider} both is clean
     *         and matches that type.
     */
    @NotNull
    public static CleanroomFulfillmentProperty of(@NotNull MetaTileEntity mte) {
        if (!(mte instanceof ICleanroomReceiver receiver)) return CleanroomFulfillmentProperty.EMPTY;
        if (ConfigHolder.machines.cleanMultiblocks && mte instanceof IMultiblockController) {
            return new CleanroomFulfillmentProperty(type -> true);
        }
        ICleanroomProvider provider = receiver.getCleanroom();
        if (provider == null) return CleanroomFulfillmentProperty.EMPTY;
        return new CleanroomFulfillmentProperty(type -> provider.isClean() && provider.checkCleanroomType(type));
    }
}
