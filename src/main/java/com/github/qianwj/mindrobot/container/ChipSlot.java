package com.github.qianwj.mindrobot.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.inventory.RobotInventory;
import com.github.qianwj.mindrobot.item.ItemFrequencyChip;

/**
 * The chip slot of the robot container (R21 / R37).
 * <p>
 * Only a frequency chip may enter, exactly one. The slot is the machine side counterpart of the single slot in
 * the brain helmet: whatever is installed here is the channel the robot answers on, and removing it takes the
 * robot off every channel (R26).
 * <p>
 * Every slot change republishes the channel as a second line of defence. The inventory setters already do it
 * for the placing and taking paths, but a click that empties the slot and a window that is closed right
 * afterwards must never leave a stale channel behind, so the slot itself re-derives it as well; the operation
 * is idempotent.
 */
public class ChipSlot extends Slot {

    private static final int CHIP_STACK_LIMIT = 1;

    public ChipSlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemFrequencyChip;
    }

    @Override
    public int getSlotStackLimit() {
        return CHIP_STACK_LIMIT;
    }

    @Override
    public void onSlotChanged() {
        super.onSlotChanged();
        if (inventory instanceof RobotInventory) {
            ((RobotInventory) inventory).syncChannel();
        }
    }
}
