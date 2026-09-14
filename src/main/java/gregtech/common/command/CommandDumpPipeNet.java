package gregtech.common.command;

import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.PipeNetTraceTickHandler;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.common.ConfigHolder;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * {@code /gt dumppipenet <list|x> [y] [z]}: a PipeNet dev-tool snapshot command, complementary to the
 * execution-trace item ({@code PipeNetTraceBehavior}). Unlike {@code CommandDumpStateMachine}'s target (a
 * machine <i>class</i>, whose graph is identical for every instance), a PipeNet is inherently a specific
 * in-world runtime object, so this command's target is a position, not a resource ID.
 * <p>
 * Gated entirely behind {@link ConfigHolder.DevOptions#enablePipeNetDebugTools} (default {@code false}).
 */
public class CommandDumpPipeNet extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "dumppipenet";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "gregtech.command.dumppipenet.usage";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        if (!ConfigHolder.dev.enablePipeNetDebugTools) {
            sender.sendMessage(new TextComponentString(
                    "PipeNet debug tools are disabled (gregtech.dev.enablePipeNetDebugTools)."));
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            dumpTracedList(sender);
            return;
        }
        if (args.length != 3) {
            throw new WrongUsageException(getUsage(sender));
        }

        BlockPos pos = parseBlockPos(sender, args, 0, false);
        World world = sender.getEntityWorld();
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IPipeTile<?, ?>pipeTile)) {
            throw new CommandException("gregtech.command.dumppipenet.not_a_pipe", pos.getX(), pos.getY(),
                    pos.getZ());
        }
        WorldPipeNet<?, ?> worldPipeNet = pipeTile.getPipeBlock().getWorldPipeNet(world);
        PipeNet<?> net = worldPipeNet.getNetFromPos(pos);
        if (net == null) {
            throw new CommandException("gregtech.command.dumppipenet.no_net", pos.getX(), pos.getY(), pos.getZ());
        }

        String fileName = "gtst_pipenet_dump_" + pos.getX() + '_' + pos.getY() + '_' + pos.getZ() + '_' +
                new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt";
        File file = new File(new File("logs"), fileName);
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            String body = "PipeNet at " + pos + "\n" +
                    "traceEnabled=" + net.isTraceEnabled() + " traceLabel=" + net.getTraceLabel() + "\n" +
                    net.describeMemoryProxy() + "\n" +
                    "lifetime stats: " + net.getTraceStats().snapshot().describe(0) + "\n";
            Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CommandException("gregtech.command.dumppipenet.write_failed", file.getPath());
        }
        sender.sendMessage(new TextComponentString("Dumped the PipeNet at " + pos + " to " + file.getPath()));
    }

    private void dumpTracedList(@NotNull ICommandSender sender) {
        List<PipeNet<?>> tracedNets = PipeNetTraceTickHandler.getTracedNets();
        sender.sendMessage(new TextComponentString(tracedNets.size() + " currently traced PipeNet(s):"));
        for (PipeNet<?> net : tracedNets) {
            sender.sendMessage(new TextComponentString("  [" + net.getTraceLabel() + "] " + net.describeMemoryProxy()));
        }
    }

    @NotNull
    @Override
    public List<String> getTabCompletions(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                                          @NotNull String[] args, net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list");
        }
        if (args.length >= 1 && args.length <= 3 && targetPos != null) {
            return getTabCompletionCoordinate(args, args.length - 1, targetPos);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(@NotNull String[] args, int index) {
        return false;
    }
}
