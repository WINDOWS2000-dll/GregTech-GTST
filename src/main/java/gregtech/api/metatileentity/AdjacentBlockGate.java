package gregtech.api.metatileentity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

/**
 * A reusable "recipe search requires specific blocks adjacent to this machine" gate, generalized from
 * {@code MetaTileEntityRockBreaker}'s original hand-written lava/water adjacency check (StateMachine migration
 * roadmap: real-machine integration, single-block machine round 5).
 * <p>
 * Scans a configurable subset of the 6 neighbor sides (see {@code sideFilter}) and requires each of a configurable
 * list of block categories (see {@code requiredCategories}) to be matched by <i>at least one</i> qualifying side
 * (not necessarily distinct sides from each other). The result is cached, not recomputed every tick: call
 * {@link #recompute()} from block-update-driven hooks (e.g. {@code MetaTileEntity#onNeighborChanged()}/
 * {@code addNotifiedInput()}), then feed {@link #isSatisfied()} into
 * {@code RecipeLogicHooks#shouldStartRecipeLookup} (which itself is only consulted while idle, so an
 * already-running recipe is never interrupted by a neighbor change &mdash; matching legacy
 * {@code AbstractRecipeLogic#shouldSearchForRecipes()}'s own idle-only semantics exactly).
 * <p>
 * Not thread-safe; intended for the same server-thread-only usage as the rest of the {@code MetaTileEntity} API.
 */
public class AdjacentBlockGate {

    private final MetaTileEntity host;
    private final Predicate<EnumFacing> sideFilter;
    private final List<Predicate<IBlockState>> requiredCategories;

    // Mirrors MetaTileEntityRockBreaker's legacy default: satisfied before the host is even placed in a world
    // (world == null), so a fresh machine isn't spuriously gated shut before its first real neighbor update.
    private boolean satisfied = true;

    /**
     * @param host               the machine this gate scans neighbors of.
     * @param sideFilter         which of the 6 {@link EnumFacing} sides to inspect (e.g. exclude the front facing
     *                           and/or vertical sides, as {@code MetaTileEntityRockBreaker} does).
     * @param requiredCategories one predicate per required block category; every category must be matched by at
     *                           least one qualifying side (categories may all be satisfied by the same side, or
     *                           by different sides) for {@link #isSatisfied()} to become {@code true}.
     */
    public AdjacentBlockGate(@NotNull MetaTileEntity host, @NotNull Predicate<EnumFacing> sideFilter,
                             @NotNull List<Predicate<IBlockState>> requiredCategories) {
        this.host = host;
        this.sideFilter = sideFilter;
        this.requiredCategories = requiredCategories;
    }

    /**
     * Re-scans the qualifying neighbor sides and updates {@link #isSatisfied()}. Cheap enough to call from every
     * block-update notification, but deliberately not wired to run every tick.
     */
    public void recompute() {
        World world = host.getWorld();
        if (world == null) {
            satisfied = true; // not placed yet; see the field's own comment for why this is the safe default
            return;
        }
        if (world.isRemote) {
            // Recipe search only ever runs server-side; mirrors MetaTileEntityRockBreaker's legacy client-side
            // default of false (the client never needs an accurate value here).
            satisfied = false;
            return;
        }

        boolean[] met = new boolean[requiredCategories.size()];
        int remaining = requiredCategories.size();
        for (EnumFacing side : EnumFacing.VALUES) {
            if (remaining == 0) {
                break;
            }
            if (!sideFilter.test(side)) {
                continue;
            }
            IBlockState state = world.getBlockState(host.getPos().offset(side));
            for (int i = 0; i < requiredCategories.size(); i++) {
                if (!met[i] && requiredCategories.get(i).test(state)) {
                    met[i] = true;
                    remaining--;
                }
            }
        }
        satisfied = remaining == 0;
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public void writeToNBT(@NotNull NBTTagCompound data, @NotNull String key) {
        data.setBoolean(key, satisfied);
    }

    public void readFromNBT(@NotNull NBTTagCompound data, @NotNull String key) {
        if (data.hasKey(key)) {
            satisfied = data.getBoolean(key);
        }
    }
}
