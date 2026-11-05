package com.github.qianwj.mindrobot.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.item.ItemFrequencyChip;
import com.github.qianwj.mindrobot.link.ChannelData;
import com.github.qianwj.mindrobot.link.ChannelMode;

/**
 * {@code /mindrobot} debug and setup command (permission level 2).
 * <ul>
 * <li>{@code /mindrobot chip <number>} - makes the chip in the main hand a public channel without opening a
 * GUI, which keeps headless testing and server side setup possible;</li>
 * <li>{@code /mindrobot chip private <name> <password>} - makes it a private channel (plain text password,
 * see {@link ChannelData});</li>
 * <li>{@code /mindrobot spawn <number>} - spawns a public robot on that number two blocks in front of the
 * player;</li>
 * <li>{@code /mindrobot spawn private <name> <password>} - spawns a private robot carrying those credentials.
 * This replaces the P0 bridge that silently materialised a robot on first connect; there is now an explicit
 * command for it until the assembly table of P3 exists.</li>
 * </ul>
 */
public class CommandMindRobot extends CommandBase {

    public static final String COMMAND_NAME = "mindrobot";
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    private static final String SUBCOMMAND_CHIP = "chip";
    private static final String SUBCOMMAND_SPAWN = "spawn";
    private static final String MODE_PRIVATE = "private";

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

    /** {@code /mindrobot chip <number>} or {@code /mindrobot chip private <name> <password>}. */
    private void configureChip(ICommandSender sender, String[] args) {
        EntityPlayerMP player = requirePlayer(sender);
        if (args.length < 2) {
            throw new CommandException("commands.mindrobot.usage");
        }
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemFrequencyChip)) {
            throw new CommandException("commands.mindrobot.chip.no_chip");
        }
        if (isPrivateForm(args)) {
            requireArguments(sender, args, 4);
            String name = args[2];
            String password = args[3];
            requireValidPrivate(name, password);
            // Same server side write path as the GUI packet.
            ItemFrequencyChip.applyConfiguration(player, ChannelMode.PRIVATE, -1, name, password);
            return;
        }
        if (args.length != 2) {
            throw new CommandException("commands.mindrobot.usage");
        }
        int channel = parseIntBounded(sender, args[1], ChannelData.MIN_CHANNEL, ChannelData.MAX_CHANNEL);
        ItemFrequencyChip.applyConfiguration(player, ChannelMode.PUBLIC, channel, null, null);
    }

    /** {@code /mindrobot spawn <number>} or {@code /mindrobot spawn private <name> <password>}. */
    private void spawnRobot(ICommandSender sender, String[] args) {
        EntityPlayerMP player = requirePlayer(sender);
        if (args.length < 2) {
            throw new CommandException("commands.mindrobot.usage");
        }
        ChannelData channelData;
        if (isPrivateForm(args)) {
            requireArguments(sender, args, 4);
            String name = args[2];
            String password = args[3];
            channelData = ChannelData.privateChannel(name, password);
            if (channelData == null) {
                throw new CommandException("commands.mindrobot.chip.invalid_private");
            }
        } else {
            if (args.length != 2) {
                throw new CommandException("commands.mindrobot.usage");
            }
            int channel = parseIntBounded(sender, args[1], ChannelData.MIN_CHANNEL, ChannelData.MAX_CHANNEL);
            channelData = ChannelData.publicChannel(channel);
        }

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
        sender.addChatMessage(spawnMessage(channelData));
    }

    /** {@code private} is the only keyword form; everything else is the numeric public form. */
    private static boolean isPrivateForm(String[] args) {
        return args.length >= 2 && MODE_PRIVATE.equalsIgnoreCase(args[1]);
    }

    private static void requireArguments(ICommandSender sender, String[] args, int required) {
        if (args.length < required) {
            throw new CommandException("commands.mindrobot.usage");
        }
    }

    /** Rejects a blank or over-long private name / password before anything is written (R26 / D5). */
    private static void requireValidPrivate(String name, String password) {
        String trimmedName = name == null ? "" : name.trim();
        if (!ChannelData.isValidName(trimmedName) || !ChannelData.isValidPassword(password)) {
            throw new CommandException("commands.mindrobot.chip.invalid_private");
        }
    }

    private static EntityPlayerMP requirePlayer(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return (EntityPlayerMP) sender;
        }
        throw new CommandException("commands.mindrobot.player_only");
    }

    private static boolean hasRoom(World world, double x, double y, double z) {
        int blockX = MathHelper.floor_double(x);
        int blockY = MathHelper.floor_double(y);
        int blockZ = MathHelper.floor_double(z);
        return world.isAirBlock(blockX, blockY, blockZ) && world.isAirBlock(blockX, blockY + SPAWN_HEADROOM, blockZ);
    }

    /** Plain chat line: the number for a public channel, the name for a private one. */
    private static ChatComponentTranslation spawnMessage(ChannelData channelData) {
        if (channelData.isPrivate()) {
            return new ChatComponentTranslation("commands.mindrobot.spawn.success_private", channelData.getName());
        }
        return new ChatComponentTranslation("commands.mindrobot.spawn.success_public", channelData.getChannel());
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMAND_CHIP, SUBCOMMAND_SPAWN);
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, MODE_PRIVATE);
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
}
