package gregtech.common.mui.widget.monitor;

import gregtech.client.utils.RenderUtil;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;

import net.minecraft.client.renderer.GlStateManager;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.Nullable;

/**
 * A GUI-internal live preview of a {@link MetaTileEntityMonitorScreen}'s rendered content, framed with a border.
 * The caller is expected to size this widget to a square via {@code size(w + 4)}.
 */
public class WidgetMonitorScreen extends Widget<WidgetMonitorScreen> {

    @Nullable
    private final MetaTileEntityMonitorScreen screen;

    public WidgetMonitorScreen(@Nullable MetaTileEntityMonitorScreen screen) {
        this.screen = screen;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int width = getArea().w();
        int height = getArea().h();
        RenderUtil.renderRect(0, 0, width, height, 0, 0XFF7B7A7C);
        RenderUtil.renderRect(2, 2, width - 4, height - 4, 0, 0XFF000000);

        if (screen != null && screen.isActive()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(2 + 0.5 * (width - 4), 2 + 0.5 * (height - 4), 0);
            GlStateManager.scale(width, width, 1.0f / width);
            GlStateManager.scale(1 / screen.scale, 1 / screen.scale, 1 / screen.scale);
            GlStateManager.translate(-(screen.scale - 1) * 0.5, -(screen.scale - 1) * 0.5, 0);

            screen.renderScreen(0, null);
            GlStateManager.popMatrix();
        }
    }
}
