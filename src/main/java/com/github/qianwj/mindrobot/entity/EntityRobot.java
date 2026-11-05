package com.github.qianwj.mindrobot.entity;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.api.IRobotCore;
import com.github.qianwj.mindrobot.container.RobotGuiHandler;
import com.github.qianwj.mindrobot.damage.RobotDamage;
import com.github.qianwj.mindrobot.inventory.RobotInventory;
import com.github.qianwj.mindrobot.item.CameraType;
import com.github.qianwj.mindrobot.item.ItemFrequencyChip;
import com.github.qianwj.mindrobot.link.ChannelData;
import com.github.qianwj.mindrobot.link.RobotLinkManager;
import com.github.qianwj.mindrobot.link.RobotRegistry;

/**
 * Remote controlled robot body.
 * <p>
 * It extends {@link EntityLivingBase} because the client camera field
 * {@code Minecraft.renderViewEntity} is declared as {@code EntityLivingBase}, not {@code Entity}
 * (design decision D1).
 * <p>
 * Health is a disguise only: the vanilla health pool never changes and vanilla death handling is never
 * entered, so other mods cannot drain or resurrect the robot through living entity hooks. The real
 * durability is {@link #getStructuralIntegrity()} and is routed through {@link RobotDamage}.
 * <p>
 * A fresh body is <b>unfinished</b> (R14 / R14b / D17): the assembly table only produces the chassis, and the
 * chip, the camera, the backpack part and the battery are installed afterwards through the item container. An
 * unfinished body can still be right clicked open - that is the only way to finish it - but it is invisible to
 * both connection paths, cannot be driven and does not pick anything up. {@link #isComplete()} is the single
 * place that decision comes from.
 */
public class EntityRobot extends EntityLivingBase implements IRobotCore {

    private static final String NBT_STRUCTURAL_INTEGRITY = "StructuralIntegrity";

    /** How often the robot reports its own position into {@link RobotRegistry} (R27). 20 ticks = 1 second. */
    private static final int POSITION_REPORT_INTERVAL = 20;
    private static final int UNKNOWN_CHUNK = Integer.MIN_VALUE;

    /** Display only health value. Never used as durability. */
    private static final float DISGUISE_MAX_HEALTH = 20.0F;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.1D;

    /** P0 placeholder cap, the assembly parts of P3 will sum their durability into this. */
    public static final int DEFAULT_MAX_STRUCTURAL_INTEGRITY = 100;

    private static final int EQUIPMENT_SLOTS = 5;
    private static final float DESTROY_EFFECT_HEIGHT_OFFSET = 0.5F;
    /** Height above the feet a dropped stack leaves the body at; the vanilla item drop offset. */
    private static final float DROP_HEIGHT_OFFSET = 0.5F;

    /** How far the robot reaches for loose items, in blocks on every axis (R36). */
    private static final double PICKUP_RANGE = 1.0D;
    /** Pickup runs every 4 ticks: fast enough to feel automatic, cheap enough for a server tick. */
    private static final int PICKUP_INTERVAL = 4;

    /** Inventory compound key; the inventory writes its own {@code Items} list inside it. */
    private static final String NBT_INVENTORY = "RobotInventory";

    /**
     * Data watcher slot carrying the part flags. The client builds the very same container as the server, but
     * item stacks are only synced for slots the open container owns, not for the entity inventory, so the
     * "backpack part installed" bit - which decides whether the 54 cells exist - has to travel separately.
     */
    private static final int DATA_PART_FLAGS = 20;
    private static final byte FLAG_BACKPACK = 1;

    /**
     * Channel snapshot of this robot (D5): the public number, or the private name plus password, and the
     * public/private mode. It is a cache of what the chip slot currently holds (R21 / R26) - never written from
     * anywhere else - and {@code null} when no chip is installed, which is what makes the machine unreachable.
     */
    private ChannelData channelData;
    /** Forced true on every load from NBT, so the first tick after a restart re-registers the robot (R27). */
    private boolean positionReportPending = true;
    /** Chunk of the last report: a border crossing reports at once instead of waiting for the interval. */
    private int lastReportedChunkX = UNKNOWN_CHUNK;
    private int lastReportedChunkZ = UNKNOWN_CHUNK;
    /** Real durability of the chassis. */
    private int structuralIntegrity = DEFAULT_MAX_STRUCTURAL_INTEGRITY;
    /** The 65 slot item storage (R35 / D15); created for both sides so the container always has a target. */
    private final RobotInventory robotInventory = new RobotInventory(this);

    public EntityRobot(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        // Bypass the overridden no-op on purpose: this is the one place the disguise value is written.
        super.setHealth(DISGUISE_MAX_HEALTH);
        dataWatcher.addObject(DATA_PART_FLAGS, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        // Registered by the superclass; set explicitly so other mods reading the attributes never NPE.
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(DISGUISE_MAX_HEALTH);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(DEFAULT_MOVEMENT_SPEED);
    }

    /** Disguise only, the real durability lives in {@link #structuralIntegrity}. */
    @Override
    public void setHealth(float health) {
        // Intentionally empty: the robot must never enter the vanilla death flow.
    }

    /**
     * Robot self reporting (R27 / D11): the machine, not the helmet, is the source of its own position.
     * <p>
     * A row is written every {@link #POSITION_REPORT_INTERVAL} ticks, immediately after a chunk border
     * crossing, and once on the first tick after the entity was loaded from disk (so a restart never leaves a
     * stale coordinate behind). Client side copies never report.
     * <p>
     * The same server tick also runs the automatic pickup (R36).
     */
    @Override
    public void onEntityUpdate() {
        super.onEntityUpdate();
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        syncPartFlags();
        reportPosition();
        if (ticksExisted % PICKUP_INTERVAL == 0) {
            collectNearbyItems();
        }
    }

    /**
     * Keeps the client side container layout in step with the installed parts: a body gains or loses the 54
     * backpack cells the moment the backpack part is put in or taken out.
     */
    private void syncPartFlags() {
        byte flags = (byte) (robotInventory.isBackpackInstalled() ? FLAG_BACKPACK : 0);
        if (dataWatcher.getWatchableObjectByte(DATA_PART_FLAGS) != flags) {
            dataWatcher.updateObject(DATA_PART_FLAGS, Byte.valueOf(flags));
        }
    }

    /**
     * True when the backpack part is installed. The server reads the slot, the client reads the mirrored flag:
     * the client inventory object is empty by design, because only the slots of an open container carry synced
     * stacks, so reading it there would report "no backpack" for a finished robot.
     */
    public boolean hasBackpackPart() {
        if (worldObj != null && worldObj.isRemote) {
            return (dataWatcher.getWatchableObjectByte(DATA_PART_FLAGS) & FLAG_BACKPACK) != 0;
        }
        return robotInventory.isBackpackInstalled();
    }

    /**
     * The completion gate of R14b / D17, asked by every path that would give an unfinished body a job: the two
     * connection paths, the drive tick, the automatic pickup and the registry (an unfinished body must not even
     * keep a wake-up row). The rule itself lives in {@link RobotInventory#isComplete()}.
     */
    public boolean isComplete() {
        return robotInventory.isComplete();
    }

    /**
     * Kind of the installed camera, or {@code null} when the camera slot is empty (R33). The night vision of the
     * driver is derived from this every tick, so a swap takes effect immediately and leaves nothing behind.
     */
    public CameraType getCameraType() {
        return robotInventory.getCameraType();
    }

    /** True while a thermal camera is installed; the driver then gets night vision (R33). */
    public boolean hasThermalCamera() {
        return getCameraType() == CameraType.THERMAL;
    }

    /**
     * Automatic pickup (R36): every loose item within {@link #PICKUP_RANGE} of the body is moved into the
     * backpack, or into the toolbar once the backpack is full.
     * <p>
     * The grid it writes into is chosen by {@link RobotInventory#insertIntoBackpack(ItemStack)} and that method
     * only ever touches slots 0..62 - the chip and the battery slot are not part of its range and
     * {@link RobotInventory#acceptsStorage(ItemStack)} turns a chip or a battery away before the insert is even
     * attempted, so no pickup can reach slot 63 or 64.
     */
    private void collectNearbyItems() {
        if (isDead || !isEntityAlive()) {
            return;
        }
        // R14b / D17: an unfinished body has no working storage, so it does not vacuum the ground either.
        if (!isComplete()) {
            return;
        }
        AxisAlignedBB area = boundingBox.expand(PICKUP_RANGE, PICKUP_RANGE, PICKUP_RANGE);
        List<EntityItem> items = worldObj.getEntitiesWithinAABB(EntityItem.class, area);
        for (EntityItem item : items) {
            absorb(item);
        }
    }

    /** Picks up what fits and leaves the rest lying on the ground, never dropping the remainder. */
    private void absorb(EntityItem item) {
        if (item == null || item.isDead || item.delayBeforeCanPickup > 0) {
            return;
        }
        ItemStack stack = item.getEntityItem();
        if (stack == null || stack.stackSize <= 0 || !RobotInventory.acceptsStorage(stack)) {
            return;
        }
        int available = stack.stackSize;
        // Work on a copy: the watcher only syncs a new stack object to the client, and a partial pickup must
        // show the reduced size instead of a ghost item.
        ItemStack leftover = robotInventory.insertIntoBackpack(stack.copy());
        int picked = available - (leftover == null ? 0 : leftover.stackSize);
        if (picked <= 0) {
            return;
        }
        // Vanilla pickup bookkeeping: the collect animation for every tracker, then the body hook.
        onItemPickup(item, picked);
        worldObj
            .playSoundAtEntity(this, "random.pop", 0.2F, ((rand.nextFloat() - rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        if (leftover == null || leftover.stackSize <= 0) {
            item.setDead();
        } else {
            item.setEntityItemStack(leftover);
        }
    }

    /** The 65 slot storage of this body (R35 / D15). */
    public RobotInventory getInventory() {
        return robotInventory;
    }

    /**
     * Player right click on the body (R35 / D15).
     * <p>
     * The robot extends {@link EntityLivingBase} directly, so it inherits {@code Entity.interactFirst}, the
     * non final hook {@code EntityPlayer.interactWith} calls before any held item may react - this is the
     * stable path that works on both sides without consuming anything. The client answers true so a click does
     * not also turn into a local item use; the server alone decides whether a screen opens.
     */
    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (player == null || worldObj == null) {
            return false;
        }
        if (worldObj.isRemote) {
            return true;
        }
        return RobotGuiHandler.open(player, this);
    }

    /** Writes the registry row when one of the three triggers fires; otherwise does nothing. */
    private void reportPosition() {
        int chunkX = MathHelper.floor_double(posX) >> 4;
        int chunkZ = MathHelper.floor_double(posZ) >> 4;
        boolean crossedChunk = chunkX != lastReportedChunkX || chunkZ != lastReportedChunkZ;
        boolean due = ticksExisted % POSITION_REPORT_INTERVAL == 0;
        if (!positionReportPending && !crossedChunk && !due) {
            return;
        }
        RobotRegistry registry = RobotRegistry.get(worldObj);
        if (registry == null) {
            return;
        }
        positionReportPending = false;
        lastReportedChunkX = chunkX;
        lastReportedChunkZ = chunkZ;
        registry.report(this);
    }

    @Override
    public ItemStack getHeldItem() {
        return null;
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        return null;
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {
        // The robot has no equipment slots in P0.
    }

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[EQUIPMENT_SLOTS];
    }

    /**
     * Routes every hit into {@link RobotDamage} instead of the vanilla damage pipeline.
     * {@code super.attackEntityFrom} is deliberately not called: no hurt event, no death event.
     */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (worldObj == null || worldObj.isRemote || isDead || source == null) {
            return false;
        }
        int integrityBefore = structuralIntegrity;
        applyStructuralDamage(source, amount);
        return isDead || structuralIntegrity < integrityBefore;
    }

    /**
     * Applies classified damage to the chassis.
     *
     * @return the remaining structural integrity
     */
    @Override
    public int applyStructuralDamage(DamageSource source, float amount) {
        if (worldObj == null || worldObj.isRemote || isDead) {
            return structuralIntegrity;
        }
        float adjusted = RobotDamage.apply(source, amount);
        if (adjusted <= 0.0F) {
            return structuralIntegrity;
        }
        if (Float.isInfinite(adjusted)) {
            destroyRobot();
            return structuralIntegrity;
        }
        structuralIntegrity -= Math.max(1, MathHelper.ceiling_float_int(adjusted));
        if (structuralIntegrity <= 0) {
            structuralIntegrity = 0;
            destroyRobot();
        }
        return structuralIntegrity;
    }

    @Override
    public int getStructuralIntegrity() {
        return structuralIntegrity;
    }

    @Override
    public int getMaxStructuralIntegrity() {
        return DEFAULT_MAX_STRUCTURAL_INTEGRITY;
    }

    @Override
    public boolean isRobot() {
        return true;
    }

    /**
     * Ends the chassis: effect, death flag and the connection teardown. Never touches the vanilla death
     * path, so no living death event is fired for the robot.
     */
    public void destroyRobot() {
        if (isDead) {
            return;
        }
        structuralIntegrity = 0;
        if (!worldObj.isRemote) {
            double effectY = posY + height * DESTROY_EFFECT_HEIGHT_OFFSET;
            worldObj.playSoundEffect(posX, effectY, posZ, "random.explode", 1.0F, 1.0F);
            worldObj.spawnParticle("largeexplode", posX, effectY, posZ, 0.0D, 0.0D, 0.0D);
            // D15: a wreck drops what it carried instead of swallowing the player's items.
            dropInventory();
            RobotLinkManager.notifyRobotDestroyed(this);
            // Unregister last: the disconnect above may re-report the still loaded robot, and a destroyed
            // machine must not stay a wake-up target (R27).
            RobotRegistry registry = RobotRegistry.get(worldObj);
            if (registry != null) {
                registry.remove(getUniqueID());
            }
        }
        setDead();
    }

    /** Drops every stored stack at the wreck position, one entity item per slot, like any other container. */
    private void dropInventory() {
        for (int index = 0; index < robotInventory.getSizeInventory(); ++index) {
            ItemStack stack = robotInventory.getStackInSlot(index);
            if (stack == null) {
                continue;
            }
            entityDropItem(stack, DROP_HEIGHT_OFFSET);
            robotInventory.setInventorySlotContents(index, null);
        }
    }

    /** Safety net: even a direct {@code onDeath} call is redirected away from the vanilla pipeline. */
    @Override
    public void onDeath(DamageSource source) {
        destroyRobot();
    }

    /** Channel snapshot this robot answers on, or {@code null} when it has none (D5). */
    public ChannelData getChannelData() {
        return channelData;
    }

    /**
     * Re-derives the channel from the chip slot and publishes it at once (R21 / R26, D5).
     * <p>
     * The chip slot is the <b>only</b> source of the machine channel: an empty slot means "answers on nothing",
     * and pulling the chip makes the robot unreachable through every path that reads
     * {@link #getChannelData()} - the registry row is dropped, so a private wake finds nothing, and a public
     * helmet no longer matches. Nothing else in the mod writes the field.
     * <p>
     * The registry is refreshed immediately instead of on the next scheduled report, because the wake-up path
     * of R27/R28 reads the row: a chip swap would otherwise leave the old channel registered for up to a
     * second. The immediate write only happens for a robot that is already part of the world; a freshly built
     * entity reports on its first tick instead.
     */
    public void syncChannelFromChip() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        channelData = ItemFrequencyChip.getChannelData(robotInventory.getChip());
        // A chip installed after spawning must be visible in the registry on the next tick, not one second
        // later (R26 vs R27: the row only exists for a robot that answers on a channel).
        positionReportPending = true;
        if (!addedToChunk) {
            return;
        }
        RobotRegistry registry = RobotRegistry.get(worldObj);
        if (registry == null) {
            return;
        }
        // Report right away instead of waiting for the scheduled one: the wake-up path reads this row (R27).
        positionReportPending = false;
        // report() drops the row of a robot with no channel, which is exactly what R26 wants.
        registry.report(this);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger(NBT_STRUCTURAL_INTEGRITY, structuralIntegrity);
        // The channel is deliberately not written: it is derived from the chip slot on load, so the save file
        // can never disagree with the chip that is actually installed.
        NBTTagCompound inventoryTag = new NBTTagCompound();
        robotInventory.writeToNBT(inventoryTag);
        tag.setTag(NBT_INVENTORY, inventoryTag);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        structuralIntegrity = tag.hasKey(NBT_STRUCTURAL_INTEGRITY)
            ? MathHelper.clamp_int(tag.getInteger(NBT_STRUCTURAL_INTEGRITY), 1, getMaxStructuralIntegrity())
            : getMaxStructuralIntegrity();
        if (tag.hasKey(NBT_INVENTORY)) {
            robotInventory.readFromNBT(tag.getCompoundTag(NBT_INVENTORY));
        }
        // The chip inside the restored storage decides the channel; an empty slot means "answers on nothing".
        // This call cannot reach the registry yet (the entity is not in a chunk during NBT loading), it only
        // fills the field, and the first tick publishes the row.
        channelData = null;
        syncChannelFromChip();
        // The registry row is rewritten on the first tick after a load, so a restart never leaves the old
        // coordinate (R27) and the helmet mirror can be refreshed from a fresh row.
        positionReportPending = true;
        lastReportedChunkX = UNKNOWN_CHUNK;
        lastReportedChunkZ = UNKNOWN_CHUNK;
    }
}
