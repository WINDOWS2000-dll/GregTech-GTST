package gregtech.common.metatileentities.steam;

import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

public class SteamMacerator extends SteamMetaTileEntity {

    public SteamMacerator(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, RecipeMaps.MACERATOR_RECIPES, Textures.MACERATOR_OVERLAY, isHighPressure);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamMacerator(metaTileEntityId, isHighPressure);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, 1, this, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new NotifiableItemStackHandler(this, 1, this, true);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        UITexture slotBase = GTGuiTextures.SLOT_STEAM.get(isHighPressure);
        return buildUITemplate(guiData, panelSyncManager)
                .child(new ItemSlot()
                        .pos(53, 25)
                        .background(slotBase, GTGuiTextures.CRUSHED_ORE_OVERLAY_STEAM.get(isHighPressure))
                        .slot(new ModularSlot(this.importItems, 0).accessibility(true, true)))
                .child(workableHandler.getRecipeMap().getRecipeMapUI()
                        .createJeiProgressWidget(workableHandler::getProgressPercent)
                        .pos(79, 26)
                        .size(21, 18)
                        .texture(GTGuiTextures.PROGRESS_BAR_MACERATE_STEAM.get(isHighPressure), 21)
                        .direction(ProgressWidget.Direction.RIGHT))
                .child(new ItemSlot()
                        .pos(107, 25)
                        .background(slotBase, GTGuiTextures.DUST_OVERLAY_STEAM.get(isHighPressure))
                        .slot(new ModularSlot(this.exportItems, 0).accessibility(false, true)));
    }

    @Override
    public int getItemOutputLimit() {
        return 1;
    }

    @Override
    public void update() {
        super.update();
        if (isActive() && getWorld().isRemote) {
            VanillaParticleEffects.TOP_SMOKE_SMALL.runEffect(this);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        // steam macerators do not make particles in this way
    }
}
