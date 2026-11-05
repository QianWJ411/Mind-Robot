package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.github.qianwj.mindrobot.item.ItemFrequencyChip;

/**
 * The one slot container behind the brain helmet (R32, D13).
 * <p>
 * <b>Location convention.</b> The helmet is not a tile entity, so {@code EntityPlayer#openGui} carries the
 * location instead: {@code x} is the {@link net.minecraft.entity.player.InventoryPlayer} index of the
 * helmet stack (0..35, where 0..8 is the hotbar), {@code y} and {@code z} are unused and always 0.
 * {@link BrainHelmetGuiHandler#MAIN_HAND} (-1) is accepted as "the hotbar slot being held right now" and is
 * resolved to a concrete index before the container is built, so the server and the client always address
 * the same stack. The stack itself is read through {@link HelmetChipInventory}, never through a tile entity.
 * <p>
 * <b>Slot layout.</b> Slot 0 is the chip slot, slots 1..27 are the main inventory (InventoryPlayer 9..35)
 * and slots 28..36 are the hotbar (InventoryPlayer 0..8) - the usual chest arrangement, so the chip can be
 * moved in and out with the cursor. The helmet's own inventory slot stays visible, because hovering it is
 * how the player sees the channel tooltip update, but it is locked: the container is bound to that stack,
 * and moving it around mid-session would point every chip write at whatever item landed in that slot.
 */
public class ContainerBrainHelmet extends Container {

    /** Container slot index of the single chip slot. */
    public static final int CHIP_SLOT = 0;

    private static final int CHIP_SLOT_X = 80;
    private static final int CHIP_SLOT_Y = 26;
    private static final int PLAYER_GRID_COLUMNS = 9;
    private static final int PLAYER_GRID_ROWS = 3;
    private static final int PLAYER_GRID_FIRST_INVENTORY_SLOT = 9;
    private static final int PLAYER_GRID_X = 8;
    private static final int PLAYER_GRID_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    /** InventoryPlayer indices 36..39 are the armor slots and can never hold the edited helmet. */
    private static final int ARMOR_FIRST_INVENTORY_SLOT = 36;

    private final EntityPlayer player;
    /** InventoryPlayer index of the helmet; -1 when it could not be resolved. */
    private final int helmetSlot;
    /** Container slot index of the helmet's own inventory slot, or -1 when it is not in this container. */
    private final int lockedSlot;
    private final HelmetChipInventory chipInventory;

    public ContainerBrainHelmet(EntityPlayer player, int helmetSlot) {
        this.player = player;
        this.helmetSlot = resolveHelmetSlot(player, helmetSlot);
        this.lockedSlot = containerSlotFor(this.helmetSlot);
        this.chipInventory = new HelmetChipInventory(player, this.helmetSlot);

        addSlotToContainer(new ChipSlot(chipInventory, CHIP_SLOT, CHIP_SLOT_X, CHIP_SLOT_Y));
        for (int row = 0; row < PLAYER_GRID_ROWS; ++row) {
            for (int column = 0; column < PLAYER_GRID_COLUMNS; ++column) {
                int inventorySlot = PLAYER_GRID_FIRST_INVENTORY_SLOT + row * PLAYER_GRID_COLUMNS + column;
                addSlotToContainer(
                    createPlayerSlot(
                        inventorySlot,
                        PLAYER_GRID_X + column * SLOT_SIZE,
                        PLAYER_GRID_Y + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < PLAYER_GRID_COLUMNS; ++column) {
            addSlotToContainer(createPlayerSlot(column, PLAYER_GRID_X + column * SLOT_SIZE, HOTBAR_Y));
        }
    }

    /**
     * Resolves {@link BrainHelmetGuiHandler#MAIN_HAND} into the hotbar slot the player is holding; every other
     * value is used as it is.
     */
    public static int resolveHelmetSlot(EntityPlayer player, int helmetSlot) {
        if (helmetSlot >= 0 || player == null || player.inventory == null) {
            return helmetSlot;
        }
        return player.inventory.currentItem;
    }

    /** Maps an InventoryPlayer index to the container slot it was added under, or -1 when it is not added. */
    private static int containerSlotFor(int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= ARMOR_FIRST_INVENTORY_SLOT) {
            return -1;
        }
        if (inventorySlot >= PLAYER_GRID_FIRST_INVENTORY_SLOT) {
            return 1 + inventorySlot - PLAYER_GRID_FIRST_INVENTORY_SLOT;
        }
        // The hotbar is added after the three main rows.
        return 1 + PLAYER_GRID_ROWS * PLAYER_GRID_COLUMNS + inventorySlot;
    }

    private Slot createPlayerSlot(int inventorySlot, int x, int y) {
        if (inventorySlot == helmetSlot) {
            return new LockedSlot(player.inventory, inventorySlot, x, y);
        }
        return new Slot(player.inventory, inventorySlot, x, y);
    }

    /**
     * The screen stays open only while the tracked stack is still a remote helmet. The check also runs once
     * per server tick through {@code ForgeHooks.canInteractWith}, so a helmet that is dropped, confiscated or
     * lost on death closes the screen instead of leaving it bound to nothing.
     */
    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return playerIn == player && chipInventory.getHelmet() != null;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        // Belt and braces (D13): every slot change already wrote back, this covers a last click whose change
        // packet the server never processed.
        chipInventory.updateChannel();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack stack = slot.getStack();
        ItemStack result = stack.copy();
        boolean merged;
        if (index == CHIP_SLOT) {
            // Shift-clicking the chip out moves it into the player inventory without ever touching the locked
            // helmet slot.
            merged = mergeSkippingLocked(stack, CHIP_SLOT + 1, this.inventorySlots.size(), true);
        } else if (stack.getItem() instanceof ItemFrequencyChip) {
            merged = mergeItemStack(stack, CHIP_SLOT, CHIP_SLOT + 1, false);
        } else {
            return null;
        }
        if (!merged) {
            return null;
        }
        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    /** {@code mergeItemStack} cannot be told to skip a slot, so the range is split around the locked one. */
    private boolean mergeSkippingLocked(ItemStack stack, int from, int to, boolean reverse) {
        if (lockedSlot >= from && lockedSlot < to) {
            return mergeItemStack(stack, from, lockedSlot, reverse)
                || mergeItemStack(stack, lockedSlot + 1, to, reverse);
        }
        return mergeItemStack(stack, from, to, reverse);
    }

    /** The chip slot: exactly one frequency chip, nothing else can enter (R32). */
    private static final class ChipSlot extends Slot {

        private ChipSlot(IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return stack != null && stack.getItem() instanceof ItemFrequencyChip;
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    /** The helmet's own inventory slot: visible for its tooltip, but it cannot be moved while the screen is open. */
    private static final class LockedSlot extends Slot {

        private LockedSlot(IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }
}
