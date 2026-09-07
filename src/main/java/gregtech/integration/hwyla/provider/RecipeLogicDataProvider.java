package gregtech.integration.hwyla.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IRecipeLogicInfoProvider;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.RecipeWorkablePrimitiveMultiblockController;
import gregtech.api.metatileentity.multiblock.RecipeWorkableSteamMultiblockController;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.multi.MetaTileEntityLargeBoiler;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Generalized from {@code CapabilityDataProvider<AbstractRecipeLogic>}
 * to {@link IRecipeLogicInfoProvider} -- see that interface's own JavaDoc for why (hover info would otherwise be
 * missing for a StateMachine-migrated machine).
 */
public class RecipeLogicDataProvider extends CapabilityDataProvider<IRecipeLogicInfoProvider> {

    public static final RecipeLogicDataProvider INSTANCE = new RecipeLogicDataProvider();

    @Override
    public void register(@NotNull IWailaRegistrar registrar) {
        registrar.registerBodyProvider(this, TileEntity.class);
        registrar.registerNBTProvider(this, TileEntity.class);
        registrar.addConfig(GTValues.MOD_NAME, "gregtech.recipe_logic");
    }

    @Override
    protected @NotNull Capability<IRecipeLogicInfoProvider> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_RECIPE_LOGIC;
    }

    /**
     * Overrides the framework-level method (not just {@link #getNBTData(IRecipeLogicInfoProvider, NBTTagCompound)})
     * so a "free power" primitive machine (Primitive Blast Furnace/Coke Oven,
     * {@link RecipeWorkablePrimitiveMultiblockController}) can be excluded by MTE type before any capability lookup
     * happens -- legacy's equivalent {@code capability instanceof PrimitiveRecipeLogic} guard (checked directly on
     * the capability, since {@code AbstractRecipeLogic}'s own subclass hierarchy had one) has no equivalent here:
     * the new engine's shared, per-machine-agnostic
     * {@code RecipeWorkable} trait never had an equivalent instance flag, hence checking by MTE type instead.
     */
    @Override
    public @NotNull NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world,
                                              BlockPos pos) {
        if (te instanceof IGregTechTileEntity gtte &&
                gtte.getMetaTileEntity() instanceof RecipeWorkablePrimitiveMultiblockController) {
            return tag;
        }
        return super.getNBTData(player, te, tag, world, pos);
    }

    @Override
    protected NBTTagCompound getNBTData(IRecipeLogicInfoProvider capability, NBTTagCompound tag) {
        NBTTagCompound subTag = new NBTTagCompound();
        subTag.setBoolean("Working", capability.isWorking());
        if (capability.isWorking()) {
            subTag.setLong("RecipeEUt", capability.getInfoProviderEUt());
        }
        tag.setTag("gregtech.AbstractRecipeLogic", subTag);
        return tag;
    }

    @NotNull
    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> tooltip, IWailaDataAccessor accessor,
                                     IWailaConfigHandler config) {
        if (!config.getConfig("gregtech.recipe_logic") || accessor.getTileEntity() == null) {
            return tooltip;
        }

        if (accessor.getNBTData().hasKey("gregtech.AbstractRecipeLogic")) {
            NBTTagCompound tag = accessor.getNBTData().getCompoundTag("gregtech.AbstractRecipeLogic");
            if (tag.getBoolean("Working")) {
                long eut = tag.getLong("RecipeEUt");
                boolean consumer = false;
                String endText = null;

                if (accessor.getTileEntity() instanceof IGregTechTileEntity gtte) {
                    MetaTileEntity mte = gtte.getMetaTileEntity();
                    if (mte instanceof SteamMetaTileEntity || mte instanceof MetaTileEntityLargeBoiler ||
                            mte instanceof RecipeWorkableSteamMultiblockController) {
                        endText = ": " + TextFormattingUtil.formatNumbers(eut) + TextFormatting.RESET + " L/t " +
                                I18n.format(Materials.Steam.getUnlocalizedName());
                    }
                    // Reads through the same generalized capability as getNBTData() above (not
                    // MetaTileEntity#getRecipeLogic(), which only ever finds a legacy AbstractRecipeLogic trait --
                    // that would silently miss every StateMachine-migrated machine here too, specific to HWYLA's
                    // own client-side re-derivation of "consumer vs. generator").
                    IRecipeLogicInfoProvider logic = mte.getCapability(GregtechTileCapabilities.CAPABILITY_RECIPE_LOGIC,
                            null);
                    if (logic != null) {
                        consumer = logic.consumesEnergy();
                    }
                }
                if (endText == null) {
                    endText = ": " + TextFormattingUtil.formatNumbers(eut) + TextFormatting.RESET + " EU/t (" +
                            GTValues.VOCNF[GTUtility.getOCTierByVoltage(eut)] + TextFormatting.RESET + ")";
                }

                if (eut == 0) return tooltip;

                if (consumer) {
                    tooltip.add(I18n.format("gregtech.top.energy_consumption") + endText);
                } else {
                    tooltip.add(I18n.format("gregtech.top.energy_production") + endText);
                }
            }
        }
        return tooltip;
    }
}
