package gregtech.common.items.behaviors;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MTETrait;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.common.ConfigHolder;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * The execution-trace dev tool. Right-click a
 * {@link RecipeWorkable}-backed machine (single-block or multiblock, GregTech's own or an addon's, found via the
 * generic {@link GregtechDataCodes#RECIPE_WORKABLE_TRAIT} trait lookup) to toggle tracing for that specific
 * instance; right-click it again to turn it back off. Each instance's trace state is independent, so multiple
 * machines can be traced at once with no separate "selection list" to manage.
 * <p>
 * Gated entirely behind {@link ConfigHolder.DevOptions#enableStateMachineDebugTools}; does nothing (passes through)
 * when that's off, so the item is inert in a normal (non-debugging) install.
 */
public class StateMachineTraceBehavior implements IItemBehaviour {

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!ConfigHolder.dev.enableStateMachineDebugTools) return EnumActionResult.PASS;

        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IGregTechTileEntity)) return EnumActionResult.PASS;
        MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        if (mte == null) return EnumActionResult.PASS;

        MTETrait trait = mte.getMTETrait(GregtechDataCodes.RECIPE_WORKABLE_TRAIT);
        if (!(trait instanceof RecipeWorkable workable)) return EnumActionResult.PASS;

        if (world.isRemote) return EnumActionResult.SUCCESS;

        boolean nowTracing = !workable.isTraceEnabled();
        workable.setTraceEnabled(nowTracing, mte.metaTileEntityId + "@" + pos);
        player.sendMessage(new TextComponentString(nowTracing ?
                "Tracing " + mte.metaTileEntityId + " at " + pos + " (see the GregTech-StateMachine-Trace log)." :
                "Stopped tracing " + mte.metaTileEntityId + " at " + pos + "."));
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add(I18n.format("metaitem.tool.state_machine_trace.tooltip"));
    }
}
