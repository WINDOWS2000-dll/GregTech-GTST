package gregtech.api.mui.factory;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MetaTileEntityGuiFactory extends AbstractUIFactory<PosGuiData> {

    public static final MetaTileEntityGuiFactory INSTANCE = new MetaTileEntityGuiFactory();

    private MetaTileEntityGuiFactory() {
        super("gregtech:mte");
    }

    public static <T extends MetaTileEntity & IGuiHolder<PosGuiData>> void open(EntityPlayer player, T mte) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(mte);
        if (!mte.isValid()) {
            throw new IllegalArgumentException("Can't open invalid MetaTileEntity GUI!");
        }
        if (player.world != mte.getWorld()) {
            throw new IllegalArgumentException("MetaTileEntity must be in same dimension as the player!");
        }
        BlockPos pos = mte.getPos();
        PosGuiData data = new PosGuiData(player, pos.getX(), pos.getY(), pos.getZ());
        GuiManager.open(INSTANCE, data, (EntityPlayerMP) player);
    }

    @Override
    public @NotNull IGuiHolder<PosGuiData> getGuiHolder(PosGuiData data) {
        TileEntity te = data.getTileEntity();
        if (te instanceof IGregTechTileEntity gtte) {
            MetaTileEntity mte = gtte.getMetaTileEntity();
            return Objects.requireNonNull(castGuiHolder(mte), "Found MetaTileEntity is not a gui holder!");
        }
        throw new IllegalStateException("Found TileEntity is not a MetaTileEntity!");
    }

    @Override
    public ModularPanel createPanel(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = super.createPanel(guiData, syncManager, settings);
        // paint tint is a purely visual, client-side overlay; avoid touching client-only rendering
        // classes (GlStateManager, Gui) while building the panel on the server.
        if (panel != null && NetworkUtils.isClient()) {
            IGuiHolder<PosGuiData> holder = getGuiHolder(guiData);
            if (holder instanceof MetaTileEntity mte) {
                int colorOverride = mte.getUIColorOverride();
                if (colorOverride != -1) {
                    panel.overlay(new PaintTintOverlay(colorOverride));
                }
            }
        }
        return panel;
    }

    /**
     * Multiplies the panel's already-drawn contents by the given color, replicating the spray can painting tint
     * that MetaTileEntity GUIs used to apply in MUI1.
     */
    @SideOnly(Side.CLIENT)
    @Desugar
    private record PaintTintOverlay(int color) implements IDrawable {

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
            Gui.drawRect(x, y, x + width, y + height, 0xFF000000 | color);
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
        }
    }

    @Override
    public void writeGuiData(PosGuiData guiData, PacketBuffer buffer) {
        buffer.writeVarInt(guiData.getX());
        buffer.writeVarInt(guiData.getY());
        buffer.writeVarInt(guiData.getZ());
    }

    @Override
    public @NotNull PosGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new PosGuiData(player, buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }
}
