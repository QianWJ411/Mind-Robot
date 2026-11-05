package com.github.qianwj.mindrobot.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.inventory.RobotInventory;

/**
 * A backpack slot of the robot container (R35 / R37).
 * <p>
 * This is where the automatic pickup puts its loot (R36). Like the toolbar it refuses frequency chips and
 * batteries, so a special item can never be misplaced by a shift-click.
 */
public class BackpackSlot extends Slot {

    public BackpackSlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return RobotInventory.acceptsStorage(stack);
    }
}
