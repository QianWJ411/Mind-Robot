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
import net.minecraft.util.IChatComponent;
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
 * Pairing is channel based (R22 / D5): a helmet stores a snapshot - a public number, or a private name plus
 * password. A public channel is resolved only among the robots that are loaded right now (R28); a private
 * channel may wake the chunk recorded for it in {@link RobotRegistry} - the world level table the robots
 * report themselves into (R27 / D11) - and retry for a bounded number of ticks. The helmet copy of the
 * position is a tooltip mirror and is never read here. {@link #requestEnter(EntityPlayerMP)} is the single
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

    /** Private connection attempts waiting for a registered chunk to load (R28). */
    private static final Map<UUID, PendingEnter> PENDING_ENTERS = new HashMap<UUID, PendingEnter>();

    /**
     * Requests arrive on the netty thread, the table is only touched on the server thread,
     * so incoming requests are queued and drained at the start of every server tick.
     */
    private static final Queue<Runnable> PENDING_REQUESTS = new ConcurrentLinkedQueue<Runnable>();

    private static final double ANCHOR_EPSILON_SQ = 1.0E-4D;
    private static final float ANCHOR_ROTATION_EPSILON = 0.1F;
    private static final int WAKE_TIMEOUT_TICKS = 60;

    /** A private connection that is being retried while the registered chunk loads back in (R27 / R28). */
    private static final class PendingEnter {

        /** The private helmet credentials; public channels never wait for a wake (R28). */
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
     * True when the robot is linked to a driver, whoever that is. The item container uses this to keep a
     * machine that is being driven sealed while it is under control (R10 / D15); a parked robot stays open.
     */
    public static boolean isRobotDriven(EntityRobot robot) {
        return robot != null && LINKS_BY_ROBOT.containsKey(robot.getUniqueID());
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
     * R28 splits the two retrieval paths:
     * <ul>
     * <li>a public helmet only looks at the robots that are loaded right now; a public channel number never
     * forces a chunk in, so a miss is a plain refusal;</li>
     * <li>a private helmet is allowed to wake its robot: the plain text name and password are looked up in
     * {@link RobotRegistry}, and only a matching pair may force the registered chunk in.</li>
     * </ul>
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
        if (!helmetChannel.isConfigured()) {
            LinkDenial.HELMET_UNCONFIGURED.send(player);
            return;
        }

        if (helmetChannel.isPublic()) {
            requestPublic(player, helmetChannel);
        } else {
            requestPrivate(player, helmetChannel);
        }
    }

    /** R28 public path: loaded robots only; a channel number never forces a chunk in. */
    private static void requestPublic(EntityPlayerMP player, ChannelData helmetChannel) {
        EntityRobot robot = findLoadedRobot(player.worldObj, helmetChannel, player);
        if (robot == null) {
            reportRegisteredButUnloaded(player, helmetChannel);
            LinkDenial.NO_ROBOT.send(player, helmetChannel.getChannel());
            return;
        }
        openLink(player, robot, helmetChannel);
    }

    /**
     * Diagnostic only: when the registry still knows a public robot that is not loaded, that is logged, but
     * nothing is woken (R28). A registry row must never become a forced chunk on a public channel.
     */
    private static void reportRegisteredButUnloaded(EntityPlayerMP player, ChannelData helmetChannel) {
        RobotRegistry registry = RobotRegistry.get(player.worldObj);
        if (registry == null) {
            return;
        }
        RobotRegistry.Entry record = registry.findPublic(helmetChannel.getChannel(), player.dimension);
        if (record != null) {
            MindRobot.LOG.info(
                "Public channel {} has a registered robot at [{},{},{}] but it is not loaded; public channels never wake a chunk (R28)",
                Integer.valueOf(helmetChannel.getChannel()),
                Integer.valueOf(record.getBlockX()),
                Integer.valueOf(record.getBlockY()),
                Integer.valueOf(record.getBlockZ()));
        }
    }

    /** R28 private path: the credentials are the wake authorisation, then a bounded wake retry follows. */
    private static void requestPrivate(EntityPlayerMP player, ChannelData helmetChannel) {
        EntityRobot loaded = findLoadedRobot(player.worldObj, helmetChannel, player);
        if (loaded != null) {
            openLink(player, loaded, helmetChannel);
            return;
        }
        RobotRegistry registry = RobotRegistry.get(player.worldObj);
        RobotRegistry.Entry record = registry == null ? null
            : registry.findPrivate(helmetChannel.getName(), helmetChannel.getPassword(), player.dimension);
        if (record == null) {
            sendPrivateMiss(player, helmetChannel, registry);
            return;
        }
        if (PENDING_ENTERS.containsKey(player.getUniqueID())) {
            return;
        }
        PENDING_ENTERS.put(player.getUniqueID(), new PendingEnter(helmetChannel, record));
        RobotChunkLoader.forceAt(player.getUniqueID(), player.worldObj, record.getChunkX(), record.getChunkZ());
        player.addChatMessage(
            new ChatComponentTranslation(
                "chat.mindrobot.link.waking",
                helmetChannel.getName(),
                Integer.valueOf(record.getBlockX()),
                Integer.valueOf(record.getBlockY()),
                Integer.valueOf(record.getBlockZ())));
    }

    /** Tells a wrong password apart from an unknown private name, without ever echoing the password. */
    private static void sendPrivateMiss(EntityPlayerMP player, ChannelData helmetChannel, RobotRegistry registry) {
        boolean nameKnown = hasLoadedPrivateName(player.worldObj, helmetChannel.getName())
            || (registry != null && registry.hasPrivateName(helmetChannel.getName(), player.dimension));
        if (nameKnown) {
            LinkDenial.PRIVATE_PASSWORD.send(player, helmetChannel.getName());
        } else {
            LinkDenial.NO_ROBOT_PRIVATE.send(player, helmetChannel.getName());
        }
    }

    /** Opens the link once every D5 rule passed; used both by a direct request and by the wake retry. */
    private static void openLink(EntityPlayerMP player, EntityRobot robot, ChannelData helmetChannel) {
        // D5 step 2: both sides must still answer on the same channel (public number, or private credentials).
        ChannelData robotChannel = robot.getChannelData();
        if (robotChannel == null || !robotChannel.isConfigured() || !helmetChannel.matches(robotChannel)) {
            LinkDenial.CHANNEL_MISMATCH.send(player);
            return;
        }
        // D5 steps 3 and 4: a robot takes any same channel driver, but only one at a time (R13/R25).
        if (LINKS_BY_ROBOT.containsKey(robot.getUniqueID())) {
            LinkDenial.sendOccupied(player, robotChannel);
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
            robotChannel,
            link.getAnchorX(),
            link.getAnchorY(),
            link.getAnchorZ());

        player.addChatMessage(connectedMessage(robotChannel));
        ModNetwork.syncLinkState(player);
    }

    /** The connected chat line names the number for a public channel and the name for a private one. */
    private static IChatComponent connectedMessage(ChannelData robotChannel) {
        if (robotChannel.isPrivate()) {
            return new ChatComponentTranslation("chat.mindrobot.link.connected_private", robotChannel.getName());
        }
        return new ChatComponentTranslation("chat.mindrobot.link.connected_public", robotChannel.getChannel());
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
        EntityPlayer linkedPlayer = findPlayer(link.getPlayerId());
        if (linkedPlayer != null) {
            disconnect(linkedPlayer, DisconnectReason.ROBOT_DESTROYED);
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

    /** Retries the requested private channel every tick until the forced chunk loads the robot back in (R28). */
    private static void tickPendingEnter(EntityPlayerMP player) {
        PendingEnter pending = PENDING_ENTERS.get(player.getUniqueID());
        if (pending == null) {
            return;
        }
        EntityRobot robot = findLoadedRobot(player.worldObj, pending.channel, player);
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
            LinkDenial.NO_ROBOT_PRIVATE.send(player, pending.channel.getName());
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
     * Finds the loaded robot that answers on the given channel in this world (R28: the only retrieval path a
     * public channel has). When several robots share a channel (only possible through debug spawning) the
     * nearest one wins, so the choice is deterministic.
     */
    private static EntityRobot findLoadedRobot(World world, ChannelData channel, EntityPlayer player) {
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
            if (robot.isDead || !channel.matches(robot.getChannelData())) {
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

    /** True when a loaded robot already answers on this private name (used only to explain a refusal). */
    private static boolean hasLoadedPrivateName(World world, String name) {
        if (world == null || name == null) {
            return false;
        }
        for (Object candidate : world.loadedEntityList) {
            if (!(candidate instanceof EntityRobot)) {
                continue;
            }
            ChannelData data = ((EntityRobot) candidate).getChannelData();
            if (data != null && data.isPrivate() && name.equals(data.getName())) {
                return true;
            }
        }
        return false;
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
