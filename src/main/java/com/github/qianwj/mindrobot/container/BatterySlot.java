package com.github.qianwj.mindrobot.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import ic2.api.item.IElectricItem;

/**
 * The battery box slot of the robot container (R38 / D16).
 * <p>
 * The acceptance rule is the one GregTech machines use for their battery slots: the item must implement
 * {@link IElectricItem}. Testing the IC2 interface instead of a GT5U class keeps this slot working across GT
 * refactors, and every GT battery is an {@code IElectricItem}. Exactly one battery fits, as in a GT machine.
 */
public class BatterySlot extends Slot {

    private static final int BATTERY_STACK_LIMIT = 1;

    public BatterySlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IElectricItem;
    }

    @Override
    public int getSlotStackLimit() {
        return BATTERY_STACK_LIMIT;
    }
}
