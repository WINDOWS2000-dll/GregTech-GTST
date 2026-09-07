package gregtech.common.command;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.metatileentity.MTETrait;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.api.statemachine.GTStateMachine;
import gregtech.common.ConfigHolder;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * {@code /gt dumpstatemachine <target|list>}: a "static graph dump" debug command. A dump target is a machine
 * <i>class</i> (its {@link GTStateMachine} graph
 * is identical for every placed instance, including the never-placed "prototype" instance already sitting in the
 * MTE registry, per {@link RecipeWorkable}'s JavaDoc) &mdash; so, unlike the trace item, this needs no in-world
 * block to point at, just a {@link ResourceLocation}.
 * <p>
 * Gated entirely behind {@link ConfigHolder.DevOptions#enableStateMachineDebugTools} (default {@code false}); does
 * nothing but print a message when that's off.
 * <p>
 * Works against any mod's MTE registry, not just GregTech's own (see {@link GregTechAPI#mteManager}), and against any
 * machine on the new {@link RecipeWorkable}-based engine specifically (found via the generic
 * {@link GregtechDataCodes#RECIPE_WORKABLE_TRAIT} trait lookup, not a hardcoded class check) &mdash; an addon that
 * migrates its own machines onto this engine is automatically dumpable/listable, no GregTech-side change needed.
 */
public class CommandDumpStateMachine extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "dumpstatemachine";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "gregtech.command.dumpstatemachine.usage";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        if (!ConfigHolder.dev.enableStateMachineDebugTools) {
            sender.sendMessage(new TextComponentString(
                    "StateMachine debug tools are disabled (gregtech.dev.enableStateMachineDebugTools)."));
            return;
        }
        if (args.length != 1) {
            throw new WrongUsageException(getUsage(sender));
        }

        if (args[0].equalsIgnoreCase("list")) {
            List<ResourceLocation> targets = findDumpableTargets();
            sender.sendMessage(new TextComponentString(targets.size() + " dumpable machine(s):"));
            for (ResourceLocation target : targets) {
                sender.sendMessage(new TextComponentString("  " + target));
            }
            return;
        }

        ResourceLocation id = new ResourceLocation(args[0]);
        RecipeWorkable workable = findWorkable(id);
        if (workable == null) {
            throw new CommandException("gregtech.command.dumpstatemachine.not_found", args[0]);
        }

        String fileName = "gtst_statemachine_dump_" + id.getPath() + '_' +
                new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt";
        File file = new File(new File("logs"), fileName);
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), workable.getStateMachine().dumpGraph().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CommandException("gregtech.command.dumpstatemachine.write_failed", file.getPath());
        }
        sender.sendMessage(new TextComponentString("Dumped " + id + "'s StateMachine graph to " + file.getPath()));
    }

    @NotNull
    @Override
    public List<String> getTabCompletions(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                                          @NotNull String[] args, @Nullable BlockPos targetPos) {
        if (args.length != 1) return Collections.emptyList();
        List<String> options = new ObjectArrayList<>();
        options.add("list");
        for (ResourceLocation target : findDumpableTargets()) {
            options.add(target.toString());
        }
        return getListOfStringsMatchingLastWord(args, options);
    }

    /** @return every registered machine (any mod) whose prototype instance already has a {@link RecipeWorkable}. */
    @NotNull
    private static List<ResourceLocation> findDumpableTargets() {
        List<ResourceLocation> result = new ObjectArrayList<>();
        for (MTERegistry registry : GregTechAPI.mteManager.getRegistries()) {
            for (MetaTileEntity mte : registry) {
                if (mte != null && asRecipeWorkable(mte) != null) {
                    result.add(mte.metaTileEntityId);
                }
            }
        }
        result.sort(Comparator.comparing(ResourceLocation::toString));
        return result;
    }

    @Nullable
    private static RecipeWorkable findWorkable(@NotNull ResourceLocation id) {
        for (MTERegistry registry : GregTechAPI.mteManager.getRegistries()) {
            if (!registry.getModid().equals(id.getNamespace())) continue;
            MetaTileEntity mte = registry.getObject(id);
            if (mte != null) return asRecipeWorkable(mte);
        }
        return null;
    }

    @Nullable
    private static RecipeWorkable asRecipeWorkable(@NotNull MetaTileEntity mte) {
        MTETrait trait = mte.getMTETrait(GregtechDataCodes.RECIPE_WORKABLE_TRAIT);
        return trait instanceof RecipeWorkable ? (RecipeWorkable) trait : null;
    }
}
