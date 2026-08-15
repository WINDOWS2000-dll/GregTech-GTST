package gregtech.common.metatileentities.storage;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.ILaserContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

import static gregtech.api.GTValues.MAX;
import static gregtech.api.GTValues.V;
import static gregtech.api.capability.GregtechDataCodes.UPDATE_ACTIVE;
import static gregtech.api.capability.GregtechDataCodes.UPDATE_IO_SPEED;

public class MetaTileEntityCreativeEnergy extends MetaTileEntity implements ILaserContainer, IControllable {

    private long voltage = 0;
    private int amps = 1;

    private int setTier = 0;
    private boolean active = false;
    private boolean source = true;

    private long lastEnergyIOPerSec = 0;
    private long energyIOPerSec = 0;

    private long ampsReceived = 0;
    private boolean doExplosion = false;

    public MetaTileEntityCreativeEnergy() {
        super(GTUtility.gregtechId("infinite_energy"));
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        IVertexOperation[] renderPipeline = ArrayUtils.add(pipeline,
                new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
        Textures.VOLTAGE_CASINGS[14].render(renderState, translation, renderPipeline, Cuboid6.full);
        for (EnumFacing face : EnumFacing.VALUES) {
            Textures.INFINITE_EMITTER_FACE.renderSided(face, renderState, translation, pipeline);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.VOLTAGE_CASINGS[this.setTier].getParticleSprite(), this.getPaintingColorForRendering());
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCreativeEnergy();
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER) {
            return GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER.cast(this);
        } else if (capability == GregtechTileCapabilities.CAPABILITY_LASER) {
            return GregtechTileCapabilities.CAPABILITY_LASER.cast(this);
        } else if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        } else {
            return super.getCapability(capability, side);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        IntSyncValue tierValue = new IntSyncValue(() -> setTier, tier -> {
            setTier = tier;
            voltage = GTValues.V[setTier];
        });
        CycleButtonWidget tierButton = new CycleButtonWidget()
                .pos(7, 7)
                .size(30, 20)
                .value(tierValue)
                .stateCount(GTValues.VNF.length);
        for (int i = 0; i < GTValues.VNF.length; i++) {
            tierButton.stateOverlay(i, IKey.str(GTValues.VNF[i]));
        }

        LongSyncValue voltageValue = new LongSyncValue(() -> voltage, v -> {
            voltage = v;
            setTier = GTUtility.getTierByVoltage(voltage);
        });
        IntSyncValue ampsValue = new IntSyncValue(() -> amps, v -> amps = v);
        IntSyncValue activeValue = new IntSyncValue(() -> active ? 1 : 0, v -> setActive(v != 0));
        IntSyncValue sourceValue = new IntSyncValue(() -> source ? 1 : 0, v -> {
            source = v != 0;
            if (source) {
                voltage = 0;
                amps = 0;
                setTier = 0;
            } else {
                voltage = V[MAX];
                amps = Integer.MAX_VALUE;
                setTier = MAX;
            }
        });

        return GTGuis.createPanel(this, 176, 166)
                .child(tierButton)
                .child(IKey.lang("gregtech.creative.energy.voltage").asWidget().pos(7, 32))
                .child(GTGuiTextures.DISPLAY.asWidget().pos(7, 44).size(156, 20))
                .child(new GTTextFieldWidget()
                        .pos(9, 46)
                        .size(152, 16)
                        .paddingTop(0)
                        .paddingBottom(0)
                        .setMaxLength(19)
                        .setValidator(getTextFieldValidator())
                        .setTextAlignment(Alignment.Center)
                        .value(voltageValue))
                .child(IKey.lang("gregtech.creative.energy.amperage").asWidget().pos(7, 74))
                .child(new ButtonWidget<>()
                        .pos(7, 87)
                        .size(20, 20)
                        .overlay(IKey.str("-"))
                        .onMousePressed(data -> {
                            ampsValue.setIntValue(Math.max(0, ampsValue.getIntValue() - 1));
                            return true;
                        }))
                .child(GTGuiTextures.DISPLAY.asWidget().pos(29, 87).size(118, 20))
                .child(new GTTextFieldWidget()
                        .pos(31, 89)
                        .size(114, 16)
                        .paddingTop(0)
                        .paddingBottom(0)
                        .setMaxLength(10)
                        .setNumbers(0, Integer.MAX_VALUE)
                        .setTextAlignment(Alignment.Center)
                        .value(ampsValue))
                .child(new ButtonWidget<>()
                        .pos(149, 87)
                        .size(20, 20)
                        .overlay(IKey.str("+"))
                        .onMousePressed(data -> {
                            if (ampsValue.getIntValue() < Integer.MAX_VALUE) {
                                ampsValue.setIntValue(ampsValue.getIntValue() + 1);
                            }
                            return true;
                        }))
                .child(IKey.dynamic(() -> "Energy I/O per sec: " + this.lastEnergyIOPerSec).asWidget().pos(7, 110))
                .child(new CycleButtonWidget()
                        .pos(7, 139)
                        .size(77, 20)
                        .value(activeValue)
                        .stateCount(2)
                        .stateOverlay(0, IKey.lang("gregtech.creative.activity.off"))
                        .stateOverlay(1, IKey.lang("gregtech.creative.activity.on")))
                .child(new CycleButtonWidget()
                        .pos(85, 139)
                        .size(77, 20)
                        .value(sourceValue)
                        .stateCount(2)
                        .stateOverlay(0, IKey.lang("gregtech.creative.energy.sink"))
                        .stateOverlay(1, IKey.lang("gregtech.creative.energy.source")));
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!getWorld().isRemote) {
            writeCustomData(GregtechDataCodes.UPDATE_ACTIVE, buf -> buf.writeBoolean(active));
            markDirty();
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.creative_tooltip.1") + TooltipHelper.RAINBOW +
                I18n.format("gregtech.creative_tooltip.2") + I18n.format("gregtech.creative_tooltip.3"));
    }

    @Override
    public long getOutputPerSec() {
        return lastEnergyIOPerSec;
    }

    @Override
    public void update() {
        super.update();
        if (getWorld().isRemote) return;
        if (getOffsetTimer() % 20 == 0) {
            this.setIOSpeed(energyIOPerSec);
            energyIOPerSec = 0;
            if (doExplosion) {
                getWorld().createExplosion(null, getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5,
                        1, false);
                doExplosion = false;
            }
        }
        ampsReceived = 0;
        if (!active || !source || voltage <= 0 || amps <= 0) return;
        long ampsUsed = 0;
        for (EnumFacing facing : EnumFacing.values()) {
            EnumFacing opposite = facing.getOpposite();
            TileEntity tile = getNeighbor(facing);
            if (tile != null) {
                IEnergyContainer container = tile.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                        opposite);
                // Try to get laser capability
                if (container == null)
                    container = tile.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, opposite);

                if (container == null || !container.inputsEnergy(opposite) || container.getEnergyCanBeInserted() == 0)
                    continue;
                ampsUsed += container.acceptEnergyFromNetwork(opposite, voltage, amps - ampsUsed);
                if (ampsUsed >= amps)
                    break;
            }
        }
        energyIOPerSec += ampsUsed * voltage;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        data.setLong("Voltage", voltage);
        data.setInteger("Amps", amps);
        data.setByte("Tier", (byte) setTier);
        data.setBoolean("Active", active);
        data.setBoolean("Source", source);
        data.setLong("EnergyIOPerSec", lastEnergyIOPerSec);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        voltage = data.getLong("Voltage");
        amps = data.getInteger("Amps");
        setTier = data.getByte("Tier");
        active = data.getBoolean("Active");
        source = data.getBoolean("Source");
        if (data.hasKey("EnergyIOPerSec"))
            lastEnergyIOPerSec = data.getLong("EnergyIOPerSec");
        super.readFromNBT(data);
    }

    @Override
    public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
        if (source || !active || ampsReceived >= amps) {
            return 0;
        }
        if (voltage > this.voltage) {
            if (doExplosion)
                return 0;
            doExplosion = true;
            return Math.min(amperage, getInputAmperage() - ampsReceived);
        }
        long amperesAccepted = Math.min(amperage, getInputAmperage() - ampsReceived);
        if (amperesAccepted > 0) {
            ampsReceived += amperesAccepted;
            energyIOPerSec += amperesAccepted * voltage;
            return amperesAccepted;
        }
        return 0;
    }

    @Override
    public boolean inputsEnergy(EnumFacing side) {
        return !source;
    }

    @Override
    public boolean outputsEnergy(EnumFacing side) {
        return source;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        if (source || !active) {
            return 0;
        }
        energyIOPerSec += differenceAmount;
        return differenceAmount;
    }

    @Override
    public long getEnergyStored() {
        return 69;
    }

    @Override
    public long getEnergyCapacity() {
        return 420;
    }

    @Override
    public long getInputAmperage() {
        return source ? 0 : amps;
    }

    @Override
    public long getInputVoltage() {
        return source ? 0 : voltage;
    }

    @Override
    public long getOutputVoltage() {
        return source ? voltage : 0;
    }

    @Override
    public long getOutputAmperage() {
        return source ? amps : 0;
    }

    public void setIOSpeed(long energyIOPerSec) {
        if (this.lastEnergyIOPerSec != energyIOPerSec) {
            this.lastEnergyIOPerSec = energyIOPerSec;
            this.writeCustomData(UPDATE_IO_SPEED, packetBuffer -> packetBuffer.writeLong(energyIOPerSec));
        }
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_IO_SPEED) {
            this.lastEnergyIOPerSec = buf.readLong();
        } else if (dataId == UPDATE_ACTIVE) {
            this.active = buf.readBoolean();
        }
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(active);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.active = buf.readBoolean();
    }

    public static Function<String, String> getTextFieldValidator() {
        return val -> {
            if (val.isEmpty()) {
                return "0";
            }
            long num;
            try {
                num = Long.parseLong(val);
            } catch (NumberFormatException ignored) {
                return "0";
            }
            if (num < 0) {
                return "0";
            }
            return val;
        };
    }

    @Override
    public boolean isWorkingEnabled() {
        return active;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        setActive(isWorkingAllowed);
    }
}
