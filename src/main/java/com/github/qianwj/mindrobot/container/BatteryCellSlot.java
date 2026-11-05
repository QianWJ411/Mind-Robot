package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.inventory.RobotInventory;
import com.github.qianwj.mindrobot.item.BatteryBoxSpec;

import ic2.api.item.IElectricItem;

/**
 * One battery cell of the robot container (R41).
 * <p>
 * The cells are part of the body, not of the box: the container adds one slot per cell the installed box
 * unlocks, so a 1x box shows a single cell slot and a 4x box shows four. A slot that the box no longer covers is
 * not added at all, and the inventory refuses a write into it even if a stale window still shows it.
 * <p>
 * The slot filters by the rated tier of the box, and it does so with a value handed in by the container rather
 * than by reading the box slot: the client side of a container never sees the stacks of an entity inventory, so
 * a check against the live inventory would refuse every cell there and the click would never reach the server.
 */
public class BatteryCellSlot extends Slot {

    private static final int CELL_STACK_LIMIT = 1;

    /** Rated tier of the box this cell belongs to, 0 when the box is gone. */
    private final int boxTier;

    public BatteryCellSlot(IInventory inventory, int index, int x, int y, int boxTier) {
        super(inventory, index, x, y);
        this.boxTier = boxTier;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof IElectricItem)) {
            return false;
        }
        int tier = ((IElectricItem) stack.getItem()).getTier(stack);
        return BatteryBoxSpec.fits(tier, boxTier);
    }

    @Override
    public int getSlotStackLimit() {
        return CELL_STACK_LIMIT;
    }

    /** Taking a cell out of an unlocked slot is always allowed; the box only decides what may go in. */
    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return inventory instanceof RobotInventory && ((RobotInventory) inventory).isActiveBatteryCell(getSlotIndex());
    }
}
