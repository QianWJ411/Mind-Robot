package com.github.qianwj.mindrobot.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.item.ItemRobotBackpack;

/**
 * The backpack part slot of the robot container (R14b / D17).
 * <p>
 * Only a backpack part may enter, exactly one. This slot is what unlocks the 54 backpack cells: the container
 * adds them only when a part is installed, and the inventory refuses a write into a locked cell, so a stale
 * window or a shift-click can never create storage that the body does not have.
 */
public class BackpackPartSlot extends Slot {

    private static final int PART_STACK_LIMIT = 1;

    public BackpackPartSlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemRobotBackpack;
    }

    @Override
    public int getSlotStackLimit() {
        return PART_STACK_LIMIT;
    }
}
