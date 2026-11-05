package com.github.qianwj.mindrobot.container;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.item.ItemRobotCamera;

/**
 * The camera slot of the robot container (R33).
 * <p>
 * Both camera kinds are accepted, because the camera is a hot swappable part: swapping the stack in this slot
 * is the whole installation. Exactly one camera fits; a stack limit of one also keeps the night vision rule
 * trivial, since there is never a second camera to decide about.
 */
public class CameraSlot extends Slot {

    private static final int CAMERA_STACK_LIMIT = 1;

    public CameraSlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemRobotCamera;
    }

    @Override
    public int getSlotStackLimit() {
        return CAMERA_STACK_LIMIT;
    }
}
