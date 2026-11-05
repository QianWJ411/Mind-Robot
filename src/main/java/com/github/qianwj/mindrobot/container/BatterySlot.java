package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.inventory.RobotInventory;
import com.github.qianwj.mindrobot.item.ItemRobotBatteryBox;

/**
 * The battery box slot of the robot container (R38 / R39 / D16).
 * <p>
 * The robot only recognises the box, never a bare cell: a loose battery is refused here even though every GT
 * cell implements the IC2 electric item contract. The energy path then runs through the box - the robot asks
 * the box, and the box asks the cells inside it - so pulling the box is the only way to take the power source
 * away from a body. Exactly one box fits, as with every other part slot.
 */
public class BatterySlot extends Slot {

    private static final int BOX_STACK_LIMIT = 1;

    public BatterySlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemRobotBatteryBox;
    }

    @Override
    public int getSlotStackLimit() {
        return BOX_STACK_LIMIT;
    }

    /**
     * R41: pulling the box out takes the cells it unlocked with it. The slot is already empty when this runs, so
     * the inventory reports the new (zero) capacity and hands every remaining battery back to this player.
     */
    @Override
    public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
        super.onPickupFromSlot(player, stack);
        if (inventory instanceof RobotInventory) {
            ((RobotInventory) inventory).returnInactiveBatteryCells(player);
        }
    }
}
