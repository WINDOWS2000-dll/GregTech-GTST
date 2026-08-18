package gregtech.common.mui.fakegui;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.ModularNetwork;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side half of a "FakeGui" mirror: builds {@code target}'s real {@code buildUI(...)} tree for one specific
 * viewing player, without ever assigning it to {@link EntityPlayerMP#openContainer} - so it never actually counts
 * as that player having {@code target}'s GUI open, and doesn't disturb whatever GUI they really have open (which
 * may well be the monitor's own config panel). One session exists per player currently viewing the mirror; see
 * {@link FakeGuiClientSession} for its client-side counterpart.
 */
public class FakeGuiServerSession {

    private final EntityPlayerMP viewer;
    private final ModularSyncManager syncManager;
    private final int networkId;
    private boolean disposed;

    public FakeGuiServerSession(@NotNull MetaTileEntity target, @NotNull EntityPlayerMP viewer) {
        this.viewer = viewer;

        ModularSyncManager msm = new ModularSyncManager(false);
        PanelSyncManager panelSyncManager = new PanelSyncManager(msm, true);
        UISettings settings = new UISettings(RecipeViewerSettings.DUMMY);
        PosGuiData guiData = new PosGuiData(viewer, target.getPos());

        ModularPanel panel = target.buildUI(guiData, panelSyncManager, settings);
        WidgetTree.collectSyncValues(panelSyncManager, panel);

        ModularContainer container = new ModularContainer();
        container.construct(viewer, msm, settings, panel.getName(), guiData);

        this.syncManager = msm;
        this.networkId = ModularNetwork.SERVER.activate(msm);
        msm.onOpen();
    }

    public int getNetworkId() {
        return networkId;
    }

    public @NotNull EntityPlayerMP getViewer() {
        return viewer;
    }

    /** Call once per server tick while this session is alive to push out changed values. */
    public void tick() {
        if (!disposed) {
            syncManager.detectAndSendChanges(false);
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        FakeGuiNetworkCleanup.deactivate(ModularNetwork.SERVER, networkId, syncManager);
    }
}
