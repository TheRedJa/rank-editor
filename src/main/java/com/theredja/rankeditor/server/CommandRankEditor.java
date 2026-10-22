package com.theredja.rankeditor.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** {@code /rankeditor}: opens the editor. Access is decided by {@link RankEditorPermissions#VIEW}, not OP level. */
public class CommandRankEditor extends CommandBase {

    @Override
    public String getName() {
        return "rankeditor";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rankeditor";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender instanceof EntityPlayerMP p && EditorService.canView(p);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EditorService.open(getCommandSenderAsPlayer(sender), true);
    }
}
