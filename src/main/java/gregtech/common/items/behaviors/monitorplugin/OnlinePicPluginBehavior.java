package gregtech.common.items.behaviors.monitorplugin;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.client.renderer.texture.picture.DownloadThread;
import gregtech.client.renderer.texture.picture.PictureTexture;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;

public class OnlinePicPluginBehavior extends MonitorPluginBaseBehavior {

    public String url;
    public float scaleX;
    public float scaleY;
    public float rotation;
    public boolean flippedX;
    public boolean flippedY;

    // run-time
    private String tmpUrl;

    @SideOnly(Side.CLIENT)
    private DownloadThread downloader;
    @SideOnly(Side.CLIENT)
    public PictureTexture texture;
    @SideOnly(Side.CLIENT)
    public boolean failed;
    @SideOnly(Side.CLIENT)
    public String error;

    public void setConfig(String url, float rotation, float scaleX, float scaleY, boolean flippedX, boolean flippedY) {
        if (url.length() > 200 || (this.url.equals(url) && this.rotation == rotation && this.scaleX == scaleX &&
                this.scaleY == scaleY && this.flippedX == flippedX && this.flippedY == flippedY))
            return;
        this.url = url;
        this.rotation = rotation;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.flippedX = flippedX;
        this.flippedY = flippedY;
        writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CONFIG, packetBuffer -> {
            packetBuffer.writeString(url);
            packetBuffer.writeFloat(rotation);
            packetBuffer.writeFloat(scaleX);
            packetBuffer.writeFloat(scaleY);
            packetBuffer.writeBoolean(flippedX);
            packetBuffer.writeBoolean(flippedY);
        });
        markAsDirty();
    }

    @Override
    public void readPluginData(int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.UPDATE_PLUGIN_CONFIG) {
            String _url = buf.readString(200);
            if (!this.url.equals(_url)) {
                this.url = _url;
                this.texture = null;
                this.failed = false;
                this.error = null;
            }
            this.rotation = buf.readFloat();
            this.scaleX = buf.readFloat();
            this.scaleY = buf.readFloat();
            this.flippedX = buf.readBoolean();
            this.flippedY = buf.readBoolean();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setString("url", url);
        data.setFloat("rotation", rotation);
        data.setFloat("scaleX", scaleX);
        data.setFloat("scaleY", scaleY);
        data.setBoolean("flippedX", flippedX);
        data.setBoolean("flippedY", flippedY);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.url = data.hasKey("url") ? data.getString("url") : "";
        this.rotation = data.hasKey("rotation") ? data.getFloat("rotation") : 0;
        this.scaleX = data.hasKey("scaleX") ? data.getFloat("scaleX") : 1;
        this.scaleY = data.hasKey("scaleY") ? data.getFloat("scaleY") : 1;
        this.flippedX = data.hasKey("flippedX") && data.getBoolean("flippedX");
        this.flippedY = data.hasKey("flippedY") && data.getBoolean("flippedY");
    }

    @Override
    public MonitorPluginBaseBehavior createPlugin() {
        return new OnlinePicPluginBehavior();
    }

    @Override
    public IWidget customUI(PanelSyncManager syncManager) {
        tmpUrl = url;

        DoubleSyncValue rotationValue = new DoubleSyncValue(
                () -> (this.rotation + 180) / 360.0,
                v -> setConfig(this.url, (float) Math.round(v * 360 - 180), this.scaleX, this.scaleY, this.flippedX,
                        this.flippedY));
        DoubleSyncValue scaleXValue = new DoubleSyncValue(
                () -> (double) this.scaleX,
                v -> setConfig(this.url, this.rotation, (float) (Math.round(v / 0.05) * 0.05), this.scaleY,
                        this.flippedX, this.flippedY));
        DoubleSyncValue scaleYValue = new DoubleSyncValue(
                () -> (double) this.scaleY,
                v -> setConfig(this.url, this.rotation, this.scaleX, (float) (Math.round(v / 0.05) * 0.05),
                        this.flippedX, this.flippedY));
        BooleanSyncValue flippedXValue = new BooleanSyncValue(() -> this.flippedX,
                v -> setConfig(this.url, this.rotation, this.scaleX, this.scaleY, v, this.flippedY));
        BooleanSyncValue flippedYValue = new BooleanSyncValue(() -> this.flippedY,
                v -> setConfig(this.url, this.rotation, this.scaleX, this.scaleY, this.flippedX, v));

        // give the panel a fixed size so children (esp. plain text labels) resolve a sane default width
        // instead of falling back to a near-zero one and wrapping their text mid-word
        ParentWidget<?> panel = new ParentWidget<>().size(250, 190);
        panel.child(IKey.dynamic(() -> url.length() > 40 ? (url.substring(0, 39) + "...") : url)
                .asWidget().pos(20, 15).size(225, 10));
        panel.child(new GTTextFieldWidget()
                .pos(20, 28).size(175, 12)
                .setMaxLength(200)
                .value(new StringSyncValue(() -> tmpUrl, text -> tmpUrl = text)));
        panel.child(new ButtonWidget<>()
                .pos(200, 28).size(45, 12)
                .overlay(IKey.str("confirm"))
                .onMousePressed(mouseButton -> {
                    setConfig(tmpUrl, this.rotation, this.scaleX, this.scaleY, this.flippedX, this.flippedY);
                    return true;
                }));

        addAdjustableRow(panel, 46, "rotation", rotationValue,
                () -> String.format("%.0f", this.rotation),
                () -> setConfig(this.url, MathHelper.clamp(this.rotation - 1, -180, 180), this.scaleX, this.scaleY,
                        this.flippedX, this.flippedY),
                () -> setConfig(this.url, MathHelper.clamp(this.rotation + 1, -180, 180), this.scaleX, this.scaleY,
                        this.flippedX, this.flippedY));

        addAdjustableRow(panel, 82, "scaleX", scaleXValue,
                () -> String.format("%.2f", this.scaleX),
                () -> setConfig(this.url, this.rotation,
                        (float) MathHelper.clamp(Math.round((this.scaleX - 0.05) / 0.05) * 0.05, 0.0, 1.0),
                        this.scaleY, this.flippedX, this.flippedY),
                () -> setConfig(this.url, this.rotation,
                        (float) MathHelper.clamp(Math.round((this.scaleX + 0.05) / 0.05) * 0.05, 0.0, 1.0),
                        this.scaleY, this.flippedX, this.flippedY));

        addAdjustableRow(panel, 118, "scaleY", scaleYValue,
                () -> String.format("%.2f", this.scaleY),
                () -> setConfig(this.url, this.rotation, this.scaleX,
                        (float) MathHelper.clamp(Math.round((this.scaleY - 0.05) / 0.05) * 0.05, 0.0, 1.0),
                        this.flippedX, this.flippedY),
                () -> setConfig(this.url, this.rotation, this.scaleX,
                        (float) MathHelper.clamp(Math.round((this.scaleY + 0.05) / 0.05) * 0.05, 0.0, 1.0),
                        this.flippedX, this.flippedY));

        panel.child(IKey.str("flippedX:").asWidget().pos(25, 158).size(70, 10));
        panel.child(new ToggleButton().pos(100, 154).size(20).value(flippedXValue)
                .overlay(IKey.dynamic(() -> this.flippedX ? "ON" : "OFF")));
        panel.child(IKey.str("flippedY:").asWidget().pos(140, 158).size(70, 10));
        panel.child(new ToggleButton().pos(215, 154).size(20).value(flippedYValue)
                .overlay(IKey.dynamic(() -> this.flippedY ? "ON" : "OFF")));
        return panel;
    }

    @Override
    public void update() {
        if (this.screen != null && this.screen.getWorld().isRemote) {
            if (this.texture != null) {
                texture.tick(); // gif update
            }
        }
    }

    @Override
    public void renderPlugin(float partialTicks, RayTraceResult rayTraceResult) {
        if (!this.url.isEmpty()) {
            if (texture != null && texture.hasTexture()) {
                texture.render(-0.5f, -0.5f, 1, 1, this.rotation, this.scaleX, this.scaleY, this.flippedX,
                        this.flippedY);
            } else
                this.loadTexture();
        }
    }

    @SideOnly(Side.CLIENT)
    public void loadTexture() {
        if (texture == null && !failed) {
            if (downloader == null && DownloadThread.activeDownloads < DownloadThread.MAXIMUM_ACTIVE_DOWNLOADS) {
                PictureTexture loadedTexture = DownloadThread.loadedImages.get(url);

                if (loadedTexture == null) {
                    synchronized (DownloadThread.LOCK) {
                        if (!DownloadThread.loadingImages.contains(url)) {
                            downloader = new DownloadThread(url);
                            return;
                        }
                    }
                } else {
                    texture = loadedTexture;
                }
            }
            if (downloader != null && downloader.hasFinished()) {
                if (downloader.hasFailed()) {
                    failed = true;
                    error = downloader.getError();
                    DownloadThread.LOGGER.error("Could not load image of " +
                            (this.screen != null ? this.screen.getPos().toString() : "") + " " + error);
                } else {
                    texture = DownloadThread.loadImage(downloader);
                }
                downloader = null;
            }
        }
    }
}
