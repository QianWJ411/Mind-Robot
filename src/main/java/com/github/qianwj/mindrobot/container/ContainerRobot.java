package com.github.qianwj.mindrobot.container;

import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.inventory.RobotInventory;

/**
 * The robot item container (R35 / D15): the robot slots plus the 36 player slots.
 * <p>
 * <b>Layout.</b> The first row (0..8) is the toolbar the driver operates while driving, then come the six
 * backpack rows (9..62) - those only exist while a backpack part is installed (R14b) - and the machine slots on
 * the right edge: chip, battery box, camera and backpack part, followed by the battery cells. The player
 * inventory follows as usual: 27 main slots, then the hotbar. Coordinates live here and the screen only paints
 * the recesses, so the two never drift apart.
 * <p>
 * <b>Battery cells (R41).</b> The body is the container, the box only unlocks cells: the container adds exactly
 * {@link EntityRobot#getBatteryCellCount()} cell slots, which is 0 without a box and 1..4 with a 1x..4x box.
 * When the box leaves the slot, every cell it covered is handed back to the player - into the inventory when
 * there is room, onto the ground otherwise - so a box swap can never destroy a battery.
 * <p>
 * <b>Two audiences.</b> The server builds this container from the real inventory, the client from the entity it
 * tracks; item stacks are not synced for an entity inventory, only for the slots of an open container, so the
 * client cannot look at the slots to decide how many cells exist. Those counts are mirrored through the entity
 * data watcher instead ({@link EntityRobot#hasBackpackPart()}, {@link EntityRobot#getBatteryCellCount()}).
 * <p>
 * <b>Isolation.</b> Placement is refused by the slot classes themselves; the shift-click paths below ask the
 * slots before they move anything, because {@link Container#mergeItemStack} does not consult
 * {@code isItemValid}. An item that cannot be placed stays where it is.
 * <p>
 * <b>Lifetime.</b> The screen is bound to a live, loaded robot: {@link #canInteractWith} fails once the robot is
 * destroyed or its chunk is unloaded, and Forge closes the container on the next server tick instead of leaving
 * a ghost screen behind.
 */
public class ContainerRobot extends Container {

    public static final int PLAYER_SLOT_COUNT = 36;

    private static final int SLOT_SIZE = 18;
    private static final int GRID_X = 8;
    private static final int TOOLBAR_Y = 18;
    private static final int BACKPACK_Y = 40;
    private static final int SPECIAL_SLOT_X = 176;
    private static final int CHIP_Y = 18;
    private static final int BATTERY_BOX_Y = 40;
    private static final int CAMERA_Y = 62;
    private static final int BACKPACK_PART_Y = 84;
    private static final int BATTERY_CELL_Y = 106;
    private static final int PLAYER_GRID_FIRST_INVENTORY_SLOT = 9;
    private static final int PLAYER_GRID_ROWS = 3;
    private static final int PLAYER_GRID_Y = 164;
    private static final int HOTBAR_Y = 222;

    /** Same reach as a vanilla chest; the robot walking away closes the screen. */
    private static final double MAX_USE_DISTANCE_SQ = 64.0D;

    private final EntityPlayer player;
    private final EntityRobot robot;
    private final RobotInventory robotInventory;
    /**
     * Container index of every robot slot, or -1 when that slot is not part of this layout (the backpack cells
     * of a body without a backpack, the battery cells a box does not unlock). Container indices are not robot
     * slot indices: the optional rows sit between the toolbar and the machine slots, so the two numberings
     * differ as soon as one group is missing.
     */
    private final int[] containerIndexOfRobotSlot = new int[RobotInventory.SLOT_COUNT];
    /** First container slot of the player inventory. */
    private int firstPlayerSlot;

    /** @param robot the robot whose storage is shown; {@code null} only when no entity could be resolved */
    public ContainerRobot(EntityPlayer player, EntityRobot robot) {
        this.player = player;
        this.robot = robot;
        this.robotInventory = robot == null ? new RobotInventory(null) : robot.getInventory();
        Arrays.fill(containerIndexOfRobotSlot, -1);
        addRobotSlots();
        firstPlayerSlot = this.inventorySlots.size();
        addPlayerSlots();
    }

    private void addRobotSlots() {
        for (int column = 0; column < RobotInventory.TOOL_SLOT_COUNT; ++column) {
            registerRobotSlot(
                new ToolSlot(
                    robotInventory,
                    RobotInventory.TOOL_FIRST_SLOT + column,
                    GRID_X + column * SLOT_SIZE,
                    TOOLBAR_Y),
                RobotInventory.TOOL_FIRST_SLOT + column);
        }
        if (robot != null && robot.hasBackpackPart()) {
            for (int row = 0; row < RobotInventory.BACKPACK_ROW_COUNT; ++row) {
                for (int column = 0; column < RobotInventory.BACKPACK_COLUMN_COUNT; ++column) {
                    int index = RobotInventory.BACKPACK_FIRST_SLOT + row * RobotInventory.BACKPACK_COLUMN_COUNT
                        + column;
                    registerRobotSlot(
                        new BackpackSlot(
                            robotInventory,
                            index,
                            GRID_X + column * SLOT_SIZE,
                            BACKPACK_Y + row * SLOT_SIZE),
                        index);
                }
            }
        }
        registerRobotSlot(
            new ChipSlot(robotInventory, RobotInventory.CHIP_SLOT, SPECIAL_SLOT_X, CHIP_Y),
            RobotInventory.CHIP_SLOT);
        registerRobotSlot(
            new BatterySlot(robotInventory, RobotInventory.BATTERY_BOX_SLOT, SPECIAL_SLOT_X, BATTERY_BOX_Y),
            RobotInventory.BATTERY_BOX_SLOT);
        registerRobotSlot(
            new CameraSlot(robotInventory, RobotInventory.CAMERA_SLOT, SPECIAL_SLOT_X, CAMERA_Y),
            RobotInventory.CAMERA_SLOT);
        registerRobotSlot(
            new BackpackPartSlot(robotInventory, RobotInventory.BACKPACK_PART_SLOT, SPECIAL_SLOT_X, BACKPACK_PART_Y),
            RobotInventory.BACKPACK_PART_SLOT);
        addBatteryCellSlots();
    }

    /** One slot per cell the installed box unlocks (R41); no box means no cell slot at all. */
    private void addBatteryCellSlots() {
        int cells = robot == null ? 0 : robot.getBatteryCellCount();
        int tier = robot == null ? 0 : robot.getBatteryBoxTier();
        for (int cell = 0; cell < cells && cell < RobotInventory.BATTERY_CELL_CAPACITY; ++cell) {
            int index = RobotInventory.BATTERY_CELL_FIRST_SLOT + cell;
            registerRobotSlot(
                new BatteryCellSlot(robotInventory, index, SPECIAL_SLOT_X, BATTERY_CELL_Y + cell * SLOT_SIZE, tier),
                index);
        }
    }

    /** Adds a robot slot and remembers which container index it got, so the shift-click paths can find it again. */
    private void registerRobotSlot(Slot slot, int robotSlot) {
        addSlotToContainer(slot);
        containerIndexOfRobotSlot[robotSlot] = this.inventorySlots.size() - 1;
    }

    private void addPlayerSlots() {
        for (int row = 0; row < PLAYER_GRID_ROWS; ++row) {
            for (int column = 0; column < RobotInventory.BACKPACK_COLUMN_COUNT; ++column) {
                int inventorySlot = PLAYER_GRID_FIRST_INVENTORY_SLOT + row * RobotInventory.BACKPACK_COLUMN_COUNT
                    + column;
                addSlotToContainer(
                    new Slot(
                        player.inventory,
                        inventorySlot,
                        GRID_X + column * SLOT_SIZE,
                        PLAYER_GRID_Y + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < RobotInventory.BACKPACK_COLUMN_COUNT; ++column) {
            addSlotToContainer(new Slot(player.inventory, column, GRID_X + column * SLOT_SIZE, HOTBAR_Y));
        }
    }

    /**
     * The screen only stays open while the robot still exists, is still loaded and is still within reach. The
     * check runs once per server tick through {@code ForgeHooks.canInteractWith}, so a robot that is destroyed
     * or whose chunk unloads closes the container instead of leaving it bound to a missing entity.
     */
    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        if (playerIn != player || robot == null || !robot.isEntityAlive() || robot.isDead) {
            return false;
        }
        if (!robot.addedToChunk || robot.worldObj != playerIn.worldObj) {
            return false;
        }
        return playerIn.getDistanceSqToEntity(robot) <= MAX_USE_DISTANCE_SQ;
    }

    /**
     * Closing republishes the channel one last time (R21 / R26) and hands back any battery the box no longer
     * covers (R41): a player can take the box onto the cursor in the very last click before pressing the close
     * key, and the cells must follow it.
     */
    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        robotInventory.syncChannel();
        robotInventory.returnInactiveBatteryCells(playerIn);
    }

    /**
     * Shift-click routing. Robot slots feed the player inventory; the player inventory feeds the machine slot
     * that accepts the stack, and storage stacks go to the backpack first and to the toolbar once it is full.
     * Only slots that are part of this layout are offered, so a body without a backpack never receives loot and
     * a body without a box never receives a cell.
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack stack = slot.getStack();
        ItemStack result = stack.copy();
        boolean moved;
        if (index < firstPlayerSlot) {
            moved = mergeItemStack(stack, firstPlayerSlot, this.inventorySlots.size(), true);
        } else if (mergeIntoRobotSlot(stack, RobotInventory.CHIP_SLOT)) {
            moved = true;
        } else if (mergeIntoRobotSlot(stack, RobotInventory.BATTERY_BOX_SLOT)) {
            moved = true;
        } else if (mergeIntoRobotSlot(stack, RobotInventory.CAMERA_SLOT)) {
            moved = true;
        } else if (mergeIntoRobotSlot(stack, RobotInventory.BACKPACK_PART_SLOT)) {
            moved = true;
        } else if (RobotInventory.acceptsStorage(stack)) {
            moved = mergeIntoRobotRange(stack, RobotInventory.BACKPACK_FIRST_SLOT, RobotInventory.BACKPACK_END_SLOT)
                || mergeIntoRobotRange(stack, RobotInventory.TOOL_FIRST_SLOT, RobotInventory.TOOL_END_SLOT);
        } else {
            moved = mergeIntoRobotRange(
                stack,
                RobotInventory.BATTERY_CELL_FIRST_SLOT,
                RobotInventory.BATTERY_CELL_END_SLOT);
        }
        if (!moved) {
            // Nothing in the container accepts this stack: leave it where it is instead of dropping it.
            return null;
        }
        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        // R41: a shift-click that pulled the box out must take the cells with it, into the player inventory.
        robotInventory.returnInactiveBatteryCells(playerIn);
        return result;
    }

    /**
     * R41: every click path can uncover a cell - pulling the box out, and dropping a smaller box into it. The
     * spilled batteries go to this player, or to the ground next to the body when the inventory is full.
     */
    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer playerIn) {
        ItemStack result = super.slotClick(slotId, clickedButton, mode, playerIn);
        robotInventory.returnInactiveBatteryCells(playerIn);
        return result;
    }

    /** Moves the stack into one robot slot when that slot exists in this layout and accepts it. */
    private boolean mergeIntoRobotSlot(ItemStack stack, int robotSlot) {
        int containerIndex = containerIndexOfRobotSlot[robotSlot];
        if (containerIndex < 0) {
            return false;
        }
        Slot target = (Slot) this.inventorySlots.get(containerIndex);
        if (target == null || !target.isItemValid(stack)) {
            return false;
        }
        return mergeItemStack(stack, containerIndex, containerIndex + 1, false);
    }

    /** Moves the stack into the first slot of a robot range that exists in this layout and accepts it. */
    private boolean mergeIntoRobotRange(ItemStack stack, int robotSlotFrom, int robotSlotTo) {
        for (int robotSlot = robotSlotFrom; robotSlot < robotSlotTo; ++robotSlot) {
            if (mergeIntoRobotSlot(stack, robotSlot)) {
                return true;
            }
        }
        return false;
    }
}
