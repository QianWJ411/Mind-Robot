package com.github.qianwj.mindrobot.link;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.entity.EntityRobot;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * Keeps the chunk under a driven robot loaded while the link is open (R23, D10).
 * <p>
 * One {@link Type#NORMAL} ticket per driver. The ticket follows the robot: when the robot crosses a chunk
 * border the new chunk is forced and the old one released, so a moving robot never accumulates forced chunks.
 * When the robot changes world the ticket of the old world is released first and a new ticket is requested in
 * the new world, because a ticket is bound to a single {@link World} instance.
 * <p>
 * A chunk can also be forced at explicit coordinates: when the robot entity is missing from the loaded entity
 * list (its chunk was unloaded anyway, for instance after a ticket failure or a world hiccup) the link is kept
 * alive (R24) and the last known chunk is pulled back in, which reloads the entity and resumes the drive.
 * <p>
 * Every end path funnels through {@link RobotLinkManager#disconnect}, which always calls
 * {@link #release(UUID)}; a leaked ticket would keep forcing a chunk until the world is reloaded.
 */
public final class RobotChunkLoader {

    /** Open ticket per driver. */
    private static final Map<UUID, Ticket> TICKETS = new HashMap<UUID, Ticket>();
    /** Chunk currently forced by that ticket, so the previous one can be released on a border crossing. */
    private static final Map<UUID, ChunkCoordIntPair> FORCED_CHUNKS = new HashMap<UUID, ChunkCoordIntPair>();

    /**
     * Tickets restored from {@code forcedchunks.dat} are dropped on purpose: a link never survives a server
     * restart (the driver has to reconnect), so a persisted ticket would be an orphan that no player owns.
     */
    private static final LoadingCallback DROP_RESTORED_TICKETS = new LoadingCallback() {

        @Override
        public void ticketsLoaded(List<Ticket> tickets, World world) {
            for (Ticket ticket : tickets) {
                releaseSafely(ticket);
            }
        }
    };

    /** The mod instance handed to {@link ForgeChunkManager}; captured while FML dispatches preInit. */
    private static Object modInstance;
    private static boolean warnedMissingMod;

    private RobotChunkLoader() {}

    /**
     * Registers the forced chunk callback. Must run during preInit, while
     * {@link Loader#activeModContainer()} still points at this mod, because
     * {@link ForgeChunkManager#requestTicket} refuses to work without a registered callback.
     */
    public static void init() {
        ModContainer container = Loader.instance()
            .activeModContainer();
        if (container == null) {
            MindRobot.LOG.warn("Mind-Robot chunk loading disabled: no active mod container during preInit");
            return;
        }
        modInstance = container.getMod();
        ForgeChunkManager.setForcedChunkLoadingCallback(modInstance, DROP_RESTORED_TICKETS);
    }

    /**
     * Requests the ticket for a freshly opened link and forces the robot chunk. Failures are logged, never
     * thrown: a missing ticket must not take the connection down with it.
     */
    public static void force(UUID playerId, EntityRobot robot) {
        if (robot == null || !(robot.worldObj instanceof WorldServer)) {
            return;
        }
        safeEnsureTicket(playerId, robot.worldObj, chunkOf(robot));
    }

    /** Per tick follow-up: forces the chunk the robot moved into and releases the one it left. */
    public static void tick(UUID playerId, EntityRobot robot) {
        force(playerId, robot);
    }

    /**
     * Forces an explicit chunk, used to pull back a robot whose chunk is not loaded (R24). This is not a
     * destruction: the link stays open and the drive resumes once the entity is back in the world.
     */
    public static void forceAt(UUID playerId, World world, int chunkX, int chunkZ) {
        if (!(world instanceof WorldServer)) {
            return;
        }
        safeEnsureTicket(playerId, world, new ChunkCoordIntPair(chunkX, chunkZ));
    }

    /**
     * Releases the ticket of the given driver. Safe to call for players without a ticket and safe to call
     * twice; this is what keeps {@link RobotLinkManager#disconnect} leak free.
     */
    public static void release(UUID playerId) {
        if (playerId == null) {
            return;
        }
        FORCED_CHUNKS.remove(playerId);
        Ticket ticket = TICKETS.remove(playerId);
        if (ticket != null) {
            releaseSafely(ticket);
        }
    }

    /** True while this driver owns a live ticket, used for logging and diagnostics. */
    public static boolean hasTicket(UUID playerId) {
        return playerId != null && TICKETS.containsKey(playerId);
    }

    private static void safeEnsureTicket(UUID playerId, World world, ChunkCoordIntPair target) {
        try {
            ensureTicket(playerId, world, target);
        } catch (RuntimeException e) {
            // ForgeChunkManager throws when its world bookkeeping is gone (world unload race). The link itself
            // must survive that; the next tick retries.
            MindRobot.LOG.warn("Could not update the forced chunk of {}", playerId, e);
        }
    }

    private static void ensureTicket(UUID playerId, World world, ChunkCoordIntPair target) {
        if (playerId == null || world == null || target == null) {
            return;
        }
        if (!(world instanceof WorldServer)) {
            return;
        }
        if (modInstance == null) {
            if (!warnedMissingMod) {
                warnedMissingMod = true;
                MindRobot.LOG
                    .warn("Robot chunk loading is unavailable: the forced chunk callback was never registered");
            }
            return;
        }

        Ticket ticket = TICKETS.get(playerId);
        if (ticket != null && ticket.world != world) {
            // A ticket belongs to exactly one World; moving across dimensions means starting over (D10).
            release(playerId);
            ticket = null;
        }
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(modInstance, world, Type.NORMAL);
            if (ticket == null) {
                MindRobot.LOG.warn("Forge refused a chunk ticket for {}; the robot chunk stays unloaded", playerId);
                return;
            }
            TICKETS.put(playerId, ticket);
        }

        ChunkCoordIntPair current = FORCED_CHUNKS.get(playerId);
        if (target.equals(current)) {
            return;
        }
        if (current != null) {
            ForgeChunkManager.unforceChunk(ticket, current);
        }
        ForgeChunkManager.forceChunk(ticket, target);
        FORCED_CHUNKS.put(playerId, target);
    }

    private static ChunkCoordIntPair chunkOf(EntityRobot robot) {
        // Computed from the position instead of Entity#chunkCoordX so the value is always current.
        return new ChunkCoordIntPair(
            MathHelper.floor_double(robot.posX) >> 4,
            MathHelper.floor_double(robot.posZ) >> 4);
    }

    private static void releaseSafely(Ticket ticket) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && !server.isServerRunning()) {
            // Forge drops its whole ticket table while the server stops; releasing then has nothing left to do.
            return;
        }
        try {
            ForgeChunkManager.releaseTicket(ticket);
        } catch (RuntimeException e) {
            // A world that is already unloading drops its ticket map first; releasing then is a no-op that
            // must not bubble out of a disconnect path.
            MindRobot.LOG.warn("Failed to release a chunk ticket, the world is probably unloading", e);
        }
    }
}
