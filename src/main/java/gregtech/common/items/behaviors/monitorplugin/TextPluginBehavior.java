package gregtech.common.items.behaviors.monitorplugin;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.client.utils.RenderUtil;
import gregtech.common.mui.widget.GTTextFieldWidget;
import gregtech.common.mui.widget.WidgetARGB;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;

import java.util.Arrays;

public class TextPluginBehavior extends MonitorPluginBaseBehavior {

    public String[] texts;
    public int[] colors;

    public void setText(int line, String text, int color) {
        if (line < 0 || line > texts.length || (texts[line].equals(text) && colors[line] == color)) return;
        this.texts[line] = text;
        this.colors[line] = color;
        writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CONFIG, packetBuffer -> {
            packetBuffer.writeInt(texts.length);
            for (int i = 0; i < texts.length; i++) {
                packetBuffer.writeString(texts[i]);
                packetBuffer.writeInt(colors[i]);
            }
        });
        markAsDirty();
    }

    @Override
    public void readPluginData(int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.UPDATE_PLUGIN_CONFIG) {
            texts = new String[buf.readInt()];
            colors = new int[texts.length];
            for (int i = 0; i < texts.length; i++) {
                texts[i] = buf.readString(100);
                colors[i] = buf.readInt();
            }
        }
    }

    @Override
    public MonitorPluginBaseBehavior createPlugin() {
        TextPluginBehavior plugin = new TextPluginBehavior();
        plugin.texts = new String[16];
        plugin.colors = new int[16];
        return plugin;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        for (int i = 0; i < texts.length; i++) {
            data.setString("t" + i, texts[i]);
        }
        data.setIntArray("color", colors);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        for (int i = 0; i < texts.length; i++) {
            texts[i] = data.hasKey("t" + i) ? data.getString("t" + i) : "";
        }
        if (data.hasKey("color")) {
            colors = data.getIntArray("color");
        } else {
            Arrays.fill(colors, 0XFFFFFFFF);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderPlugin(float partialTicks, RayTraceResult rayTraceResult) {
        for (int i = 0; i < texts.length; i++) {
            RenderUtil.renderText(-0.5f, -0.4625f + i / 16f, 0.002f, 1 / 128f, colors[i], texts[i], false);
        }
    }

    @Override
    public boolean hasUI() {
        return true;
    }

    @Override
    public IWidget customUI(PanelSyncManager syncManager) {
        ParentWidget<?> panel = new ParentWidget<>();
        for (int i = 0; i < texts.length; i++) {
            int line = i;
            panel.child(new GTTextFieldWidget()
                    .pos(25, 25 + line * 10).size(100, 10)
                    .value(new StringSyncValue(() -> texts[line], text -> setText(line, text, colors[line]))));
            panel.child(new WidgetARGB(10, () -> colors[line], color -> setText(line, texts[line], color))
                    .pos(135, 25 + line * 10));
        }
        return panel;
    }
}
