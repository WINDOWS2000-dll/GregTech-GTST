package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.mui.widget.GTTextFieldWidget;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MetaTileEntityAlarm extends TieredMetaTileEntity {

    private SoundEvent selectedSound;
    private boolean isActive;
    private int radius = 64;
    public static final int BASE_EU_CONSUMPTION = 4;

    public MetaTileEntityAlarm(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 1);
        selectedSound = GTSoundEvents.DEFAULT_ALARM;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityAlarm(metaTileEntityId);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.uses_per_tick", BASE_EU_CONSUMPTION));
        tooltip.add(
                I18n.format("gregtech.universal.tooltip.energy_storage_capacity", energyContainer.getEnergyCapacity()));
    }

    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.isActive) {
            Textures.ALARM_OVERLAY_ACTIVE.renderSided(getFrontFacing(), renderState, translation, pipeline);
        } else {
            Textures.ALARM_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        StringSyncValue soundValue = new StringSyncValue(() -> getNameOfSound(this.selectedSound), v -> {
            if (this.getWorld().isRemote) GregTechAPI.soundManager.stopTileSound(getPos());
            SoundEvent newSound = SoundEvent.REGISTRY.getObject(new ResourceLocation(v));
            if (this.selectedSound != newSound) {
                this.selectedSound = newSound;
                this.writeCustomData(GregtechDataCodes.UPDATE_SOUND,
                        writer -> writer.writeResourceLocation(getResourceLocationOfSound(this.selectedSound)));
            }
        });

        StringSyncValue radiusValue = new StringSyncValue(() -> String.valueOf(radius), value -> {
            if (!value.isEmpty()) {
                int newRadius = Integer.parseInt(value);
                if (newRadius != radius) {
                    this.writeCustomData(GregtechDataCodes.UPDATE_RADIUS,
                            writer -> writer.writeInt(newRadius));
                    radius = newRadius;
                }
            }
        });

        return GTGuis.createPanel(this, 240, 86)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(10, 5))
                .child(new DropdownWidget<>("alarm_sound", String.class)
                        .pos(10, 20)
                        .size(220, 20)
                        .optionToWidget((v, forSelectedDisplay) -> {
                            var widget = IKey.str(v).alignment(Alignment.Center).asWidget().widthRel(1f);
                            // Only the closed-menu display can fill the dropdown's fixed height; menu rows
                            // size themselves to their text (their ButtonWidget parent uses coverChildrenHeight,
                            // which conflicts with a child that demands a relative height).
                            if (forSelectedDisplay) {
                                widget.heightRel(1f);
                            }
                            return widget;
                        })
                        .options(getSounds().stream().map(this::getNameOfSound).collect(Collectors.toList()))
                        .value(soundValue))
                .child(IKey.lang("gregtech.gui.alarm.radius").asWidget().pos(10, 44))
                .child(GTGuiTextures.DISPLAY.asWidget().pos(10, 54).size(220, 20))
                .child(new GTTextFieldWidget()
                        .pos(12, 56)
                        .size(216, 16)
                        .paddingTop(0)
                        .paddingBottom(0)
                        .setMaxLength(10)
                        .setNumbers(0, 128)
                        .setTextAlignment(Alignment.Center)
                        .setScale(1.1f)
                        .value(radiusValue));
    }

    protected List<SoundEvent> getSounds() {
        if (GTValues.FOOLS.get() && ConfigHolder.misc.specialEvents) {
            return Arrays.asList(GTSoundEvents.DEFAULT_ALARM, GTSoundEvents.ARC, SoundEvents.ENTITY_WOLF_HOWL,
                    SoundEvents.ENTITY_ENDERMEN_DEATH, GTSoundEvents.SUS_RECORD);
        }
        return Arrays.asList(GTSoundEvents.DEFAULT_ALARM, GTSoundEvents.ARC, SoundEvents.ENTITY_WOLF_HOWL,
                SoundEvents.ENTITY_ENDERMEN_DEATH);
    }

    @Override
    public SoundEvent getSound() {
        return selectedSound;
    }

    @Override
    public boolean isActive() {
        if (this.getWorld().isRemote) {
            return isActive;
        }
        return this.isBlockRedstonePowered() &&
                this.energyContainer.changeEnergy(-BASE_EU_CONSUMPTION) == -BASE_EU_CONSUMPTION;
    }

    @Override
    public void update() {
        super.update();
        if (!this.getWorld().isRemote) {
            if (this.isActive != this.isActive()) {
                this.writeCustomData(GregtechDataCodes.UPDATE_ACTIVE, (writer) -> writer.writeBoolean(this.isActive()));
                this.isActive = this.isActive();
            }
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_ACTIVE) {
            this.isActive = buf.readBoolean();
            this.scheduleRenderUpdate();
        } else if (dataId == GregtechDataCodes.UPDATE_SOUND) {
            this.selectedSound = SoundEvent.REGISTRY.getObject(buf.readResourceLocation());
            GregTechAPI.soundManager.stopTileSound(getPos());
        } else if (dataId == GregtechDataCodes.UPDATE_RADIUS) {
            this.radius = buf.readInt();
            GregTechAPI.soundManager.stopTileSound(getPos());
        }
    }

    @Override
    public float getVolume() {
        return radius / 16f;
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.isActive = buf.readBoolean();
        this.selectedSound = SoundEvent.REGISTRY.getObject(buf.readResourceLocation());
        this.radius = buf.readInt();
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(this.isActive);
        buf.writeResourceLocation(getResourceLocationOfSound(this.selectedSound));
        buf.writeInt(this.radius);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        data.setBoolean("isActive", this.isActive);
        data.setString("selectedSound", getNameOfSound(this.selectedSound));
        data.setInteger("radius", this.radius);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        this.isActive = data.getBoolean("isActive");
        this.selectedSound = SoundEvent.REGISTRY.getObject(new ResourceLocation(data.getString("selectedSound")));
        this.radius = data.getInteger("radius");
        super.readFromNBT(data);
    }

    public String getNameOfSound(SoundEvent sound) {
        return getResourceLocationOfSound(sound).toString();
    }

    public ResourceLocation getResourceLocationOfSound(SoundEvent sound) {
        return SoundEvent.REGISTRY.getNameForObject(sound);
    }
}
