package gregtech.integration.hwyla.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IWorkable;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityResearchStation;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WorkableDataProvider extends CapabilityDataProvider<IWorkable> {

    public static final WorkableDataProvider INSTANCE = new WorkableDataProvider();

    @Override
    public void register(@NotNull IWailaRegistrar registrar) {
        registrar.registerBodyProvider(this, TileEntity.class);
        registrar.registerNBTProvider(this, TileEntity.class);
        registrar.addConfig(GTValues.MOD_NAME, "gregtech.workable");
    }

    @Override
    protected @NotNull Capability<IWorkable> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_WORKABLE;
    }

    /**
     * Overrides the framework-level method (not just {@link #getNBTData(IWorkable, NBTTagCompound)}) so
     * {@code ShowAsComputation} can be set by MTE type. Legacy checked
     * {@code capability instanceof ComputationRecipeLogic} --
     * Research Station is this provider's only "show as total computation" consumer and is StateMachine-based
     * ({@code RecipeWorkable}, which carries no per-instance flag equivalent to that legacy subclass).
     */
    @Override
    public @NotNull NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world,
                                              BlockPos pos) {
        tag = super.getNBTData(player, te, tag, world, pos);
        if (te instanceof IGregTechTileEntity gtte &&
                gtte.getMetaTileEntity() instanceof MetaTileEntityResearchStation) {
            tag.getCompoundTag("gregtech.IWorkable").setBoolean("ShowAsComputation", true);
        }
        return tag;
    }

    @Override
    protected NBTTagCompound getNBTData(IWorkable capability, NBTTagCompound tag) {
        NBTTagCompound subTag = new NBTTagCompound();
        subTag.setBoolean("Active", capability.isActive());
        if (capability.isActive()) {
            subTag.setInteger("Progress", capability.getProgress());
            subTag.setInteger("MaxProgress", capability.getMaxProgress());
        }
        tag.setTag("gregtech.IWorkable", subTag);
        return tag;
    }

    @NotNull
    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> tooltip, IWailaDataAccessor accessor,
                                     IWailaConfigHandler config) {
        if (!config.getConfig("gregtech.workable") || accessor.getTileEntity() == null) {
            return tooltip;
        }

        if (accessor.getNBTData().hasKey("gregtech.IWorkable")) {
            NBTTagCompound tag = accessor.getNBTData().getCompoundTag("gregtech.IWorkable");
            boolean active = tag.getBoolean("Active");
            if (active) {
                int progress = tag.getInteger("Progress");
                int maxProgress = tag.getInteger("MaxProgress");

                if (tag.getBoolean("ShowAsComputation")) {
                    tooltip.add(I18n.format("gregtech.waila.progress_computation", progress, maxProgress));
                }

                if (maxProgress == 0) {
                    tooltip.add(I18n.format("gregtech.waila.progress_idle"));
                } else if (maxProgress < 20) {
                    tooltip.add(I18n.format("gregtech.waila.progress_tick", progress, maxProgress));
                } else {
                    progress = Math.round(progress / 20.0F);
                    maxProgress = Math.round(maxProgress / 20.0F);
                    tooltip.add(I18n.format("gregtech.waila.progress_sec", progress, maxProgress));
                }
            }
        }

        return super.getWailaBody(itemStack, tooltip, accessor, config);
    }
}
