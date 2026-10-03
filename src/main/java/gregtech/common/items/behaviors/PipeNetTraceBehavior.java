package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.pipenet.tile.IPipeTile;
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

public class PipeNetTraceBehavior implements IItemBehaviour {

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!ConfigHolder.dev.enablePipeNetDebugTools) return EnumActionResult.PASS;

        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IPipeTile<?, ?>pipeTile)) return EnumActionResult.PASS;

        if (world.isRemote) return EnumActionResult.SUCCESS;

        WorldPipeNet<?, ?> worldPipeNet = pipeTile.getPipeBlock().getWorldPipeNet(world);
        PipeNet<?> net = worldPipeNet.getNetFromPos(pos);
        if (net == null) return EnumActionResult.PASS;

        boolean nowTracing = !net.isTraceEnabled();
        net.setTraceEnabled(nowTracing, pos.toString());
        player.sendMessage(new TextComponentString(nowTracing ?
                "Tracing the PipeNet at " + pos + " (see the GregTech-PipeNet-Trace log)." :
                "Stopped tracing the PipeNet at " + pos + "."));
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add(I18n.format("metaitem.tool.pipe_net_tracer.tooltip"));
    }
}
