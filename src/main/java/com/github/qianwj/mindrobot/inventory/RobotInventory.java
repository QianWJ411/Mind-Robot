package com.github.qianwj.mindrobot.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.item.CameraType;
import com.github.qianwj.mindrobot.item.ItemFrequencyChip;
import com.github.qianwj.mindrobot.item.ItemRobotBackpack;
import com.github.qianwj.mindrobot.item.ItemRobotCamera;

import ic2.api.item.IElectricItem;

/**
 * The item storage of a robot body (R35 / D15): 9 toolbar slots, 54 backpack slots, one chip slot, one battery
 * slot, one camera slot and one backpack part slot, 67 in total.
 * <p>
 * <b>Slot layout.</b> 0..8 is the toolbar - the first row of the container, which is what the driver operates
 * while the body inventory is hidden (R10). 9..62 is the backpack, the bulk storage a robot fills on its own
 * (R36); those 54 cells only exist while the backpack part of {@link #BACKPACK_PART_SLOT} is installed (R14b).
 * 63 is the chip slot, 64 the battery slot, 65 the camera slot and 66 the backpack part slot; the four special
 * slots only exist for the screen and for the machine itself, they are never part of the pickup path.
 * <p>
 * <b>Isolation (R37).</b> {@link #acceptsStorage(ItemStack)} is the single predicate behind "a part never lands
 * in the toolbar or in the backpack": the slot classes, the shift-click handler and the automatic pickup all
 * call it, so the rule cannot drift apart between the three call sites. The special slots have the mirrored
 * rule and only accept {@link ItemFrequencyChip}, {@link IElectricItem}, {@link ItemRobotCamera} respectively
 * {@link ItemRobotBackpack}. The battery slot is the battery box layer of R39: a robot reads its energy from
 * this slot only and never from a loose battery lying around.
 * <p>
 * <b>Completion (R14b / D17).</b> {@link #isComplete()} is the single completion rule: chip, camera, backpack
 * part and battery all installed. The connection, the pickup and the drive paths ask the robot, and the robot
 * asks this method, so "unfinished" cannot mean two different things in two places.
 * <p>
 * The whole inventory is stored in the entity compound as a list of {@code {Slot, Item}} entries, so a robot
 * keeps its contents across a save and a chunk unload.
 */
public class RobotInventory implements IInventory {

    /** First row of the container: the toolbar the driver operates while driving (R35). */
    public static final int TOOL_SLOT_COUNT = 9;
    /** Backpack rows of the container, six rows of nine (R35). */
    public static final int BACKPACK_ROW_COUNT = 6;
    public static final int BACKPACK_COLUMN_COUNT = 9;
    public static final int BACKPACK_SLOT_COUNT = BACKPACK_ROW_COUNT * BACKPACK_COLUMN_COUNT;

    public static final int TOOL_FIRST_SLOT = 0;
    public static final int TOOL_END_SLOT = TOOL_FIRST_SLOT + TOOL_SLOT_COUNT;
    public static final int BACKPACK_FIRST_SLOT = TOOL_END_SLOT;
    public static final int BACKPACK_END_SLOT = BACKPACK_FIRST_SLOT + BACKPACK_SLOT_COUNT;
    /** Chip slot, only a {@link ItemFrequencyChip} may enter. */
    public static final int CHIP_SLOT = BACKPACK_END_SLOT;
    /** Battery slot, the battery box layer of R39: only an {@link IElectricItem} may enter. */
    public static final int BATTERY_SLOT = CHIP_SLOT + 1;
    /** Camera slot (R33): one {@link ItemRobotCamera}, swapped while the robot is parked. */
    public static final int CAMERA_SLOT = BATTERY_SLOT + 1;
    /** Backpack part slot (R14b): without a part here the 54 backpack cells do not exist at all. */
    public static final int BACKPACK_PART_SLOT = CAMERA_SLOT + 1;
    public static final int SLOT_COUNT = BACKPACK_PART_SLOT + 1;

    private static final String NBT_ITEMS = "Items";
    private static final String NBT_SLOT = "Slot";
    private static final String NBT_STACK = "Item";
    private static final int NBT_COMPOUND_ID = 10;

    /** 8 blocks: the reach of a vanilla chest, used to close a screen whose robot walked away. */
    private static final double MAX_USE_DISTANCE_SQ = 64.0D;

    /** Owner of the contents; {@code null} only for the detached inventory used on a client without the entity. */
    private final EntityRobot robot;
    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];

    public RobotInventory(EntityRobot robot) {
        this.robot = robot;
    }

    /**
     * The storage rule of R37: the toolbar and the backpack take anything except a part. A chip, a battery, a
     * camera and a backpack are parts of their own, so a dropped one is left on the ground for the player
     * instead of being pulled in by the automatic pickup.
     */
    public static boolean acceptsStorage(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        return !(stack.getItem() instanceof ItemFrequencyChip) && !(stack.getItem() instanceof IElectricItem)
            && !(stack.getItem() instanceof ItemRobotCamera)
            && !(stack.getItem() instanceof ItemRobotBackpack);
    }

    /** The toolbar stack of the given column, 0..8. */
    public ItemStack getToolSlot(int index) {
        if (index < 0 || index >= TOOL_SLOT_COUNT) {
            return null;
        }
        return slots[TOOL_FIRST_SLOT + index];
    }

    /** The installed connection chip, or {@code null}. */
    public ItemStack getChip() {
        return slots[CHIP_SLOT];
    }

    /** The installed battery, or {@code null}. */
    public ItemStack getBattery() {
        return slots[BATTERY_SLOT];
    }

    /** The installed camera, or {@code null}. */
    public ItemStack getCamera() {
        return slots[CAMERA_SLOT];
    }

    /** The installed backpack part, or {@code null}. */
    public ItemStack getBackpackPart() {
        return slots[BACKPACK_PART_SLOT];
    }

    /**
     * The kind of camera that is installed right now.
     *
     * @return the camera type, or {@code null} when the slot is empty or holds something else
     */
    public CameraType getCameraType() {
        return CameraType.byItemStack(slots[CAMERA_SLOT]);
    }

    /** True when the 54 backpack cells are unlocked by an installed backpack part (R14b). */
    public boolean isBackpackInstalled() {
        ItemStack part = slots[BACKPACK_PART_SLOT];
        return part != null && part.getItem() instanceof ItemRobotBackpack;
    }

    /**
     * Completion gate of R14b / D17: a body is finished only when the chip, the camera, the backpack part and a
     * battery are all installed. The battery sits inside the battery box, so the battery slot is the only place
     * the body reads its energy from (R39).
     * <p>
     * This is the one definition of "complete": the connection paths, the pickup and the drive all ask
     * {@link EntityRobot#isComplete()}, which delegates here, so the body cannot be half finished in one place
     * and finished in another.
     */
    public boolean isComplete() {
        return getChip() != null && getCamera() != null && isBackpackInstalled() && getBattery() != null;
    }

    /**
     * Inserts a stack the way a robot picks loot up (R36): the backpack first, the toolbar only once the
     * backpack is full, and never the four special slots. Without an installed backpack part the body has no
     * storage of its own, so the pickup fails outright (R14b).
     *
     * @param stack the stack that should be stored; its size is reduced by what was stored
     * @return the part that did not fit, or {@code null} when the whole stack was stored
     */
    public ItemStack insertIntoBackpack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return stack;
        }
        if (!isBackpackInstalled()) {
            return stack;
        }
        if (!acceptsStorage(stack)) {
            return stack;
        }
        ItemStack remaining = insertIntoRange(stack, BACKPACK_FIRST_SLOT, BACKPACK_END_SLOT);
        if (remaining != null) {
            remaining = insertIntoRange(remaining, TOOL_FIRST_SLOT, TOOL_END_SLOT);
        }
        return remaining;
    }

    /** Merges into matching stacks first, then fills empty slots of the range; never touches another range. */
    private ItemStack insertIntoRange(ItemStack stack, int from, int to) {
        ItemStack remaining = stack;
        for (int index = from; index < to && remaining != null; ++index) {
            remaining = mergeIntoSlot(remaining, index);
        }
        for (int index = from; index < to && remaining != null; ++index) {
            remaining = fillEmptySlot(remaining, index);
        }
        return remaining;
    }

    /** Adds as much of the stack as the slot still takes; returns the rest. */
    private ItemStack mergeIntoSlot(ItemStack stack, int index) {
        ItemStack existing = slots[index];
        if (existing == null || !isSameStack(existing, stack)) {
            return stack;
        }
        int limit = Math.min(getInventoryStackLimit(), Math.min(existing.getMaxStackSize(), stack.getMaxStackSize()));
        int space = limit - existing.stackSize;
        if (space <= 0) {
            return stack;
        }
        int moved = Math.min(space, stack.stackSize);
        existing.stackSize += moved;
        stack.stackSize -= moved;
        markDirty();
        return stack.stackSize <= 0 ? null : stack;
    }

    /** Puts part of the stack into an empty slot; returns the rest. */
    private ItemStack fillEmptySlot(ItemStack stack, int index) {
        if (slots[index] != null) {
            return stack;
        }
        int limit = Math.min(getInventoryStackLimit(), stack.getMaxStackSize());
        int moved = Math.min(limit, stack.stackSize);
        ItemStack stored = stack.copy();
        stored.stackSize = moved;
        slots[index] = stored;
        stack.stackSize -= moved;
        markDirty();
        return stack.stackSize <= 0 ? null : stack;
    }

    /** Same item, same damage value and same NBT: the vanilla rule for merging (R36). */
    private static boolean isSameStack(ItemStack left, ItemStack right) {
        return left.getItem() == right.getItem() && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    /** True for a backpack cell, which only exists while a backpack part is installed. */
    private static boolean isBackpackCell(int index) {
        return index >= BACKPACK_FIRST_SLOT && index < BACKPACK_END_SLOT;
    }

    /** Whole-inventory serialisation, used by {@link EntityRobot} from its entity compound. */
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList items = new NBTTagList();
        for (int index = 0; index < SLOT_COUNT; ++index) {
            ItemStack stack = slots[index];
            if (stack == null) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte(NBT_SLOT, (byte) index);
            entry.setTag(NBT_STACK, stack.writeToNBT(new NBTTagCompound()));
            items.appendTag(entry);
        }
        tag.setTag(NBT_ITEMS, items);
    }

    /** Restores the inventory; the array is cleared first so a reuse never mixes two robots. */
    public void readFromNBT(NBTTagCompound tag) {
        for (int index = 0; index < SLOT_COUNT; ++index) {
            slots[index] = null;
        }
        NBTTagList items = tag.getTagList(NBT_ITEMS, NBT_COMPOUND_ID);
        for (int entry = 0; entry < items.tagCount(); ++entry) {
            NBTTagCompound item = items.getCompoundTagAt(entry);
            int index = item.getByte(NBT_SLOT) & 0xFF;
            if (index >= SLOT_COUNT) {
                continue;
            }
            ItemStack stack = ItemStack.loadItemStackFromNBT(item.getCompoundTag(NBT_STACK));
            if (stack != null) {
                slots[index] = stack;
            }
        }
    }

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index >= 0 && index < SLOT_COUNT ? slots[index] : null;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack stack = getStackInSlot(index);
        if (stack == null) {
            return null;
        }
        ItemStack split;
        if (stack.stackSize <= count) {
            slots[index] = null;
            markDirty();
            split = stack;
        } else {
            split = stack.splitStack(count);
            if (stack.stackSize <= 0) {
                slots[index] = null;
            }
            markDirty();
        }
        if (index == CHIP_SLOT) {
            syncChannel();
        }
        return split;
    }

    /** The contents belong to the robot, so closing the screen must not spill them on the ground. */
    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= SLOT_COUNT) {
            return;
        }
        // A backpack cell is only reachable while the part is installed, whatever a stale window tries to do.
        if (stack != null && isBackpackCell(index) && !isBackpackInstalled()) {
            return;
        }
        slots[index] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        markDirty();
        if (index == CHIP_SLOT) {
            syncChannel();
        }
    }

    /**
     * Re-derives the robot channel from the chip slot (R21 / R26).
     * <p>
     * Every write to the chip slot - placing, replacing, taking the chip out, and the shift-click and drag
     * paths that end in {@link #setInventorySlotContents} or {@link #decrStackSize} - funnels through here, so
     * the chip is the single source of the channel: an empty slot always means "cannot be connected". Client
     * side copies are ignored by the entity, and the chip slot calls this again on every slot change, which is
     * idempotent.
     */
    public void syncChannel() {
        if (robot != null) {
            robot.syncChannelFromChip();
        }
    }

    @Override
    public String getInventoryName() {
        return "container.mindrobot.robot";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    /** The entity compound is written on every save, there is no separate dirty flag to raise. */
    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        if (robot == null || player == null || robot.isDead || !robot.isEntityAlive()) {
            return false;
        }
        return player.getDistanceSqToEntity(robot) <= MAX_USE_DISTANCE_SQ;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        if (index == CHIP_SLOT) {
            return stack.getItem() instanceof ItemFrequencyChip;
        }
        if (index == BATTERY_SLOT) {
            return stack.getItem() instanceof IElectricItem;
        }
        if (index == CAMERA_SLOT) {
            return stack.getItem() instanceof ItemRobotCamera;
        }
        if (index == BACKPACK_PART_SLOT) {
            return stack.getItem() instanceof ItemRobotBackpack;
        }
        if (isBackpackCell(index) && !isBackpackInstalled()) {
            return false;
        }
        return acceptsStorage(stack);
    }
}
