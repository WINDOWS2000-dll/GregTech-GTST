package gregtech.common.mui.widget.prospector;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.util.FileUtility;
import gregtech.api.util.Mods;
import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.api.worldgen.filler.FillerEntry;
import gregtech.common.mui.sync.prospector.ProspectingSyncHandler;
import gregtech.integration.xaero.ColorUtility;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Renders the {@link ProspectingTexture} collected by a {@link ProspectingSyncHandler}, and lets the player
 * double-click a scanned cell to drop a waypoint in a supported minimap mod.
 */
public class WidgetProspectingMap extends Widget<WidgetProspectingMap> implements Interactable {

    private static final int DOUBLE_CLICK_MS = 400;
    private static final int CELL_SIZE = 16;

    private static final double[][] XAERO_COLORS = {
            ColorUtility.getLab(new Color(0, 0, 0)),
            ColorUtility.getLab(new Color(0, 0, 128)),
            ColorUtility.getLab(new Color(0, 128, 0)),
            ColorUtility.getLab(new Color(0, 128, 128)),
            ColorUtility.getLab(new Color(128, 0, 0)),
            ColorUtility.getLab(new Color(128, 0, 128)),
            ColorUtility.getLab(new Color(128, 128, 0)),
            ColorUtility.getLab(new Color(192, 192, 192)),
            ColorUtility.getLab(new Color(128, 128, 128)),
            ColorUtility.getLab(new Color(0, 0, 255)),
            ColorUtility.getLab(new Color(0, 255, 0)),
            ColorUtility.getLab(new Color(0, 255, 255)),
            ColorUtility.getLab(new Color(255, 0, 0)),
            ColorUtility.getLab(new Color(255, 0, 255)),
            ColorUtility.getLab(new Color(255, 255, 0)),
            ColorUtility.getLab(new Color(255, 255, 255)),
    };

    private final ProspectingSyncHandler syncHandler;
    private final int chunksPerSide;

    private long lastClickTime;
    private final List<String> hoveredNames = new ArrayList<>();
    private int hoveredHeight;
    private int waypointColor;

    public WidgetProspectingMap(@NotNull ProspectingSyncHandler syncHandler) {
        this.syncHandler = syncHandler;
        this.chunksPerSide = syncHandler.getChunkRadius() * 2 - 1;
        size(CELL_SIZE * this.chunksPerSide);
        tooltipAutoUpdate(true);
        tooltipBuilder(this::buildTooltip);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        ProspectingTexture texture = this.syncHandler.getTexture();
        if (texture == null) return;

        GlStateManager.color(1, 1, 1, 1);
        texture.draw(0, 0);
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        markTooltipDirty();
        int[] cell = hoveredCell(context);
        if (cell != null) {
            Gui.drawRect(cell[0] * CELL_SIZE, cell[1] * CELL_SIZE, (cell[0] + 1) * CELL_SIZE,
                    (cell[1] + 1) * CELL_SIZE, new Color(0x4B6C6C6C, true).getRGB());
        }
        super.drawForeground(context);
    }

    private int @Nullable [] hoveredCell(ModularGuiContext context) {
        if (!isHovering()) return null;
        int localX = context.getAbsMouseX() - getArea().x();
        int localY = context.getAbsMouseY() - getArea().y();
        int cellX = localX / CELL_SIZE;
        int cellY = localY / CELL_SIZE;
        if (cellX < 0 || cellY < 0 || cellX >= this.chunksPerSide || cellY >= this.chunksPerSide) return null;
        return new int[] { cellX, cellY };
    }

    private void buildTooltip(RichTooltip tooltip) {
        ProspectingTexture texture = this.syncHandler.getTexture();
        int[] cell = texture == null ? null : hoveredCell(getContext());
        if (cell == null) return;

        this.hoveredNames.clear();
        this.hoveredHeight = 0;
        if (this.syncHandler.getMode() == ProspectorMode.ORE) {
            buildOreTooltip(tooltip, texture, cell[0], cell[1]);
        } else {
            buildFluidTooltip(tooltip, texture, cell[0], cell[1]);
        }

        if (Mods.JourneyMap.isModLoaded() || Mods.VoxelMap.isModLoaded() || Mods.XaerosMinimap.isModLoaded()) {
            tooltip.addLine(IKey.lang("terminal.prospector.waypoint.add"));
        }
    }

    private void buildOreTooltip(RichTooltip tooltip, ProspectingTexture texture, int cellX, int cellY) {
        tooltip.addLine(IKey.lang("terminal.prospector.ore"));

        Map<String, Integer> oreCounts = new HashMap<>();
        Map<String, Integer> oreHeights = new HashMap<>();
        int maxCount = 0;
        for (int i = 0; i < CELL_SIZE; i++) {
            for (int j = 0; j < CELL_SIZE; j++) {
                var column = texture.map[cellX * CELL_SIZE + i][cellY * CELL_SIZE + j];
                if (column == null) continue;

                for (var entry : column.entrySet()) {
                    String dict = entry.getValue();
                    if (!ProspectingTexture.SELECTED_ALL.equals(texture.getSelected()) &&
                            !texture.getSelected().equals(dict))
                        continue;

                    String name = OreDictUnifier.get(dict).getDisplayName();
                    int count = oreCounts.merge(name, 1, Integer::sum);
                    oreHeights.merge(name, Byte.toUnsignedInt(entry.getKey()), Integer::sum);
                    if (count > maxCount) {
                        maxCount = count;
                        MaterialStack material = OreDictUnifier.getMaterial(OreDictUnifier.get(dict));
                        if (material != null) this.waypointColor = material.material.getMaterialRGB();
                    }
                }
            }
        }

        int totalCount = oreCounts.values().stream().mapToInt(Integer::intValue).sum();
        oreCounts.forEach((name, count) -> {
            int avgHeight = oreHeights.get(name) / count;
            this.hoveredHeight += avgHeight * count;
            tooltip.addLine(IKey.str(name + " --- §e" + count + "§r, §cy" + avgHeight + "§r"));
            this.hoveredNames.add(name);
        });
        if (totalCount != 0) {
            this.hoveredHeight /= totalCount;
        }
    }

    private void buildFluidTooltip(RichTooltip tooltip, ProspectingTexture texture, int cellX, int cellY) {
        tooltip.addLine(IKey.lang("terminal.prospector.fluid"));

        var column = texture.map[cellX][cellY];
        if (column == null || column.isEmpty()) return;
        if (!ProspectingTexture.SELECTED_ALL.equals(texture.getSelected()) &&
                !texture.getSelected().equals(column.get((byte) 1)))
            return;

        FluidStack fluidStack = FluidRegistry.getFluidStack(column.get((byte) 1), 1);
        if (fluidStack == null) return;

        tooltip.addLine(IKey.lang("terminal.prospector.fluid.info", fluidStack.getLocalizedName(),
                column.get((byte) 2), column.get((byte) 3)));
        this.hoveredNames.add(fluidStack.getLocalizedName());
        this.waypointColor = fluidStack.getFluid().getColor(fluidStack);
    }

    @NotNull
    @Override
    public Result onMousePressed(int mouseButton) {
        int[] cell = hoveredCell(getContext());
        if (cell == null) return Result.IGNORE;

        int xPos = ((Minecraft.getMinecraft().player.chunkCoordX + cell[0] - this.syncHandler.getChunkRadius() + 1)
                << 4) + 8;
        int zPos = ((Minecraft.getMinecraft().player.chunkCoordZ + cell[1] - this.syncHandler.getChunkRadius() + 1)
                << 4) + 8;
        int yPos = this.hoveredHeight != 0 ? this.hoveredHeight : Minecraft.getMinecraft().world.getHeight(xPos, zPos);
        BlockPos pos = new BlockPos(xPos, yPos, zPos);

        long now = System.currentTimeMillis();
        boolean isDoubleClick = now - this.lastClickTime < DOUBLE_CLICK_MS;
        this.lastClickTime = now;
        if (isDoubleClick && !this.hoveredNames.isEmpty() && addWaypoint(pos)) {
            Minecraft.getMinecraft().player
                    .sendStatusMessage(new TextComponentTranslation("behavior.prospector.added_waypoint"), true);
        }
        return Result.ACCEPT;
    }

    private boolean addWaypoint(BlockPos pos) {
        trimHoveredNames();
        if (Mods.JourneyMap.isModLoaded()) {
            return addJourneymapWaypoint(pos);
        } else if (Mods.VoxelMap.isModLoaded()) {
            return addVoxelMapWaypoint(pos);
        } else if (Mods.XaerosMinimap.isModLoaded()) {
            return addXaeroMapWaypoint(pos);
        }
        return false;
    }

    private void trimHoveredNames() {
        for (OreDepositDefinition deposit : WorldGenRegistry.getOreDeposits()) {
            for (FillerEntry fillerEntry : deposit.getBlockFiller().getAllPossibleStates()) {
                Collection<String> matches = new ArrayList<>();
                for (IBlockState state : fillerEntry.getPossibleResults()) {
                    for (String dict : OreDictUnifier.getOreDictionaryNames(new ItemStack(state.getBlock()))) {
                        String name = OreDictUnifier.get(dict).getDisplayName();
                        if (this.hoveredNames.contains(name)) {
                            matches.add(name);
                        }
                    }
                }
                if (matches.size() > fillerEntry.getPossibleResults().size() / 2) {
                    this.hoveredNames.removeAll(matches);
                    this.hoveredNames.add(FileUtility.trimFileName(deposit.getDepositName()));
                }
            }
        }
    }

    @NotNull
    private String createVeinName() {
        String s = this.hoveredNames.toString();
        return s.substring(1, s.length() - 1);
    }

    @Optional.Method(modid = Mods.Names.JOURNEY_MAP)
    private boolean addJourneymapWaypoint(BlockPos pos) {
        journeymap.client.model.Waypoint waypoint = new journeymap.client.model.Waypoint(createVeinName(), pos,
                new Color(this.waypointColor), journeymap.client.model.Waypoint.Type.Normal,
                Minecraft.getMinecraft().world.provider.getDimension());
        if (journeymap.client.waypoint.WaypointStore.INSTANCE.getAll().contains(waypoint)) return false;

        journeymap.client.waypoint.WaypointStore.INSTANCE.save(waypoint);
        return true;
    }

    @Optional.Method(modid = Mods.Names.VOXEL_MAP)
    private boolean addVoxelMapWaypoint(@NotNull BlockPos pos) {
        Color color = new Color(this.waypointColor);
        TreeSet<Integer> world = new TreeSet<>();
        world.add(Minecraft.getMinecraft().world.provider.getDimension());

        var waypointManager = com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap.getInstance().getWaypointManager();
        var waypoint = new com.mamiyaotaru.voxelmap.util.Waypoint(createVeinName(), pos.getX(), pos.getZ(), pos.getY(),
                true, color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F,
                Minecraft.getMinecraft().world.provider.getDimensionType().getSuffix(),
                Minecraft.getMinecraft().world.provider.getDimensionType().getName(), world);

        if (waypointManager.getWaypoints().contains(waypoint)) return false;

        waypointManager.addWaypoint(waypoint);
        waypointManager.saveWaypoints();
        return true;
    }

    @Optional.Method(modid = Mods.Names.XAEROS_MINIMAP)
    private boolean addXaeroMapWaypoint(@NotNull BlockPos pos) {
        Color color = new Color(clampColor(this.waypointColor >> 16 & 0xFF), clampColor(this.waypointColor >> 8 & 0xFF),
                clampColor(this.waypointColor & 0xFF));
        int bestColorIndex = closestXaeroColorIndex(ColorUtility.getLab(color));

        var session = xaero.common.XaeroMinimapSession.getCurrentSession();
        var waypoints = session.getWaypointsManager().getWaypoints();
        var world = session.getWaypointsManager().getCurrentWorld();
        var waypoint = new xaero.common.minimap.waypoints.Waypoint(pos.getX(), pos.getY(), pos.getZ(), createVeinName(),
                this.hoveredNames.get(0).substring(0, 1), bestColorIndex);

        for (var existing : waypoints.getList()) {
            if (existing.getX() == waypoint.getX() && existing.getY() == waypoint.getY() &&
                    existing.getZ() == waypoint.getZ())
                return false;
        }

        waypoints.getList().add(waypoint);
        try {
            session.getModMain().getSettings().saveWaypoints(world);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private static int closestXaeroColorIndex(double[] labColor) {
        int bestIndex = 0;
        double closestDistance = Double.MAX_VALUE;
        for (int i = 0; i < XAERO_COLORS.length; i++) {
            double[] c = XAERO_COLORS[i];
            double dl = c[0] - labColor[0];
            double da = c[1] - labColor[1];
            double db = c[2] - labColor[2];
            double distance = dl * dl + da * da + db * db;
            if (distance < closestDistance) {
                closestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int clampColor(int color) {
        if (color < 32) return 0;
        if (color < 128) return 128;
        if (color < 192) return 192;
        return 255;
    }
}
