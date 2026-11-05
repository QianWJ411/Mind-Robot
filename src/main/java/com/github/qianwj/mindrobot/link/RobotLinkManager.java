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
 * {@link #disconnect(EntityPlayer, DisconnectReason)} is the only public way to end a connection
 * (design decision D4): every end path (body death, robot death, menu exit, logout, dimension change,
 * server stop, helmet loss) funnels through it so no ghost link can survive.
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
     * Single public disconnect entry point (D4). Unbinds the link, drops the anchor and tells the
     * owning client to give the camera back.
     */
    public static void disconnect(EntityPlayer player, DisconnectReason reason) {
        if (player == null) {
            return;
        }
        RobotLink link = LINKS_BY_PLAYER.remove(player.getUniqueID());
        if (link == null) {
            return;
        }
        LINKS_BY_ROBOT.remove(link.getRobotId());

        EntityRobot robot = findRobot(player.worldObj, link.getRobotId());
        if (robot != null) {
            robot.setOwnerId(null);
        }

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
     */
    public static void requestEnter(EntityPlayerMP player) {
        if (player == null || isDriving(player)) {
            return;
        }
        ItemStack helmetStack = player.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT);
        if (helmetStack == null || !(helmetStack.getItem() instanceof ItemRemoteHelmet)) {
            return;
        }

        EntityRobot robot = resolveRobot(player, helmetStack);
        if (robot == null || LINKS_BY_ROBOT.containsKey(robot.getUniqueID())) {
            return;
        }

        RobotLink link = new RobotLink(player, robot.getUniqueID());
        LINKS_BY_PLAYER.put(player.getUniqueID(), link);
        LINKS_BY_ROBOT.put(robot.getUniqueID(), link);
        robot.setOwnerId(player.getUniqueID());

        MindRobot.LOG.info(
            "Robot link opened: player={} robot={} anchor=[{},{},{}]",
            player.getCommandSenderName(),
            robot.getUniqueID(),
            link.getAnchorX(),
            link.getAnchorY(),
            link.getAnchorZ());

        ModNetwork.syncLinkState(player);
    }

    /** Called by {@link EntityRobot#onDeath} so a destroyed robot forces the player out (R8). */
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
        }
    }

    /**
     * Per player server tick: enforces the frozen body rules of D3 and detects every involuntary
     * disconnect reason.
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

    /** Server side menu clipping (D6): the options the client is allowed to draw right now. */
    public static MenuOption[] menuOptionsFor(EntityPlayer player) {
        if (player == null) {
            return new MenuOption[0];
        }
        if (isDriving(player)) {
            return new MenuOption[] { MenuOption.EXIT };
        }
        ItemStack helmetStack = player.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT);
        if (helmetStack != null && helmetStack.getItem() instanceof ItemRemoteHelmet) {
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

    private static EntityRobot resolveRobot(EntityPlayer player, ItemStack helmetStack) {
        UUID robotId = ItemRemoteHelmet.getPairedRobot(helmetStack);
        if (robotId != null) {
            EntityRobot existing = findRobot(player.worldObj, robotId);
            if (existing != null) {
                return existing;
            }
            MindRobot.LOG.warn("Paired robot {} is not loaded, pairing a fresh one", robotId);
        }
        return spawnPlaceholderRobot(player, helmetStack);
    }

    /**
     * P0 stand in for the assembly table (R14/P3): there is no way to build a robot yet, so the first
     * connection request materialises one next to the player and pairs the helmet with it.
     */
    private static EntityRobot spawnPlaceholderRobot(EntityPlayer player, ItemStack helmetStack) {
        World world = player.worldObj;
        double yaw = Math.toRadians(player.rotationYaw);
        double spawnX = player.posX - Math.sin(yaw) * 2.0D;
        double spawnZ = player.posZ + Math.cos(yaw) * 2.0D;
        if (!hasRoomForRobot(world, spawnX, player.posY, spawnZ)) {
            spawnX = player.posX;
            spawnZ = player.posZ;
        }

        EntityRobot robot = new EntityRobot(world);
        robot.setLocationAndAngles(spawnX, player.posY, spawnZ, player.rotationYaw, 0.0F);
        robot.setOwnerId(player.getUniqueID());
        world.spawnEntityInWorld(robot);
        ItemRemoteHelmet.setPairedRobot(helmetStack, robot.getUniqueID());
        return robot;
    }

    private static boolean hasRoomForRobot(World world, double x, double y, double z) {
        int blockX = MathHelper.floor_double(x);
        int blockY = MathHelper.floor_double(y);
        int blockZ = MathHelper.floor_double(z);
        return world.isAirBlock(blockX, blockY, blockZ) && world.isAirBlock(blockX, blockY + 1, blockZ);
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
