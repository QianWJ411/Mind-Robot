package com.github.qianwj.mindrobot.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.item.ItemFrequencyChip;

/**
 * The chip slot of the robot container (R21 / R37).
 * <p>
 * Only a frequency chip may enter, exactly one. The slot is the machine side counterpart of the single slot in
 * the brain helmet: whatever is installed here is the channel the robot answers on.
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
}
