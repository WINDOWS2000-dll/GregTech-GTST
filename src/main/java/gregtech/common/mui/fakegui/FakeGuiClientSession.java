package gregtech.common.mui.fakegui;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.ModularNetwork;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.sizer.Area;
import org.jetbrains.annotations.NotNull;

/**
 * Client-side half of a "FakeGui" mirror (see {@link FakeGuiServerSession}). Builds {@code target}'s own
 * {@code buildUI(...)} tree locally and registers it under the {@code networkId} the server allocated, so it starts
 * receiving that machine's real {@link com.cleanroommc.modularui.value.sync.SyncHandler} packets exactly as if its
 * GUI were actually open - it just never gets displayed as a real {@link net.minecraft.client.gui.GuiScreen}.
 * <p>
 * {@link #render}/{@link #click} take a "screen space" position in the same {@code [-0.5, 0.5]} unit range as
 * {@code MetaTileEntityMonitorScreen#checkLookingAt}, and internally scale the mirrored panel to fit within that
 * unit square (matching the old {@code FakeModularGui}'s projection) before mapping it to the panel's own pixel
 * space and applying it as this frame's mouse position.
 */
@SideOnly(Side.CLIENT)
public class FakeGuiClientSession {

    private final ModularSyncManager syncManager;
    private final ModularPanel panel;
    private final ModularScreen screen;
    private final ModularGuiContext context;
    private final int networkId;
    private boolean disposed;

    public FakeGuiClientSession(@NotNull MetaTileEntity target, int networkId) {
        this.networkId = networkId;

        ModularSyncManager msm = new ModularSyncManager(true);
        PanelSyncManager panelSyncManager = new PanelSyncManager(msm, true);
        UISettings settings = new UISettings();
        PosGuiData guiData = new PosGuiData(Minecraft.getMinecraft().player, target.getPos());

        this.panel = target.buildUI(guiData, panelSyncManager, settings);
        WidgetTree.collectSyncValues(panelSyncManager, panel);

        this.screen = new ModularScreen("gregtech", panel);
        // Overlay mode: constructOverlay never touches the dummy GuiScreen it's given, it's only there to satisfy
        // ModularScreen's non-null requirement. This session is never actually displayed as a real screen.
        this.screen.constructOverlay(new GuiScreen() {});
        this.context = this.screen.getContext();

        Minecraft mc = Minecraft.getMinecraft();
        this.screen.onResize(Math.max(mc.displayWidth, 1), Math.max(mc.displayHeight, 1));

        this.syncManager = msm;
        ModularNetwork.CLIENT.activate(networkId, msm);
        msm.onOpen();
    }

    public int getNetworkId() {
        return networkId;
    }

    /**
     * Scales the panel to fit within the current {@code [-0.5, 0.5]} GL unit square, translates
     * {@code (screenX, screenY)} into that panel's own pixel space, and draws it as this frame's mouse position.
     * Pass {@code (0, 0)} while not looking directly at a valid point on the screen.
     */
    public void render(double screenX, double screenY, float partialTicks) {
        if (disposed) return;
        int[] mouse = toPanelPixels(screenX, screenY);

        GlStateManager.pushMatrix();
        applyPanelScale();
        context.updateState(mouse[0], mouse[1], partialTicks);
        screen.onFrameUpdate();
        screen.drawScreen();
        screen.drawForeground();
        GlStateManager.popMatrix();
    }

    /** Relays a synthetic click at {@code (screenX, screenY)} (see {@link #render}) to whatever's under it. */
    public void click(double screenX, double screenY, int button) {
        if (disposed) return;
        int[] mouse = toPanelPixels(screenX, screenY);
        context.updateState(mouse[0], mouse[1], 0);
        screen.onFrameUpdate();
        screen.onMousePressed(button);
        screen.onMouseRelease(button);
    }

    private void applyPanelScale() {
        Area area = panel.getArea();
        float halfW = area.w() / 2f;
        float halfH = area.h() / 2f;
        float scale = 0.5f / Math.max(halfW, halfH);
        GlStateManager.translate(-scale * halfW, -scale * halfH, 0);
        GlStateManager.scale(scale, scale, 1);
    }

    private int[] toPanelPixels(double screenX, double screenY) {
        Area area = panel.getArea();
        float halfW = area.w() / 2f;
        float halfH = area.h() / 2f;
        float scale = 0.5f / Math.max(halfW, halfH);
        int mouseX = (int) ((screenX / scale) + (halfW > halfH ? 0 : (halfW - halfH)));
        int mouseY = (int) ((screenY / scale) + (halfH > halfW ? 0 : (halfH - halfW)));
        return new int[] { mouseX, mouseY };
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        FakeGuiNetworkCleanup.deactivate(ModularNetwork.CLIENT, networkId, syncManager);
    }
}
