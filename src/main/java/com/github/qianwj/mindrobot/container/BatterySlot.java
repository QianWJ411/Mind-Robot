package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.inventory.RobotInventory;
import com.github.qianwj.mindrobot.item.GtBatteryBox;

/**
 * The battery box slot of the robot container (R38 / R39 / D16 / D17).
 * <p>
 * The slot takes a GregTech battery buffer block item - the machine whose Chinese name is "电池箱" - and nothing
 * else. A bare cell is refused here even though every GT cell implements the IC2 electric item contract: the body
 * gains its cell slots from the box, so the box is the only thing this slot may hold. Exactly one box fits, as
 * with every other part slot.
 * <p>
 * The box itself is never consumed by the robot and keeps its own inventory: it stays a real GregTech machine in
 * item form, and pulling it out of the body is the only way to take the power source away (R41).
 */
public class BatterySlot extends Slot {

    private static final int BOX_STACK_LIMIT = 1;

    public BatterySlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return GtBatteryBox.isBatteryBox(stack);
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
