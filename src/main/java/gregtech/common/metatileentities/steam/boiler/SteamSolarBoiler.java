package gregtech.common.metatileentities.steam.boiler;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;

public class SteamSolarBoiler extends SteamBoiler {

    public SteamSolarBoiler(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, isHighPressure, Textures.SOLAR_BOILER_OVERLAY);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamSolarBoiler(metaTileEntityId, isHighPressure);
    }

    @Override
    protected int getBaseSteamOutput() {
        return isHighPressure ? 360 : 120;
    }

    @Override
    protected void tryConsumeNewFuel() {
        if (GTUtility.canSeeSunClearly(getWorld(), getPos())) {
            setFuelMaxBurnTime(20);
        }
    }

    @Override
    protected int getCooldownInterval() {
        return isHighPressure ? 50 : 45;
    }

    @Override
    protected int getCoolDownRate() {
        return 3;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        ModularPanel panel = buildUITemplate(guiData, panelSyncManager);
        panel.child(new ProgressWidget()
                .pos(114, 44)
                .size(20, 20)
                .value(new DoubleSyncValue(() -> GTUtility.canSeeSunClearly(getWorld(), getPos()) ? 1.0 : 0.0))
                .texture(isHighPressure ? GTGuiTextures.PROGRESS_BAR_SOLAR_STEAM_STEEL :
                        GTGuiTextures.PROGRESS_BAR_SOLAR_STEAM_BRONZE, 20)
                .direction(ProgressWidget.Direction.RIGHT));
        return panel;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        // Solar boilers do not display particles
    }
}
