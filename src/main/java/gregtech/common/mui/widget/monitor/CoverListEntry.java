package gregtech.common.mui.widget.monitor;

import gregtech.api.pipenet.tile.PipeCoverableImplementation;
import gregtech.client.renderer.handler.BlockPosHighlightRenderer;
import gregtech.common.covers.CoverDigitalInterface;
import gregtech.common.mui.widget.FakeItemSlot;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single row of {@link WidgetCoverList}: an item icon representing the covered block/pipe and its position.
 * Clicking the icon closes the GUI and highlights the block in-world; clicking elsewhere toggles selection.
 */
public class CoverListEntry extends ParentWidget<CoverListEntry> implements Interactable {

    private static final IDrawable SELECTED_HIGHLIGHT = new Rectangle().color(0x4BFFFFFF);
    private static final int ICON_WIDTH = 18;

    private final ItemStack icon;
    private final BlockPos highlightPos;
    private final int index;
    private final IntSyncValue selectedIndexSync;

    /**
     * @return {@code null} if this cover currently has no representable icon (e.g. the pipe it is attached to
     *         has no neighboring block), matching the old list's behaviour of simply skipping such covers.
     */
    @Nullable
    public static CoverListEntry create(@NotNull CoverDigitalInterface cover, int index,
                                        @NotNull IntSyncValue selectedIndexSync) {
        BlockPos pos = cover.getPos();
        ItemStack icon = cover.getPickItem();
        if (cover.getCoverableView() instanceof PipeCoverableImplementation) {
            pos = pos.offset(cover.getAttachedSide());
            TileEntity tileEntity = cover.getWorld().getTileEntity(pos);
            if (tileEntity == null) return null;
            IBlockState state = cover.getWorld().getBlockState(pos);
            icon = tileEntity.getBlockType().getItem(cover.getWorld(), pos, state);
            if (icon == null || icon.isEmpty()) return null;
        }
        return new CoverListEntry(icon, pos, index, selectedIndexSync);
    }

    private CoverListEntry(@NotNull ItemStack icon, @NotNull BlockPos highlightPos, int index,
                           @NotNull IntSyncValue selectedIndexSync) {
        this.icon = icon;
        this.highlightPos = highlightPos;
        this.index = index;
        this.selectedIndexSync = selectedIndexSync;

        height(ICON_WIDTH);
        overlay(new DynamicDrawable(
                () -> selectedIndexSync.getIntValue() == index ? SELECTED_HIGHLIGHT : IDrawable.NONE));
        child(new FakeItemSlot(false).item(icon).pos(0, 0).size(16, 16));
        child(IKey.str(String.format("(%d, %d, %d)", highlightPos.getX(), highlightPos.getY(), highlightPos.getZ()))
                .asWidget().pos(20, 5));
    }

    @NotNull
    @Override
    public Result onMousePressed(int mouseButton) {
        int localX = getContext().getAbsMouseX() - getArea().x();
        if (localX < ICON_WIDTH) {
            Minecraft mc = Minecraft.getMinecraft();
            BlockPosHighlightRenderer.renderBlockBoxHighLight(highlightPos, 5000);
            mc.player.closeScreen();
            mc.player.sendMessage(new TextComponentString(icon.getDisplayName() + ": (" + highlightPos.getX() + ", " +
                    highlightPos.getY() + ", " + highlightPos.getZ() + ")"));
            return Result.SUCCESS;
        }
        selectedIndexSync.setIntValue(selectedIndexSync.getIntValue() == index ? -1 : index);
        return Result.SUCCESS;
    }
}
