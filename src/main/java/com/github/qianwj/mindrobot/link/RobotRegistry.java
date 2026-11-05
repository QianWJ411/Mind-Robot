package com.github.qianwj.mindrobot.link;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * World level registry of robot positions and channel snapshots (R27 / D11).
 * <p>
 * The robot owns its own position: it writes a row into this registry every 20 ticks, on every chunk border
 * crossing, once on the first tick after being loaded from disk and once more when a link ends. The table lives
 * in the world save directory, so it can be read while the robot chunk - and therefore the robot entity - is not
 * loaded, which is exactly what makes it usable as a wake-up source for private channels (R28).
 * <p>
 * The position stored in the helmet is a display mirror only: it is refreshed from here when a link opens or
 * ends and is never consulted to wake a chunk.
 * <p>
 * Storage is <b>hand editable JSON</b> at {@code <world>/mindrobot/robots.json}, one registry per save directory
 * and shared by every dimension of that save (each row carries the dimension it was reported in and lookups
 * filter by dimension). This is deliberately not NBT: a server operator who has to recover a forgotten private
 * channel password can open the file in a text editor and repair it, without an NBT editor (D11).
 * <p>
 * The file is written in this shape (key names are part of the format):
 *
 * <pre>
 * {
 *   "format": 1,
 *   "robots": [
 *     {
 *       "uuid": "0f6b8c1e-...",
 *       "mode": "public",
 *       "channel": 4242,
 *       "dimension": 0,
 *       "x": 112.5,
 *       "y": 70.0,
 *       "z": -88.25,
 *       "lastReportTick": 9876543
 *     },
 *     {
 *       "uuid": "...",
 *       "mode": "private",
 *       "name": "homebase",
 *       "password": "plain text password",
 *       "dimension": 0,
 *       "x": 0.0,
 *       "y": 64.0,
 *       "z": 0.0,
 *       "lastReportTick": 42
 *     }
 *   ]
 * }
 * </pre>
 *
 * A private row stores the password in plain text on purpose (see {@link ChannelData}); anyone able to read the
 * save can read it, which is the accepted trade off for a recoverable password. Rows are picked "newest report
 * first" by {@code lastReportTick}, so a hand edited row that should win over an old duplicate needs a bigger
 * value there.
 * <p>
 * Every file access is defensive: a missing file is an empty table, an unreadable file is logged, copied to
 * {@code robots.json.broken} and then treated as an empty table, and no exception from disk IO is ever allowed
 * to reach a tick. Writes go through a {@code robots.json.tmp} file that is moved into place atomically, so a
 * crash never leaves a half written registry.
 * <p>
 * The table is marked dirty on every change and flushed diluted (every 200 ticks, {@link #flushAll()} from the
 * server tick), on world saves, on server stop and immediately when a robot is unregistered. The parsed table is
 * cached per save directory, so a tick never re-reads the file.
 */
public final class RobotRegistry {

    /** Folder under the world directory that holds the registry (D11). */
    public static final String DIRECTORY_NAME = "mindrobot";
    /** File name of the registry inside {@link #DIRECTORY_NAME} (D11). */
    public static final String FILE_NAME = "robots.json";

    private static final String TEMP_FILE_NAME = "robots.json.tmp";
    private static final String BROKEN_FILE_NAME = "robots.json.broken";

    private static final String JSON_FORMAT = "format";
    private static final int FORMAT_VERSION = 1;
    private static final String JSON_ROBOTS = "robots";
    private static final String JSON_UUID = "uuid";
    private static final String JSON_MODE = "mode";
    private static final String JSON_CHANNEL = "channel";
    private static final String JSON_NAME = "name";
    private static final String JSON_PASSWORD = "password";
    private static final String JSON_X = "x";
    private static final String JSON_Y = "y";
    private static final String JSON_Z = "z";
    private static final String JSON_DIMENSION = "dimension";
    private static final String JSON_LAST_REPORT_TICK = "lastReportTick";

    /** Pretty printed on purpose: the file is meant to be read and edited by a human. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    /** One registry per save directory; every dimension of the same save shares this entry. */
    private static final Map<File, RobotRegistry> REGISTRIES = new HashMap<File, RobotRegistry>();

    private final File directory;
    private final File file;
    private final File tempFile;
    private final File brokenFile;
    /** Insertion order is kept so the file diff stays small between saves. */
    private final Map<UUID, Entry> entries = new LinkedHashMap<UUID, Entry>();

    private boolean loaded;
    private boolean dirty;

    private RobotRegistry(File worldDirectory) {
        this.directory = new File(worldDirectory, DIRECTORY_NAME);
        this.file = new File(this.directory, FILE_NAME);
        this.tempFile = new File(this.directory, TEMP_FILE_NAME);
        this.brokenFile = new File(this.directory, BROKEN_FILE_NAME);
    }

    /**
     * Returns the registry of the given world, creating the per-save cache entry on first use and loading the
     * file lazily on the first table access.
     *
     * @return the registry, or {@code null} on the client side or when the world carries no save handler
     */
    public static RobotRegistry get(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        ISaveHandler saveHandler = world.getSaveHandler();
        if (saveHandler == null) {
            return null;
        }
        File worldDirectory = saveHandler.getWorldDirectory();
        if (worldDirectory == null) {
            return null;
        }
        synchronized (REGISTRIES) {
            RobotRegistry registry = REGISTRIES.get(worldDirectory);
            if (registry == null) {
                registry = new RobotRegistry(worldDirectory);
                REGISTRIES.put(worldDirectory, registry);
            }
            return registry;
        }
    }

    /**
     * Flushes every cached registry that has unsaved changes, used by the diluted 200 tick write, by the server
     * stopping hook and as a last resort before the worlds unload.
     */
    public static void flushAll() {
        synchronized (REGISTRIES) {
            for (RobotRegistry registry : REGISTRIES.values()) {
                registry.flush();
            }
        }
    }

    /**
     * Writes (or refreshes) the row of this robot. Called by {@link EntityRobot} on its own tick, so the machine
     * is the only writer of its position (R27).
     * <p>
     * A robot without a chip answers on no channel (R26), so its row is dropped instead of kept around. An
     * unfinished body is dropped for the same reason (R14b / D17): a wake-up row that could only ever be denied
     * is a lie in the save file, so the registry does not keep one.
     */
    public void report(EntityRobot robot) {
        if (robot == null || robot.isDead || robot.worldObj == null) {
            return;
        }
        load();
        UUID robotId = robot.getUniqueID();
        ChannelData channel = robot.getChannelData();
        if (channel == null || !channel.isConfigured() || !robot.isComplete()) {
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
        dirty = true;
    }

    /**
     * Removes the row of a robot that no longer exists and writes the table immediately: a destroyed robot is a
     * definite end state, so it must not come back as a wake-up target after a crash.
     *
     * @return true when a row was actually removed, so the caller never marks dirty for nothing
     */
    public boolean remove(UUID robotId) {
        if (robotId == null) {
            return false;
        }
        load();
        if (entries.remove(robotId) == null) {
            return false;
        }
        dirty = true;
        flush();
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
        load();
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
            dirty = true;
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
        if (robotId == null) {
            return null;
        }
        load();
        return entries.get(robotId);
    }

    /** Number of registered robots, used for logging and diagnostics. */
    public int size() {
        load();
        return entries.size();
    }

    /**
     * The newest row of a public channel in one dimension, used by read-only diagnostics. Public channels are
     * never woken from this registry (R28): the caller only reports that a registered robot exists but is not
     * loaded, it must not force a chunk from this result.
     *
     * @return the newest row, or {@code null} when no public robot of that number was ever reported there
     */
    public Entry findPublic(int channel, int dimension) {
        load();
        Entry best = null;
        for (Entry entry : entries.values()) {
            if (!entry.isPublic() || entry.getChannel() != channel || entry.getDimension() != dimension) {
                continue;
            }
            if (best == null || entry.getLastReportTick() > best.getLastReportTick()) {
                best = entry;
            }
        }
        return best;
    }

    /**
     * The newest row whose private credentials match exactly (name and plain text password), used by R28 to
     * decide whether a private channel is allowed to wake its chunk.
     *
     * @return the matching row, or {@code null} when the name does not exist or the password differs
     */
    public Entry findPrivate(String name, String password, int dimension) {
        load();
        Entry best = null;
        for (Entry entry : entries.values()) {
            if (entry.getDimension() != dimension || !entry.matchesPrivate(name, password)) {
                continue;
            }
            if (best == null || entry.getLastReportTick() > best.getLastReportTick()) {
                best = entry;
            }
        }
        return best;
    }

    /**
     * True when a private channel of that name exists in the dimension, used only to tell a wrong password
     * apart from an unknown name in the refusal message.
     */
    public boolean hasPrivateName(String name, int dimension) {
        if (name == null) {
            return false;
        }
        load();
        for (Entry entry : entries.values()) {
            if (entry.getDimension() == dimension && entry.isPrivate() && name.equals(entry.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes the table when it has unsaved changes.
     *
     * @return true when the file was written, false when there was nothing to do or the write failed
     */
    public boolean flush() {
        load();
        if (!dirty) {
            return false;
        }
        if (!writeFile()) {
            // Keep the dirty flag: the next periodic flush retries instead of losing the row.
            return false;
        }
        dirty = false;
        return true;
    }

    private boolean writeFile() {
        if (!ensureDirectory()) {
            return false;
        }
        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                GSON.toJson(buildJson(), writer);
            }
            moveIntoPlace();
            return true;
        } catch (IOException | RuntimeException error) {
            // Disk trouble or an unexpected Gson failure must never reach a tick.
            MindRobot.LOG.error("Could not write the robot registry " + file, error);
            return false;
        }
    }

    /** Atomic replace: the registry is never visible in a half written state (D11). */
    private void moveIntoPlace() throws IOException {
        try {
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException fallback) {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean ensureDirectory() {
        if (directory.isDirectory()) {
            return true;
        }
        if (directory.mkdirs() || directory.isDirectory()) {
            return true;
        }
        MindRobot.LOG.error("Could not create the robot registry directory {}", directory);
        return false;
    }

    private JsonObject buildJson() {
        JsonArray robots = new JsonArray();
        for (Map.Entry<UUID, Entry> stored : entries.entrySet()) {
            robots.add(
                stored.getValue()
                    .toJson(stored.getKey()));
        }
        JsonObject root = new JsonObject();
        root.addProperty(JSON_FORMAT, FORMAT_VERSION);
        root.add(JSON_ROBOTS, robots);
        return root;
    }

    /** Reads the file once; a missing, empty or unreadable file leaves the table empty and never throws. */
    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!file.isFile()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (root == null || root.isJsonNull()) {
                // An empty file is a valid empty table (for instance after a hand edit cleared it).
                return;
            }
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("The root element is not a JSON object");
            }
            JsonObject json = root.getAsJsonObject();
            int format = readInt(json, JSON_FORMAT, FORMAT_VERSION);
            if (format != FORMAT_VERSION) {
                MindRobot.LOG.warn(
                    "Robot registry {} declares unknown format {}; reading it as format {}",
                    file,
                    Integer.valueOf(format),
                    Integer.valueOf(FORMAT_VERSION));
            }
            JsonElement robots = json.get(JSON_ROBOTS);
            if (robots == null || robots.isJsonNull()) {
                return;
            }
            if (!robots.isJsonArray()) {
                throw new IllegalArgumentException("'" + JSON_ROBOTS + "' is not a JSON array");
            }
            readEntries(robots.getAsJsonArray());
        } catch (IOException | RuntimeException error) {
            entries.clear();
            MindRobot.LOG.error(
                "Could not read the robot registry {}; it is treated as an empty table and kept as {}",
                file,
                brokenFile,
                error);
            backupBrokenFile();
        }
    }

    private void readEntries(JsonArray robots) {
        for (int i = 0; i < robots.size(); ++i) {
            JsonElement element = robots.get(i);
            if (element == null || !element.isJsonObject()) {
                MindRobot.LOG.warn("Skipping robot entry #{} in {}: not a JSON object", Integer.valueOf(i), file);
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            UUID robotId = parseUuid(readString(object, JSON_UUID));
            if (robotId == null) {
                MindRobot.LOG.warn(
                    "Skipping robot entry #{} in {}: '{}' is not a robot UUID",
                    Integer.valueOf(i),
                    file,
                    readString(object, JSON_UUID));
                continue;
            }
            try {
                Entry entry = Entry.readFromJson(object);
                if (entry == null) {
                    MindRobot.LOG.warn(
                        "Skipping robot entry #{} ({}) in {}: incomplete channel snapshot or coordinates",
                        Integer.valueOf(i),
                        robotId,
                        file);
                    continue;
                }
                entries.put(robotId, entry);
            } catch (RuntimeException malformed) {
                // One broken hand edited row must not take the rest of the table down.
                MindRobot.LOG.warn(
                    "Skipping robot entry #{} ({}) in {}: {}",
                    Integer.valueOf(i),
                    robotId,
                    file,
                    malformed.toString());
            }
        }
    }

    /** Keeps a copy of a file we could not parse before a later flush replaces it. */
    private void backupBrokenFile() {
        try {
            Files.copy(file.toPath(), brokenFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException backupError) {
            MindRobot.LOG.warn("Could not back up the unreadable robot registry {}", file, backupError);
        }
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    /**
     * Reads a required coordinate.
     *
     * @throws IllegalArgumentException when the key is missing or not a number, which skips only that row
     */
    private static double readDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing numeric field '" + key + "'");
        }
        return element.getAsDouble();
    }

    /** Parses a stored UUID without ever throwing on a malformed value. */
    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * One robot: its channel snapshot, its last reported position, the dimension and the report tick. The
     * snapshot is either a public number or a private name plus plain text password; the password is never
     * exposed through a getter.
     */
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
         * @return the entry, or {@code null} when the row carries no usable channel snapshot
         */
        static Entry readFromJson(JsonObject object) {
            ChannelData channel = readChannel(object);
            if (channel == null || !channel.isConfigured()) {
                return null;
            }
            return new Entry(
                channel,
                readDouble(object, JSON_X),
                readDouble(object, JSON_Y),
                readDouble(object, JSON_Z),
                readInt(object, JSON_DIMENSION, 0),
                readLong(object, JSON_LAST_REPORT_TICK, 0L));
        }

        /**
         * Reads the channel snapshot the way a human would write it: the mode may be omitted, in which case a
         * {@code channel} field means public and a {@code name}/{@code password} pair means private.
         */
        private static ChannelData readChannel(JsonObject object) {
            ChannelMode mode = readMode(object);
            if (mode == ChannelMode.PUBLIC) {
                int channel = readInt(object, JSON_CHANNEL, -1);
                return ChannelData.isValidChannel(channel) ? ChannelData.publicChannel(channel) : null;
            }
            if (mode == ChannelMode.PRIVATE) {
                return ChannelData.privateChannel(readString(object, JSON_NAME), readString(object, JSON_PASSWORD));
            }
            return null;
        }

        private static ChannelMode readMode(JsonObject object) {
            String mode = readString(object, JSON_MODE);
            if (mode == null) {
                return object.has(JSON_CHANNEL) ? ChannelMode.PUBLIC : ChannelMode.PRIVATE;
            }
            if (ChannelMode.PUBLIC.getNbtName()
                .equalsIgnoreCase(mode)) {
                return ChannelMode.PUBLIC;
            }
            if (ChannelMode.PRIVATE.getNbtName()
                .equalsIgnoreCase(mode)) {
                return ChannelMode.PRIVATE;
            }
            return null;
        }

        private JsonObject toJson(UUID robotId) {
            JsonObject object = new JsonObject();
            object.addProperty(JSON_UUID, robotId.toString());
            object.addProperty(
                JSON_MODE,
                channel.getMode()
                    .getNbtName());
            if (channel.isPublic()) {
                object.addProperty(JSON_CHANNEL, channel.getChannel());
            } else {
                object.addProperty(JSON_NAME, channel.getName());
                object.addProperty(JSON_PASSWORD, channel.getPassword());
            }
            object.addProperty(JSON_DIMENSION, dimension);
            object.addProperty(JSON_X, x);
            object.addProperty(JSON_Y, y);
            object.addProperty(JSON_Z, z);
            object.addProperty(JSON_LAST_REPORT_TICK, lastReportTick);
            return object;
        }

        /** The public channel number, or -1 for a private row. */
        public int getChannel() {
            return channel.getChannel();
        }

        public ChannelMode getMode() {
            return channel.getMode();
        }

        public boolean isPublic() {
            return channel.isPublic();
        }

        public boolean isPrivate() {
            return channel.isPrivate();
        }

        /** The private channel name, or {@code null} for a public row. */
        public String getName() {
            return channel.getName();
        }

        /** The full channel snapshot, reusable for the D5 checks without rebuilding one. */
        public ChannelData getChannelData() {
            return channel;
        }

        boolean matchesPrivate(String name, String password) {
            return channel.isPrivate() && channel.matchesPrivate(name, password);
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

        /** Renders the row as the helmet mirror value (tooltip only, never a wake-up source). */
        public LastKnownPosition asPosition() {
            return new LastKnownPosition(x, y, z, dimension);
        }
    }
}
