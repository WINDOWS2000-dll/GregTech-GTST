package gregtech.common.mui.widget.among_us;

import gregtech.api.mui.GTGuiTextures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec2f;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * MUI2 port of the April Fools' "fix wiring" minigame: four color-coded wires must be dragged from their
 * shuffled left-side terminal to the matching right-side terminal. State (colors, shuffle, connections) lives
 * in {@link FixWiringSyncHandler}; this widget only handles drawing and mouse interaction.
 */
public class FixWiringTaskWidget extends Widget<FixWiringTaskWidget> implements Interactable {

    private BiPredicate<Integer, ItemStack> hoverItemCheck;
    private int dragged = -1;
    private Supplier<Boolean> canInteractPredicate = () -> true;
    private FixWiringSyncHandler syncHandler;

    public FixWiringTaskWidget syncHandler(FixWiringSyncHandler syncHandler) {
        setSyncHandler(syncHandler);
        return this;
    }

    @Override
    public boolean isValidSyncHandler(SyncHandler syncHandler) {
        return syncHandler instanceof FixWiringSyncHandler;
    }

    @Override
    protected void setSyncHandler(@Nullable SyncHandler syncHandler) {
        super.setSyncHandler(syncHandler);
        this.syncHandler = (FixWiringSyncHandler) syncHandler;
    }

    public FixWiringTaskWidget setHoverItemCheck(BiPredicate<Integer, ItemStack> hoverItemCheck) {
        this.hoverItemCheck = hoverItemCheck;
        return this;
    }

    public FixWiringTaskWidget setCanInteractPredicate(Supplier<Boolean> canInteractPredicate) {
        this.canInteractPredicate = canInteractPredicate;
        return this;
    }

    /**
     * @return the index of the wire whose terminal the mouse is currently over, or -1 if none.
     */
    private int isMouseOverWire(int mouseX, int mouseY, boolean isLeft) {
        if (!canInteractPredicate.get()) return -1;
        Area area = getArea();
        int width = area.width;
        int height = area.height;
        float xScale = width / 504f;
        float yScale = height / 504f;
        for (int i = 0; i < 4; i++) {
            float y1 = yScale * ((i + 1) * 104 - 10);
            if (isLeft) {
                if (isInBox(0, (int) (y1 - yScale * 16), (int) (xScale * 38 * 1.5), (int) (yScale * 64), mouseX,
                        mouseY)) {
                    return i;
                }
            } else {
                if (isInBox(width - (int) (xScale * 38 * 1.5), (int) (y1 - yScale * 16), (int) (xScale * 38 * 1.5),
                        (int) (yScale * 64), mouseX, mouseY)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isInBox(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void draw(@NotNull ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (syncHandler == null || !syncHandler.isInitialized()) return;
        Area area = getArea();
        int width = area.width;
        int height = area.height;
        int mouseX = context.getAbsMouseX() - area.x;
        int mouseY = context.getAbsMouseY() - area.y;

        GTGuiTextures.ELECTRICITY_WIRES_BASEBACK.draw(0, 0, width, height);
        float xScale = width / 504f;
        float yScale = height / 504f;
        // connected
        for (int i = 0; i < 4; i++) {
            float y1 = yScale * ((i + 1) * 104 - 10);
            int color = syncHandler.getColor(i);
            if (syncHandler.isConnected(i)) {
                for (int j = 0; j < 4; j++) {
                    if (syncHandler.getTarget(j) == i) {
                        float y2 = yScale * ((j + 1) * 104 - 10);
                        drawLines(Arrays.asList(new Vec2f(xScale * 25, y1 + yScale * 12),
                                new Vec2f(width - (xScale * 25), y2 + yScale * 12)), color, color, 5);
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            float y1 = yScale * ((i + 1) * 104 - 10);
            int color = syncHandler.getColor(i);

            if (dragged == i) {
                drawLines(Arrays.asList(new Vec2f(xScale * 25, y1 + yScale * 12), new Vec2f(mouseX, mouseY)), color,
                        color, 5);
            }

            // left
            GTGuiTextures.ELECTRICITY_WIRES.drawSubArea(xScale * 25, y1 - yScale * 5, xScale * 38, yScale * 32, 0f,
                    0f, 0.5f, 1f);
            Color.setGlColor(color);
            GTGuiTextures.ELECTRICITY_WIRES_BASE.drawSubArea(0, y1, xScale * 50, yScale * 32, 0f, 0f, 0.5f, 1f);
            GlStateManager.color(1, 1, 1, 1);

            // right
            GTGuiTextures.ELECTRICITY_WIRES.drawSubArea(width - (xScale * (25 + 38)), y1 - yScale * 5, xScale * 38,
                    yScale * 32, 0.5f, 0f, 0.5f, 1f);
            Color.setGlColor(syncHandler.getColor(syncHandler.getTarget(i)));
            GTGuiTextures.ELECTRICITY_WIRES_BASE.drawSubArea(width - xScale * 50, y1, xScale * 50, yScale * 32, 0.5f,
                    0f, 0.5f, 1f);
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    @NotNull
    @Override
    public Result onMousePressed(int mouseButton) {
        if (syncHandler == null || !syncHandler.isInitialized()) return Result.IGNORE;
        int mouseX = getContext().getAbsMouseX() - getArea().x;
        int mouseY = getContext().getAbsMouseY() - getArea().y;
        int over = isMouseOverWire(mouseX, mouseY, true);
        ItemStack holdStack = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (over != -1 && !syncHandler.isConnected(over) &&
                (hoverItemCheck == null || hoverItemCheck.test(over, holdStack))) {
            dragged = over;
            return Result.SUCCESS;
        }
        return Result.IGNORE;
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (dragged != -1 && syncHandler != null) {
            int mouseX = getContext().getAbsMouseX() - getArea().x;
            int mouseY = getContext().getAbsMouseY() - getArea().y;
            int over = isMouseOverWire(mouseX, mouseY, false);
            if (over != -1 && syncHandler.getTarget(over) == dragged) {
                syncHandler.connectWire(dragged);
            }
        }
        dragged = -1;
        return false;
    }

    /**
     * Draws a poly-line through the given points, interpolating from {@code startColor} to {@code endColor}.
     * Ported as-is from the MUI1 widget; only uses vanilla/Forge rendering APIs so no MUI2-specific rework was
     * needed.
     */
    private static void drawLines(List<Vec2f> points, int startColor, int endColor, float width) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(width);
        if (startColor == endColor) {
            Color.setGlColor(startColor);
            bufferbuilder.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
            for (Vec2f point : points) {
                bufferbuilder.pos(point.x, point.y, 0).endVertex();
            }
        } else {
            float startAlpha = (startColor >> 24 & 255) / 255.0F;
            float startRed = (startColor >> 16 & 255) / 255.0F;
            float startGreen = (startColor >> 8 & 255) / 255.0F;
            float startBlue = (startColor & 255) / 255.0F;
            float endAlpha = (endColor >> 24 & 255) / 255.0F;
            float endRed = (endColor >> 16 & 255) / 255.0F;
            float endGreen = (endColor >> 8 & 255) / 255.0F;
            float endBlue = (endColor & 255) / 255.0F;
            bufferbuilder.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            int size = points.size();
            for (int i = 0; i < size; i++) {
                float p = i * 1.0f / size;
                bufferbuilder.pos(points.get(i).x, points.get(i).y, 0)
                        .color(startRed + (endRed - startRed) * p, startGreen + (endGreen - startGreen) * p,
                                startBlue + (endBlue - startBlue) * p, startAlpha + (endAlpha - startAlpha) * p)
                        .endVertex();
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }
}
