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
 * channel is looked up in the world at connect time. When no robot is loaded, {@link RobotRegistry} - the
 * world level table the robots report themselves into (R27 / D11) - provides the chunk to wake; the helmet
 * copy of the position is a tooltip mirror and is never read here. {@link #requestEnter(EntityPlayerMP)} is
 * the single server side entry point and walks the D5 validation order, telling the player the exact reason
 * whenever it refuses.
 * <p>
 * {@link #disconnect(EntityPlayer, DisconnectReason)} is the only public way to end a connection (D4): every
 * end path (body death, robot death, menu exit, logout, dimension change, server stop, helmet loss) funnels
 * through it so no ghost link survives, and it always releases the driving chunk ticket (D10).
 */
public final class RobotLinkManager {

    private static final Map<UUID, RobotLink> LINKS_BY_PLAYER = new HashMap<UUID, RobotLink>();
    private static final Map<UUID, RobotLink> LINKS_BY_ROBOT = new HashMap<UUID, RobotLink>();

    /** Connection attempts waiting for a registered chunk to load (R27). */
    private static final Map<UUID, PendingEnter> PENDING_ENTERS = new HashMap<UUID, PendingEnter>();

    /**
     * Requests arrive on the netty thread, the table is only touched on the server thread,
     * so incoming requests are queued and drained at the start of every server tick.
     */
    private static final Queue<Runnable> PENDING_REQUESTS = new ConcurrentLinkedQueue<Runnable>();

    private static final double ANCHOR_EPSILON_SQ = 1.0E-4D;
    private static final float ANCHOR_ROTATION_EPSILON = 0.1F;
    private static final int WAKE_TIMEOUT_TICKS = 60;

    /** A connection that is being retried while the registered chunk loads back in (R27). */
    private static final class PendingEnter {

        private final ChannelData channel;
        private final int chunkX;
        private final int chunkZ;
        private int ticksLeft = WAKE_TIMEOUT_TICKS;

        private PendingEnter(ChannelData channel, RobotRegistry.Entry record) {
            this.channel = channel;
            this.chunkX = record.getChunkX();
            this.chunkZ = record.getChunkZ();
        }
    }

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
        PENDING_ENTERS.remove(player.getUniqueID());
        RobotLink existing = LINKS_BY_PLAYER.get(player.getUniqueID());
        if (existing != null) {
            rememberLastPosition(player, existing);
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

    /**
     * R27: a finished link refreshes two things - the registry row of the robot (it reports once more while it
     * is still loaded) and the display mirror inside the helmet the driver wore. The helmet value is never
     * consulted to find a robot again; the registry is the only wake-up source (D11).
     */
    private static void rememberLastPosition(EntityPlayer player, RobotLink link) {
        RobotRegistry registry = RobotRegistry.get(player.worldObj);
        EntityRobot robot = findRobot(player.worldObj, link.getRobotId());
        LastKnownPosition position;
        if (robot != null) {
            if (registry != null) {
                registry.report(robot);
            }
            position = new LastKnownPosition(robot.posX, robot.posY, robot.posZ, robot.worldObj.provider.dimensionId);
        } else {
            RobotRegistry.Entry record = registry == null ? null : registry.get(link.getRobotId());
            position = record != null ? record.asPosition()
                : new LastKnownPosition(link.getRobotX(), link.getRobotY(), link.getRobotZ(), link.getDimensionId());
        }
        ItemStack helmet = player.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT);
        if (helmet != null && helmet.getItem() instanceof ItemRemoteHelmet) {
            ItemRemoteHelmet.setLastKnownPosition(helmet, position);
        }
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
            if (scheduleWake(player, helmetChannel)) {
                return;
            }
            LinkDenial.NO_ROBOT.send(player, helmetChannel.getChannel());
            return;
        }
        openLink(player, robot, helmetChannel);
    }

    /**
     * R27: no robot is loaded, so the world level registry decides. When it holds a row for the helmet
     * channel in this dimension, the chunk of that row is forced back in and the request is retried for a
     * bounded number of ticks instead of being refused outright. The helmet copy of the position is not
     * consulted, it is a display mirror only.
     */
    private static boolean scheduleWake(EntityPlayerMP player, ChannelData helmetChannel) {
        if (PENDING_ENTERS.containsKey(player.getUniqueID())) {
            return true;
        }
        RobotRegistry registry = RobotRegistry.get(player.worldObj);
        if (registry == null) {
            return false;
        }
        RobotRegistry.Entry record = registry
            .findMostRecent(helmetChannel.getChannel(), player.dimension, player.posX, player.posZ);
        if (record == null) {
            return false;
        }
        // A private channel owned by somebody else is refused before a single chunk is forced for it.
        ChannelData recordChannel = record.getChannelData();
        if (recordChannel != null && recordChannel.isPrivate() && !recordChannel.isOwnedBy(helmetChannel.getOwner())) {
            LinkDenial.PRIVATE_OWNER.send(player, helmetChannel.getChannel());
            return true;
        }
        PENDING_ENTERS.put(player.getUniqueID(), new PendingEnter(helmetChannel, record));
        RobotChunkLoader.forceAt(player.getUniqueID(), player.worldObj, record.getChunkX(), record.getChunkZ());
        player.addChatMessage(
            new ChatComponentTranslation(
                "chat.mindrobot.link.waking",
                record.getBlockX(),
                record.getBlockY(),
                record.getBlockZ()));
        return true;
    }

    /** Opens the link once every D5 rule passed; used both by a direct request and by the wake retry. */
    private static void openLink(EntityPlayerMP player, EntityRobot robot, ChannelData helmetChannel) {
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

        RobotLink link = new RobotLink(player, robot.getUniqueID(), robot.posX, robot.posY, robot.posZ);
        LINKS_BY_PLAYER.put(player.getUniqueID(), link);
        LINKS_BY_ROBOT.put(robot.getUniqueID(), link);
        RobotChunkLoader.force(player.getUniqueID(), robot);
        rememberIntoHelmet(player, robot);

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

    /** Copies the current robot position into the worn helmet; tooltip only, never a wake-up source (D11). */
    private static void rememberIntoHelmet(EntityPlayer player, EntityRobot robot) {
        ItemStack helmet = player.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT);
        if (helmet == null || !(helmet.getItem() instanceof ItemRemoteHelmet)) {
            return;
        }
        ItemRemoteHelmet.setLastKnownPosition(
            helmet,
            new LastKnownPosition(robot.posX, robot.posY, robot.posZ, robot.worldObj.provider.dimensionId));
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
        if (player == null) {
            return;
        }
        tickPendingEnter(player);
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

        // Cross dimension control is not supported yet: the client cannot follow a robot of another world.
        if (player.dimension != link.getDimensionId()) {
            disconnect(player, DisconnectReason.DIMENSION_CHANGE);
            return;
        }

        EntityRobot robot = findRobot(player.worldObj, link.getRobotId());
        if (robot == null) {
            // An unloaded chunk is not a destroyed robot (R24): pull the last known chunk back and keep the
            // link open. The drive resumes as soon as the entity is loaded again.
            RobotChunkLoader
                .forceAt(player.getUniqueID(), player.worldObj, link.getRobotChunkX(), link.getRobotChunkZ());
            pinToAnchor(player, link);
            return;
        }
        if (robot.isDead) {
            disconnect(player, DisconnectReason.ROBOT_DESTROYED);
            return;
        }
        link.updateRobotPosition(robot.posX, robot.posY, robot.posZ);

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

    /** Retries the requested channel every tick until the forced chunk loads the robot back in (R27). */
    private static void tickPendingEnter(EntityPlayerMP player) {
        PendingEnter pending = PENDING_ENTERS.get(player.getUniqueID());
        if (pending == null) {
            return;
        }
        EntityRobot robot = findRobotByChannel(player.worldObj, pending.channel, player);
        if (robot != null) {
            PENDING_ENTERS.remove(player.getUniqueID());
            openLink(player, robot, pending.channel);
            return;
        }
        if (--pending.ticksLeft <= 0) {
            PENDING_ENTERS.remove(player.getUniqueID());
            // The wake failed: when the chunk is loaded the row is stale (the robot was removed outside
            // destroyRobot, for instance with /kill), so it must not trigger another futile wake later.
            RobotRegistry registry = RobotRegistry.get(player.worldObj);
            if (registry != null) {
                registry.pruneStale(player.worldObj);
            }
            LinkDenial.NO_ROBOT.send(player, pending.channel.getChannel());
            return;
        }
        RobotChunkLoader.forceAt(player.getUniqueID(), player.worldObj, pending.chunkX, pending.chunkZ);
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
