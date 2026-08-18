package gregtech.common.mui.fakegui;

import gregtech.api.util.GTLog;

import com.cleanroommc.modularui.network.ModularNetworkSide;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;

import java.lang.reflect.Field;

/**
 * Unregisters a detached {@link ModularSyncManager} session (see {@link FakeGuiServerSession}/
 * {@link FakeGuiClientSession}) from a {@link ModularNetworkSide}'s active-screen registry.
 * <p>
 * MUI2 only exposes this through the public {@code closeContainer(...)}, which also force-closes whatever real GUI
 * the target player currently has open - not acceptable for a session that exists purely to mirror another
 * machine's screen onto a monitor block. The package-private {@code deactivate(int, boolean)} does exactly what we
 * want, but isn't reachable normally, so this reflects into the two registry fields it operates on directly
 * ({@code activeScreens} / {@code inverseActiveScreens}) rather than into the method itself: a method's behavior can
 * change across MUI2 versions, but the presence of these two fields is far less likely to.
 * <p>
 * If reflection ever fails (e.g. a future MUI2 restructures its network side), {@link #isAvailable()} reports false
 * and {@link #deactivate} degrades to just closing/disposing the sync manager without unregistering it - the leaked
 * registry entry is harmless (network IDs recycle after 100000 allocations), not a crash.
 */
public final class FakeGuiNetworkCleanup {

    private static final Field ACTIVE_SCREENS;
    private static final Field INVERSE_ACTIVE_SCREENS;

    static {
        Field activeScreens = null;
        Field inverseActiveScreens = null;
        try {
            activeScreens = ModularNetworkSide.class.getDeclaredField("activeScreens");
            activeScreens.setAccessible(true);
            inverseActiveScreens = ModularNetworkSide.class.getDeclaredField("inverseActiveScreens");
            inverseActiveScreens.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            GTLog.logger.warn("Could not reflect ModularNetworkSide's active screen registry; FakeGui mirror " +
                    "sessions will leak network IDs until they recycle (harmless). ModularUI version mismatch?", e);
        }
        ACTIVE_SCREENS = activeScreens;
        INVERSE_ACTIVE_SCREENS = inverseActiveScreens;
    }

    private FakeGuiNetworkCleanup() {}

    public static boolean isAvailable() {
        return ACTIVE_SCREENS != null;
    }

    /** Closes, unregisters (if possible) and disposes {@code msm}, which was registered on {@code side}. */
    @SuppressWarnings("unchecked")
    public static void deactivate(ModularNetworkSide side, int networkId, ModularSyncManager msm) {
        if (!msm.isClosed()) msm.onClose();
        if (isAvailable()) {
            try {
                ((Int2ReferenceMap<ModularSyncManager>) ACTIVE_SCREENS.get(side)).remove(networkId);
                ((Reference2IntMap<ModularSyncManager>) INVERSE_ACTIVE_SCREENS.get(side)).removeInt(msm);
            } catch (ReflectiveOperationException e) {
                GTLog.logger.warn("Failed to unregister FakeGui mirror session {}", networkId, e);
            }
        }
        msm.dispose();
    }
}
