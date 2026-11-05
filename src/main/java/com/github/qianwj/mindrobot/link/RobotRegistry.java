package com.github.qianwj.mindrobot.link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.github.qianwj.mindrobot.entity.EntityRobot;

/**
 * World level registry of robot positions (R27 / D11).
 * <p>
 * The robot owns its own position: it writes a row into this registry every 20 ticks, on every chunk border
 * crossing, once on the first tick after being loaded from disk and once more when a link ends. The registry is
 * a {@link WorldSavedData}, so it can be read while the robot chunk - and therefore the robot entity - is not
 * loaded, which is exactly what makes it usable as a wake-up source.
 * <p>
 * The position stored in the helmet is a display mirror only: it is refreshed from here when a link opens or
 * ends and is never consulted to wake a chunk.
 * <p>
 * Storage is shared by every dimension of the same save ({@code World#mapStorage} is a static cache keyed by
 * the save handler, {@code World.java:338}), so each row carries the dimension it was reported in and lookups
 * filter by dimension.
 * <p>
 * Rows are written to disk by {@code MapStorage#saveAllData()} ({@code MapStorage.java:113}), which the server
 * calls from {@code WorldServer#saveLevel()} ({@code WorldServer.java:898}) on every autosave and on world
 * unload; this class therefore only marks itself dirty after each change.
 */
public final class RobotRegistry extends WorldSavedData {

    /** Fixed data name; changing it orphans every existing save file. */
    public static final String DATA_NAME = "mindrobot_robots";

    private static final int NBT_TAG_COMPOUND = 10;
    private static final String NBT_ENTRIES = "robots";
    private static final String NBT_ID = "id";
    private static final String NBT_X = "x";
    private static final String NBT_Y = "y";
    private static final String NBT_Z = "z";
    private static final String NBT_DIMENSION = "dim";
    private static final String NBT_LAST_REPORT = "lastReport";

    /** Newest report first; a channel normally maps to a single robot, this only orders debug duplicates. */
    private static final Comparator<Entry> NEWEST_FIRST = new Comparator<Entry>() {

        @Override
        public int compare(Entry left, Entry right) {
            if (left.getLastReportTick() == right.getLastReportTick()) {
                return 0;
            }
            return left.getLastReportTick() > right.getLastReportTick() ? -1 : 1;
        }
    };

    private final Map<UUID, Entry> entries = new HashMap<UUID, Entry>();

    /**
     * Required by {@code MapStorage#loadData}, which instantiates the class reflectively with the data name
     * ({@code MapStorage.java:60}).
     */
    public RobotRegistry(String mapName) {
        super(mapName);
    }

    /**
     * Returns the registry of the given world, creating and registering it on first use.
     *
     * @return the registry, or {@code null} on the client side or when the world carries no map storage
     */
    public static RobotRegistry get(World world) {
        if (world == null || world.isRemote || world.mapStorage == null) {
            return null;
        }
        WorldSavedData stored = world.mapStorage.loadData(RobotRegistry.class, DATA_NAME);
        if (stored instanceof RobotRegistry) {
            return (RobotRegistry) stored;
        }
        RobotRegistry created = new RobotRegistry(DATA_NAME);
        world.mapStorage.setData(DATA_NAME, created);
        return created;
    }

    /**
     * Writes (or refreshes) the row of this robot. Called by {@link EntityRobot} on its own tick, so the
     * machine is the only writer of its position (R27).
     * <p>
     * A robot without a chip answers on no channel (R26), so its row is dropped instead of kept around.
     */
    public void report(EntityRobot robot) {
        if (robot == null || robot.isDead || robot.worldObj == null) {
            return;
        }
        UUID robotId = robot.getUniqueID();
        ChannelData channel = robot.getChannelData();
        if (channel == null) {
            remove(robotId);
            return;
        }
        // The dimension is read from the world the entity actually lives in: Entity#dimension is only updated
        // on a dimension transfer and still says 0 for a robot spawned directly in the nether.
        entries.put(
            robotId,
            new Entry(
                channel,
                robot.posX,
                robot.posY,
                robot.posZ,
                robot.worldObj.provider.dimensionId,
                robot.worldObj.getTotalWorldTime()));
        markDirty();
    }

    /**
     * Removes the row of a robot that no longer exists.
     *
     * @return true when a row was actually removed, so the caller never marks dirty for nothing
     */
    public boolean remove(UUID robotId) {
        if (robotId == null || entries.remove(robotId) == null) {
            return false;
        }
        markDirty();
        return true;
    }

    /**
     * Drops rows whose chunk is loaded but whose robot is gone - the case of an entity removed outside
     * {@link EntityRobot#destroyRobot()} (for instance {@code /kill}). Rows of robots that merely sit in
     * unloaded chunks are deliberately kept: remembering them is the entire point of this registry.
     *
     * @return the number of dropped rows
     */
    public int pruneStale(World world) {
        if (world == null || world.isRemote) {
            return 0;
        }
        int dropped = 0;
        Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> row = iterator.next();
            Entry entry = row.getValue();
            if (!world.getChunkProvider()
                .chunkExists(entry.getChunkX(), entry.getChunkZ())) {
                continue;
            }
            if (!isRobotLoaded(world, row.getKey())) {
                iterator.remove();
                dropped++;
            }
        }
        if (dropped > 0) {
            markDirty();
        }
        return dropped;
    }

    private boolean isRobotLoaded(World world, UUID robotId) {
        for (Object candidate : world.loadedEntityList) {
            if (candidate instanceof EntityRobot && robotId.equals(((EntityRobot) candidate).getUniqueID())) {
                return true;
            }
        }
        return false;
    }

    /** The row of one robot, or {@code null} when it never reported or was unregistered. */
    public Entry get(UUID robotId) {
        return robotId == null ? null : entries.get(robotId);
    }

    /** Number of registered robots, used for logging and diagnostics. */
    public int size() {
        return entries.size();
    }

    /**
     * Every row of one channel, newest report first, across all dimensions.
     * <p>
     * No distance ordering is applied here because a distance needs a reference point; use
     * {@link #findMostRecent(int, int, double, double)} when the caller has one.
     */
    public List<Entry> findByChannel(int channel) {
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries.values()) {
            if (entry.getChannel() == channel) {
                result.add(entry);
            }
        }
        Collections.sort(result, NEWEST_FIRST);
        return result;
    }

    /**
     * The row a reconnect should wake: same channel, same dimension, newest report first, and when two rows
     * share the same report tick the one closest to the reference point wins (only debug duplicates can
     * produce that situation).
     *
     * @return the best row, or {@code null} when no robot of that channel was ever reported in that dimension
     */
    public Entry findMostRecent(int channel, int dimension, double referenceX, double referenceZ) {
        Entry best = null;
        for (Entry entry : entries.values()) {
            if (entry.getChannel() != channel || entry.getDimension() != dimension) {
                continue;
            }
            if (best == null || prefer(entry, best, referenceX, referenceZ)) {
                best = entry;
            }
        }
        return best;
    }

    private static boolean prefer(Entry candidate, Entry current, double referenceX, double referenceZ) {
        if (candidate.getLastReportTick() != current.getLastReportTick()) {
            return candidate.getLastReportTick() > current.getLastReportTick();
        }
        return candidate.distanceSqTo(referenceX, referenceZ) < current.distanceSqTo(referenceX, referenceZ);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        entries.clear();
        if (tag == null) {
            return;
        }
        NBTTagList list = tag.getTagList(NBT_ENTRIES, NBT_TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound entryTag = list.getCompoundTagAt(i);
            UUID robotId = parseUuid(entryTag.getString(NBT_ID));
            Entry entry = Entry.readFromNBT(entryTag);
            if (robotId != null && entry != null) {
                entries.put(robotId, entry);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Entry> stored : entries.entrySet()) {
            NBTTagCompound entryTag = new NBTTagCompound();
            String robotId = stored.getKey()
                .toString();
            entryTag.setString(NBT_ID, robotId);
            Entry entry = stored.getValue();
            entry.writeToNBT(entryTag);
            list.appendTag(entryTag);
        }
        tag.setTag(NBT_ENTRIES, list);
    }

    /** Parses a stored UUID without ever throwing on a malformed value. */
    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            // A corrupt row must not take the whole registry down; it is simply skipped.
            return null;
        }
    }

    /** One robot: its channel snapshot, its last reported position, the dimension and the report tick. */
    public static final class Entry {

        private final ChannelData channel;
        private final double x;
        private final double y;
        private final double z;
        private final int dimension;
        private final long lastReportTick;

        Entry(ChannelData channel, double x, double y, double z, int dimension, long lastReportTick) {
            this.channel = channel;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.lastReportTick = lastReportTick;
        }

        /**
         * Reads one entry from a registry row.
         *
         * @return the entry, or {@code null} when the row carries no usable channel
         */
        static Entry readFromNBT(NBTTagCompound tag) {
            ChannelData channel = ChannelData.readFromNBT(tag);
            if (channel == null) {
                return null;
            }
            return new Entry(
                channel,
                tag.getDouble(NBT_X),
                tag.getDouble(NBT_Y),
                tag.getDouble(NBT_Z),
                tag.getInteger(NBT_DIMENSION),
                tag.getLong(NBT_LAST_REPORT));
        }

        void writeToNBT(NBTTagCompound tag) {
            channel.writeToNBT(tag);
            tag.setDouble(NBT_X, x);
            tag.setDouble(NBT_Y, y);
            tag.setDouble(NBT_Z, z);
            tag.setInteger(NBT_DIMENSION, dimension);
            tag.setLong(NBT_LAST_REPORT, lastReportTick);
        }

        public int getChannel() {
            return channel.getChannel();
        }

        public ChannelMode getMode() {
            return channel.getMode();
        }

        public UUID getOwner() {
            return channel.getOwner();
        }

        /** The full channel snapshot, reusable for the D5 checks without rebuilding one. */
        public ChannelData getChannelData() {
            return channel;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public int getDimension() {
            return dimension;
        }

        public long getLastReportTick() {
            return lastReportTick;
        }

        public int getBlockX() {
            return MathHelper.floor_double(x);
        }

        public int getBlockY() {
            return MathHelper.floor_double(y);
        }

        public int getBlockZ() {
            return MathHelper.floor_double(z);
        }

        public int getChunkX() {
            return MathHelper.floor_double(x) >> 4;
        }

        public int getChunkZ() {
            return MathHelper.floor_double(z) >> 4;
        }

        /** Squared horizontal distance, used only to break a report-tick tie. */
        public double distanceSqTo(double referenceX, double referenceZ) {
            double deltaX = x - referenceX;
            double deltaZ = z - referenceZ;
            return deltaX * deltaX + deltaZ * deltaZ;
        }

        /** Renders the row as the helmet mirror value (tooltip only, never a wake-up source). */
        public LastKnownPosition asPosition() {
            return new LastKnownPosition(x, y, z, dimension);
        }
    }
}
