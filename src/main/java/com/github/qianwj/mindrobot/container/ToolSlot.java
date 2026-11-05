package com.github.qianwj.mindrobot.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.inventory.RobotInventory;

/**
 * A toolbar slot of the robot container (R35 / R37).
 * <p>
 * Shift-clicking into the toolbar is allowed for ordinary items only: a frequency chip or a battery can never
 * be stored here, because those two belong to their own slots and must not end up in the toolbar by accident.
 */
public class ToolSlot extends Slot {

    public ToolSlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return RobotInventory.acceptsStorage(stack);
    }
}
