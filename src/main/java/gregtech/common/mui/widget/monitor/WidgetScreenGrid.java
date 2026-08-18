package gregtech.common.mui.widget.monitor;

import gregtech.api.mui.factory.MetaTileEntityGuiFactory;
import gregtech.client.utils.RenderUtil;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityCentralMonitor;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * One cell of a grid overview of a {@link MetaTileEntityCentralMonitor}'s screens: a small square, colored by the
 * bound {@link MetaTileEntityMonitorScreen}'s {@code frameColor} while active (black otherwise), that shows a
 * scaled-down live preview of the screen on hover and opens that specific screen's own MUI2 config UI on click
 * (equivalent to screwdriver-clicking it in-world).
 * <p>
 * Intended to back a future "screen layout" overview panel on {@code MetaTileEntityCentralMonitor} itself
 * (one {@code WidgetScreenGrid} per {@code (x, y)} slot in {@link MetaTileEntityMonitorScreen#getX()}/
 * {@link MetaTileEntityMonitorScreen#getY()} space), letting a player pick a screen to configure without walking
 * up to it. Not wired into any screen yet.
 *
 * @deprecated unused: ported 1:1 from the pre-MUI2 {@code gregtech.common.gui.widget.monitor.WidgetScreenGrid},
 *             which itself was never instantiated anywhere. Kept for a future overview panel; remove if that
 *             never materializes.
 */
@Deprecated
public class WidgetScreenGrid extends Widget<WidgetScreenGrid> implements Interactable {

    public static final int CELL_SIZE = 20;

    private final int gridX, gridY;
    @Nullable
    private final MetaTileEntityMonitorScreen monitorScreen;
    private final OpenScreenUiSyncHandler syncHandler;

    public WidgetScreenGrid(int gridX, int gridY, @Nullable MetaTileEntityMonitorScreen monitorScreen) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.monitorScreen = monitorScreen;
        this.syncHandler = new OpenScreenUiSyncHandler(monitorScreen);
        pos(gridX * CELL_SIZE, gridY * CELL_SIZE);
        size(CELL_SIZE, CELL_SIZE);
        setSyncHandler(this.syncHandler);
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int color = (monitorScreen != null && monitorScreen.isActive()) ? monitorScreen.frameColor : 0XFF000000;
        Gui.drawRect(1, 1, getArea().w() - 1, getArea().h() - 1, color);
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        if (isHovering() && monitorScreen != null && monitorScreen.isActive()) {
            int width = getArea().w();
            int height = getArea().h();
            GlStateManager.pushMatrix();
            GlStateManager.translate(width / 2f, height / 2f, 100);
            GlStateManager.scale(width, height, 1);
            RenderUtil.renderRect(-0.5f, -0.5f, monitorScreen.scale, monitorScreen.scale, 0, 0XFF000000);
            monitorScreen.renderScreen(0, null);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean isValidSyncHandler(@NotNull SyncHandler syncHandler) {
        return syncHandler instanceof OpenScreenUiSyncHandler;
    }

    @NotNull
    @Override
    public Result onMousePressed(int mouseButton) {
        if (monitorScreen == null) return Result.IGNORE;
        syncHandler.openScreenUi();
        return Result.SUCCESS;
    }

    /** Tells the server to open {@code screen}'s own config UI for the clicking player. */
    private static class OpenScreenUiSyncHandler extends SyncHandler {

        private static final int OPEN_UI = 0;

        @Nullable
        private final MetaTileEntityMonitorScreen screen;

        private OpenScreenUiSyncHandler(@Nullable MetaTileEntityMonitorScreen screen) {
            this.screen = screen;
        }

        void openScreenUi() {
            syncToServer(OPEN_UI);
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) {
            if (id == OPEN_UI && screen != null) {
                MetaTileEntityGuiFactory.open(getSyncManager().getPlayer(), screen);
            }
        }

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {}
    }
}
