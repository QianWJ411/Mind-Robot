package com.github.qianwj.mindrobot.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.item.ItemFrequencyChip;
import com.github.qianwj.mindrobot.link.ChannelData;
import com.github.qianwj.mindrobot.link.ChannelMode;

/**
 * {@code /mindrobot} debug and setup command (permission level 2).
 * <ul>
 * <li>{@code /mindrobot chip <channel> [private|public]} - configures the frequency chip in the main hand
 * without opening a GUI, which keeps headless testing and server side setup possible;</li>
 * <li>{@code /mindrobot spawn <channel> [private|public]} - spawns a robot carrying that channel two blocks
 * in front of the player. This replaces the P0 bridge that silently materialised a robot on first connect;
 * there is now an explicit command for it until the assembly table of P3 exists.</li>
 * </ul>
 */
public class CommandMindRobot extends CommandBase {

    public static final String COMMAND_NAME = "mindrobot";
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    private static final String SUBCOMMAND_CHIP = "chip";
    private static final String SUBCOMMAND_SPAWN = "spawn";
    private static final String MODE_PRIVATE = "private";
    private static final String MODE_PUBLIC = "public";

    private static final int SPAWN_DISTANCE = 2;
    private static final int SPAWN_HEADROOM = 1;

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.mindrobot.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return REQUIRED_PERMISSION_LEVEL;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new CommandException("commands.mindrobot.usage");
        }
        String subcommand = args[0].toLowerCase();
        if (SUBCOMMAND_CHIP.equals(subcommand)) {
            configureChip(sender, args);
        } else if (SUBCOMMAND_SPAWN.equals(subcommand)) {
            spawnRobot(sender, args);
        } else {
            throw new CommandException("commands.mindrobot.usage");
        }
    }

    /** {@code /mindrobot chip <channel> [private|public]}: writes the channel into the main hand chip. */
    private void configureChip(ICommandSender sender, String[] args) {
        EntityPlayerMP player = requirePlayer(sender);
        if (args.length < 2) {
            throw new CommandException("commands.mindrobot.usage");
        }
        int channel = parseIntBounded(sender, args[1], ChannelData.MIN_CHANNEL, ChannelData.MAX_CHANNEL);
        ChannelMode mode = args.length >= 3 ? parseMode(args[2]) : ChannelMode.PRIVATE;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemFrequencyChip)) {
            throw new CommandException("commands.mindrobot.chip.no_chip");
        }
        // Same server side write path as the GUI packet, so the owner is always the executing player.
        ItemFrequencyChip.applyConfiguration(player, channel, mode);
    }

    /** {@code /mindrobot spawn <channel> [private|public]}: debug spawner until the P3 assembly table. */
    private void spawnRobot(ICommandSender sender, String[] args) {
        EntityPlayerMP player = requirePlayer(sender);
        if (args.length < 2) {
            throw new CommandException("commands.mindrobot.usage");
        }
        int channel = parseIntBounded(sender, args[1], ChannelData.MIN_CHANNEL, ChannelData.MAX_CHANNEL);
        ChannelMode mode = args.length >= 3 ? parseMode(args[2]) : ChannelMode.PRIVATE;
        ChannelData channelData = new ChannelData(channel, mode, player.getUniqueID());

        World world = player.worldObj;
        double yaw = Math.toRadians(player.rotationYaw);
        double spawnX = player.posX - Math.sin(yaw) * SPAWN_DISTANCE;
        double spawnZ = player.posZ + Math.cos(yaw) * SPAWN_DISTANCE;
        if (!hasRoom(world, spawnX, player.posY, spawnZ)) {
            spawnX = player.posX;
            spawnZ = player.posZ;
        }

        EntityRobot robot = new EntityRobot(world);
        robot.setChannelData(channelData);
        robot.setLocationAndAngles(spawnX, player.posY, spawnZ, player.rotationYaw, 0.0F);
        if (!world.spawnEntityInWorld(robot)) {
            throw new CommandException("commands.mindrobot.spawn.failed");
        }
        sender.addChatMessage(
            new ChatComponentTranslation("commands.mindrobot.spawn.success", channel, modeTranslation(mode)));
    }

    private static EntityPlayerMP requirePlayer(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return (EntityPlayerMP) sender;
        }
        throw new CommandException("commands.mindrobot.player_only");
    }

    private static ChannelMode parseMode(String value) {
        if (MODE_PUBLIC.equalsIgnoreCase(value)) {
            return ChannelMode.PUBLIC;
        }
        if (MODE_PRIVATE.equalsIgnoreCase(value)) {
            return ChannelMode.PRIVATE;
        }
        throw new CommandException("commands.mindrobot.mode.invalid", value);
    }

    private static boolean hasRoom(World world, double x, double y, double z) {
        int blockX = MathHelper.floor_double(x);
        int blockY = MathHelper.floor_double(y);
        int blockZ = MathHelper.floor_double(z);
        return world.isAirBlock(blockX, blockY, blockZ) && world.isAirBlock(blockX, blockY + SPAWN_HEADROOM, blockZ);
    }

    private static IChatComponent modeTranslation(ChannelMode mode) {
        return new ChatComponentTranslation(
            mode == ChannelMode.PUBLIC ? "tooltip.mindrobot.mode.public" : "tooltip.mindrobot.mode.private");
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMAND_CHIP, SUBCOMMAND_SPAWN);
        }
        if (args.length == 3) {
            return getListOfStringsMatchingLastWord(args, MODE_PRIVATE, MODE_PUBLIC);
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
}
