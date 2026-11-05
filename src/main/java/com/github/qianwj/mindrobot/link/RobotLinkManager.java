package com.github.qianwj.mindrobot.link;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.item.ItemRemoteHelmet;
import com.github.qianwj.mindrobot.network.MenuOption;
import com.github.qianwj.mindrobot.network.ModNetwork;

/**
 * Server side connection table between players and robots.
 * <p>
 * Pairing is channel based (D5): a helmet only stores a channel snapshot, the robot that answers on that
 * channel is looked up in the world at connect time. {@link #requestEnter(EntityPlayerMP)} is the single
 * server side entry point and walks the D5 validation order, telling the player the exact reason whenever it
 * refuses.
 * <p>
 * {@link #disconnect(EntityPlayer, DisconnectReason)} is the only public way to end a connection (D4): every
 * end path (body death, robot death, menu exit, logout, dimension change, server stop, helmet loss) funnels
 * through it so no ghost link survives, and it always releases the driving chunk ticket (D10).
 */
public final class RobotLinkManager {

    private static final Map<UUID, RobotLink> LINKS_BY_PLAYER = new HashMap<UUID, RobotLink>();
    private static final Map<UUID, RobotLink> LINKS_BY_ROBOT = new HashMap<UUID, RobotLink>();

    /**
     * Requests arrive on the netty thread, the table is only touched on the server thread,
     * so incoming requests are queued and drained at the start of every server tick.
     */
    private static final Queue<Runnable> PENDING_REQUESTS = new ConcurrentLinkedQueue<Runnable>();

    private static final double ANCHOR_EPSILON_SQ = 1.0E-4D;
    private static final float ANCHOR_ROTATION_EPSILON = 0.1F;

    private RobotLinkManager() {}

    public static RobotLink getLink(UUID playerId) {
        return playerId == null ? null : LINKS_BY_PLAYER.get(playerId);
    }

    public static boolean isDriving(EntityPlayer player) {
        return player != null && LINKS_BY_PLAYER.containsKey(player.getUniqueID());
    }

    /**
     * Single public disconnect entry point (D4). Unbinds the link, releases the forced chunk ticket and tells
     * the owning client to give the camera back. The ticket is released even when no link is registered, so a
     * desynchronised table can never leak a ticket.
     */
    public static void disconnect(EntityPlayer player, DisconnectReason reason) {
        if (player == null) {
            return;
        }
        RobotChunkLoader.release(player.getUniqueID());
        RobotLink link = LINKS_BY_PLAYER.remove(player.getUniqueID());
        if (link == null) {
            return;
        }
        LINKS_BY_ROBOT.remove(link.getRobotId());

        MindRobot.LOG.info(
            "Robot link closed: player={} reason={} robot={}",
            player.getCommandSenderName(),
            reason,
            link.getRobotId());

        ModNetwork.syncLinkState(player);
    }

    /** Queues an enter request coming from a client packet (handled on the server thread). */
    public static void queueEnterRequest(final EntityPlayerMP player) {
        PENDING_REQUESTS.add(new Runnable() {

            @Override
            public void run() {
                requestEnter(player);
            }
        });
    }

    /** Queues an exit request coming from a client packet (handled on the server thread). */
    public static void queueExitRequest(final EntityPlayerMP player) {
        PENDING_REQUESTS.add(new Runnable() {

            @Override
            public void run() {
                disconnect(player, DisconnectReason.PLAYER_REQUEST);
            }
        });
    }

    /** Drains queued requests. Must be called from the server thread. */
    public static void processPendingRequests() {
        Runnable request;
        while ((request = PENDING_REQUESTS.poll()) != null) {
            request.run();
        }
    }

    /**
     * Validates and opens a connection. The server decides, the client never does (D6).
     * <p>
     * Validation order is exactly D5: helmet channel present, channel match, private ownership, free robot.
     * Every refusal is reported through {@link LinkDenial}, never silently.
     */
    public static void requestEnter(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        if (isDriving(player)) {
            LinkDenial.ALREADY_DRIVING.send(player);
            return;
        }

        ItemStack helmetStack = player.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT);
        if (helmetStack == null || !(helmetStack.getItem() instanceof ItemRemoteHelmet)) {
            LinkDenial.HELMET_MISSING.send(player);
            return;
        }
        // D5 step 1: a helmet without a chip has nothing to pair with, so there is no connection option.
        ChannelData helmetChannel = ItemRemoteHelmet.getChannelData(helmetStack);
        if (helmetChannel == null) {
            LinkDenial.HELMET_UNPAIRED.send(player);
            return;
        }

        EntityRobot robot = findRobotByChannel(player.worldObj, helmetChannel, player);
        if (robot == null) {
            LinkDenial.NO_ROBOT.send(player, helmetChannel.getChannel());
            return;
        }

        // D5 step 2: channels must be identical.
        ChannelData robotChannel = robot.getChannelData();
        if (robotChannel == null || !helmetChannel.matchesChannel(robotChannel)) {
            LinkDenial.CHANNEL_MISMATCH.send(
                player,
                helmetChannel.getChannel(),
                robotChannel == null ? "?" : Integer.valueOf(robotChannel.getChannel()));
            return;
        }
        // D5 step 3: a private robot only accepts its own owner, even on the same number.
        if (robotChannel.isPrivate() && !robotChannel.isOwnedBy(helmetChannel.getOwner())) {
            LinkDenial.PRIVATE_OWNER.send(player, robotChannel.getChannel());
            return;
        }
        // D5 steps 4 and 5: a public robot takes any same channel driver, but only one at a time (R13).
        if (LINKS_BY_ROBOT.containsKey(robot.getUniqueID())) {
            LinkDenial.ROBOT_OCCUPIED.send(player, robotChannel.getChannel());
            return;
        }

        RobotLink link = new RobotLink(player, robot.getUniqueID());
        LINKS_BY_PLAYER.put(player.getUniqueID(), link);
        LINKS_BY_ROBOT.put(robot.getUniqueID(), link);
        RobotChunkLoader.force(player.getUniqueID(), robot);

        MindRobot.LOG.info(
            "Robot link opened: player={} robot={} channel={} anchor=[{},{},{}]",
            player.getCommandSenderName(),
            robot.getUniqueID(),
            robotChannel.getChannel(),
            link.getAnchorX(),
            link.getAnchorY(),
            link.getAnchorZ());

        player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.link.connected", robotChannel.getChannel()));
        ModNetwork.syncLinkState(player);
    }

    /** Called by {@link EntityRobot#destroyRobot()} so a destroyed robot forces the player out (R8). */
    public static void notifyRobotDestroyed(EntityRobot robot) {
        if (robot == null) {
            return;
        }
        RobotLink link = LINKS_BY_ROBOT.get(robot.getUniqueID());
        if (link == null) {
            return;
        }
        EntityPlayer owner = findPlayer(link.getPlayerId());
        if (owner != null) {
            disconnect(owner, DisconnectReason.ROBOT_DESTROYED);
        } else {
            LINKS_BY_ROBOT.remove(robot.getUniqueID());
            LINKS_BY_PLAYER.remove(link.getPlayerId());
            RobotChunkLoader.release(link.getPlayerId());
        }
    }

    /**
     * Per player server tick: enforces the frozen body rules of D3, follows the robot with the forced chunk
     * ticket (R23) and detects every involuntary disconnect reason.
     */
    public static void tickPlayer(EntityPlayerMP player) {
        RobotLink link = LINKS_BY_PLAYER.get(player.getUniqueID());
        if (link == null) {
            return;
        }

        if (player.getHealth() <= 0.0F) {
            disconnect(player, DisconnectReason.PLAYER_DEATH);
            return;
        }

        ItemStack helmetStack = player.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT);
        if (helmetStack == null || !(helmetStack.getItem() instanceof ItemRemoteHelmet)) {
            disconnect(player, DisconnectReason.HELMET_REMOVED);
            return;
        }

        EntityRobot robot = findRobot(player.worldObj, link.getRobotId());
        if (robot == null || robot.isDead) {
            disconnect(player, DisconnectReason.ROBOT_DESTROYED);
            return;
        }

        // The ticket follows the robot: crossing a chunk border forces the new chunk and releases the old one.
        RobotChunkLoader.tick(player.getUniqueID(), robot);
        pinToAnchor(player, link);
    }

    /** Ends every remaining link, used on server shutdown and as a safety net. */
    public static void disconnectAll(DisconnectReason reason) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            Object[] players = server.getConfigurationManager().playerEntityList.toArray();
            for (Object candidate : players) {
                if (candidate instanceof EntityPlayerMP) {
                    disconnect((EntityPlayerMP) candidate, reason);
                }
            }
        }
        LINKS_BY_PLAYER.clear();
        LINKS_BY_ROBOT.clear();
    }

    /**
     * Server side menu clipping (D6): the options the client is allowed to draw right now. A helmet with no
     * channel gets no connection entry at all (D5 step 1).
     */
    public static MenuOption[] menuOptionsFor(EntityPlayer player) {
        if (player == null) {
            return new MenuOption[0];
        }
        if (isDriving(player)) {
            return new MenuOption[] { MenuOption.EXIT };
        }
        ItemStack helmetStack = player.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT);
        if (helmetStack != null && helmetStack.getItem() instanceof ItemRemoteHelmet
            && ItemRemoteHelmet.hasChannel(helmetStack)) {
            return new MenuOption[] { MenuOption.ENTER_ROBOT };
        }
        return new MenuOption[0];
    }

    /**
     * Entity id of the robot the given player is driving, or -1 when the player is not linked or the
     * robot is not loaded.
     */
    public static int getRobotEntityId(EntityPlayer player) {
        if (player == null) {
            return -1;
        }
        RobotLink link = LINKS_BY_PLAYER.get(player.getUniqueID());
        if (link == null) {
            return -1;
        }
        EntityRobot robot = findRobot(player.worldObj, link.getRobotId());
        return robot == null ? -1 : robot.getEntityId();
    }

    /**
     * Keeps the body on the entry point. World damage, hunger and natural regeneration are untouched
     * (R6), only the position and view direction are forced back (D3).
     */
    private static void pinToAnchor(EntityPlayerMP player, RobotLink link) {
        if (player.playerNetServerHandler == null) {
            return;
        }
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        player.setSprinting(false);

        double deltaX = player.posX - link.getAnchorX();
        double deltaY = player.posY - link.getAnchorY();
        double deltaZ = player.posZ - link.getAnchorZ();
        boolean moved = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > ANCHOR_EPSILON_SQ;
        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(player.rotationYaw - link.getAnchorYaw()));
        float pitchDelta = Math.abs(player.rotationPitch - link.getAnchorPitch());
        boolean turned = yawDelta > ANCHOR_ROTATION_EPSILON || pitchDelta > ANCHOR_ROTATION_EPSILON;

        if (moved || turned) {
            player.rotationYaw = link.getAnchorYaw();
            player.rotationPitch = link.getAnchorPitch();
            player.setPositionAndUpdate(link.getAnchorX(), link.getAnchorY(), link.getAnchorZ());
        }
    }

    /**
     * Finds the robot that answers on the given channel in this world. When several robots share a channel
     * (only possible through debug spawning) the nearest one wins, so the choice is deterministic.
     */
    private static EntityRobot findRobotByChannel(World world, ChannelData channel, EntityPlayer player) {
        if (world == null || channel == null) {
            return null;
        }
        EntityRobot nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Object candidate : world.loadedEntityList) {
            if (!(candidate instanceof EntityRobot)) {
                continue;
            }
            EntityRobot robot = (EntityRobot) candidate;
            if (robot.isDead || !channel.matchesChannel(robot.getChannelData())) {
                continue;
            }
            double distance = player == null ? 0.0D : robot.getDistanceSqToEntity(player);
            if (nearest == null || distance < nearestDistance) {
                nearest = robot;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static EntityRobot findRobot(World world, UUID robotId) {
        if (world == null || robotId == null) {
            return null;
        }
        for (Object candidate : world.loadedEntityList) {
            if (candidate instanceof EntityRobot) {
                EntityRobot robot = (EntityRobot) candidate;
                if (!robot.isDead && robotId.equals(robot.getUniqueID())) {
                    return robot;
                }
            }
        }
        return null;
    }

    private static EntityPlayer findPlayer(UUID playerId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || playerId == null) {
            return null;
        }
        Object[] players = server.getConfigurationManager().playerEntityList.toArray();
        for (Object candidate : players) {
            if (candidate instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) candidate;
                if (playerId.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }
}
