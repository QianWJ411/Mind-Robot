package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.entity.EntityRobot;
import com.github.qianwj.mindrobot.inventory.RobotInventory;

/**
 * The robot item container (R35 / D15): 65 robot slots plus the 36 player slots.
 * <p>
 * <b>Layout.</b> Slot 0 marks the start of the robot grid - the first row (0..8) is the toolbar the driver
 * operates while driving, next come the six backpack rows (9..62), then the chip slot (63) and the battery box
 * slot (64) on the right edge. The player inventory follows as usual: 27 main slots, then the hotbar.
 * Coordinates live here and the screen only paints the recesses, so the two never drift apart.
 * <p>
 * <b>Isolation.</b> Placement is refused by the slot classes themselves ({@link ToolSlot}, {@link BackpackSlot},
 * {@link ChipSlot}, {@link BatterySlot}); the shift-click paths below pass ranges that already match the rule,
 * because {@link Container#mergeItemStack} does not consult {@code isItemValid}. An item that cannot be placed
 * stays where it is - a shift-click that moves nothing returns {@code null} and the stack is untouched.
 * <p>
 * <b>Lifetime.</b> The screen is bound to a live, loaded robot: {@link #canInteractWith} fails once the robot is
 * destroyed or its chunk is unloaded, and Forge closes the container on the next server tick instead of leaving
 * a ghost screen behind.
 */
public class ContainerRobot extends Container {

    /** First container slot index of the player inventory (27 main slots, then the hotbar). */
    public static final int PLAYER_SLOT_FIRST = RobotInventory.SLOT_COUNT;
    public static final int PLAYER_SLOT_COUNT = 36;

    private static final int SLOT_SIZE = 18;
    private static final int GRID_X = 8;
    private static final int TOOLBAR_Y = 18;
    private static final int BACKPACK_Y = 40;
    private static final int SPECIAL_SLOT_X = 176;
    private static final int CHIP_Y = 18;
    private static final int BATTERY_Y = 40;
    private static final int PLAYER_GRID_FIRST_INVENTORY_SLOT = 9;
    private static final int PLAYER_GRID_ROWS = 3;
    private static final int PLAYER_GRID_Y = 164;
    private static final int HOTBAR_Y = 222;

    /** Same reach as a vanilla chest; the robot walking away closes the screen. */
    private static final double MAX_USE_DISTANCE_SQ = 64.0D;

    private final EntityPlayer player;
    private final EntityRobot robot;
    private final RobotInventory robotInventory;

    /** @param robot the robot whose storage is shown; {@code null} only when no entity could be resolved */
    public ContainerRobot(EntityPlayer player, EntityRobot robot) {
        this.player = player;
        this.robot = robot;
        this.robotInventory = robot == null ? new RobotInventory(null) : robot.getInventory();
        addRobotSlots();
        addPlayerSlots();
    }

    private void addRobotSlots() {
        for (int column = 0; column < RobotInventory.TOOL_SLOT_COUNT; ++column) {
            addSlotToContainer(
                new ToolSlot(
                    robotInventory,
                    RobotInventory.TOOL_FIRST_SLOT + column,
                    GRID_X + column * SLOT_SIZE,
                    TOOLBAR_Y));
        }
        for (int row = 0; row < RobotInventory.BACKPACK_ROW_COUNT; ++row) {
            for (int column = 0; column < RobotInventory.BACKPACK_COLUMN_COUNT; ++column) {
                int index = RobotInventory.BACKPACK_FIRST_SLOT + row * RobotInventory.BACKPACK_COLUMN_COUNT + column;
                addSlotToContainer(
                    new BackpackSlot(robotInventory, index, GRID_X + column * SLOT_SIZE, BACKPACK_Y + row * SLOT_SIZE));
            }
        }
        addSlotToContainer(new ChipSlot(robotInventory, RobotInventory.CHIP_SLOT, SPECIAL_SLOT_X, CHIP_Y));
        addSlotToContainer(new BatterySlot(robotInventory, RobotInventory.BATTERY_SLOT, SPECIAL_SLOT_X, BATTERY_Y));
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
     * Shift-click routing. Robot slots and the two special slots feed the player inventory; the player
     * inventory feeds the chip slot, the battery slot or the backpack and toolbar, in that order.
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
        if (index < PLAYER_SLOT_FIRST) {
            moved = mergeItemStack(stack, PLAYER_SLOT_FIRST, this.inventorySlots.size(), true);
        } else if (robotInventory.isItemValidForSlot(RobotInventory.CHIP_SLOT, stack)) {
            moved = mergeItemStack(stack, RobotInventory.CHIP_SLOT, RobotInventory.CHIP_SLOT + 1, false);
        } else if (robotInventory.isItemValidForSlot(RobotInventory.BATTERY_SLOT, stack)) {
            moved = mergeItemStack(stack, RobotInventory.BATTERY_SLOT, RobotInventory.BATTERY_SLOT + 1, false);
        } else if (RobotInventory.acceptsStorage(stack)) {
            moved = mergeItemStack(stack, RobotInventory.BACKPACK_FIRST_SLOT, RobotInventory.BACKPACK_END_SLOT, false)
                || mergeItemStack(stack, RobotInventory.TOOL_FIRST_SLOT, RobotInventory.TOOL_END_SLOT, false);
        } else {
            // Nothing in the container accepts this stack: leave it where it is instead of dropping it.
            return null;
        }
        if (!moved) {
            return null;
        }
        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }
}
