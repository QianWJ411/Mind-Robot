package com.github.qianwj.mindrobot.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.github.qianwj.mindrobot.item.ItemFrequencyChip;
import com.github.qianwj.mindrobot.item.ItemRemoteHelmet;
import com.github.qianwj.mindrobot.link.ChannelData;

/**
 * The single chip slot of the brain helmet (R32, D13).
 * <p>
 * 1.7.10 has no "item that owns an inventory", so the slot is backed by the helmet stack itself: the chip
 * lives in the helmet NBT under {@value #CHIP_TAG}, next to the channel snapshot written by
 * {@link ItemRemoteHelmet#setChannelData}. Keeping the chip inside the helmet is what makes the slot
 * persistent - closing the screen and opening it again still shows the installed chip, and the player can
 * take it back out at any time.
 * <p>
 * The helmet stack is resolved from the player inventory on every access instead of being cached. If the
 * helmet is dropped, replaced, or the tracked slot no longer holds one, the slot goes empty instead of
 * writing NBT into whatever item happens to be there.
 * <p>
 * Every mutation mirrors the chip snapshot into the helmet, including the "no chip" case, which clears the
 * channel: a helmet without a chip must never keep a usable channel, otherwise it would still connect (D5
 * treats "not configured" as no channel at all). The mirror runs on both sides on purpose - the server is
 * authoritative, and the client needs the same view so that the chip slot and the helmet tooltip agree
 * between two synchronisation packets.
 */
public class HelmetChipInventory implements IInventory {

    /** Helmet NBT key of the chip stack. A sibling of the channel keys, never a child of them. */
    public static final String CHIP_TAG = "Chip";

    private static final int SLOT_COUNT = 1;
    private static final int SLOT_LIMIT = 1;
    private static final int NBT_COMPOUND = 10;
    /** InventoryPlayer indices 36..39 are the armor slots; the edited helmet is always a held stack. */
    private static final int ARMOR_FIRST_INVENTORY_SLOT = 36;

    private final EntityPlayer player;
    /** {@link net.minecraft.entity.player.InventoryPlayer} index of the helmet, or -1 for the main hand. */
    private final int helmetSlot;

    public HelmetChipInventory(EntityPlayer player, int helmetSlot) {
        this.player = player;
        this.helmetSlot = helmetSlot;
    }

    /**
     * The helmet stack this inventory writes into.
     *
     * @return the stack, or {@code null} when the tracked slot no longer holds a remote helmet
     */
    public ItemStack getHelmet() {
        if (player == null || player.inventory == null) {
            return null;
        }
        int slot = helmetSlot < 0 ? player.inventory.currentItem : helmetSlot;
        if (slot < 0 || slot >= ARMOR_FIRST_INVENTORY_SLOT) {
            return null;
        }
        ItemStack stack = player.inventory.getStackInSlot(slot);
        return stack != null && stack.getItem() instanceof ItemRemoteHelmet ? stack : null;
    }

    /**
     * Re-reads the chip and writes its snapshot into the helmet, clearing the channel when the slot is empty.
     * Called on every slot change and once more when the screen closes (D13).
     */
    public void updateChannel() {
        ItemStack helmet = getHelmet();
        if (helmet == null) {
            return;
        }
        ItemStack chip = readChip(helmet);
        ChannelData channel = chip == null ? null : ItemFrequencyChip.getChannelData(chip);
        ItemRemoteHelmet.setChannelData(helmet, channel);
    }

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index == 0 ? readChip(getHelmet()) : null;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index != 0 || count <= 0) {
            return null;
        }
        ItemStack current = getStackInSlot(0);
        if (current == null) {
            return null;
        }
        if (current.stackSize <= count) {
            setInventorySlotContents(0, null);
            return current;
        }
        ItemStack taken = current.splitStack(count);
        setInventorySlotContents(0, current);
        return taken;
    }

    /** Nothing in this inventory may be dropped on close: the chip belongs to the helmet. */
    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index != 0) {
            return;
        }
        ItemStack helmet = getHelmet();
        if (helmet == null) {
            return;
        }
        if (stack != null && !(stack.getItem() instanceof ItemFrequencyChip)) {
            // Only the chip slot rule from the container should reach this, but a stray path must not be able
            // to turn the helmet into a bag for arbitrary items.
            return;
        }
        writeChip(helmet, stack);
        updateChannel();
    }

    /** Slot changes land here through {@code Slot#onSlotChanged}; the NBT write is the same mirror again. */
    @Override
    public void markDirty() {
        updateChannel();
    }

    @Override
    public String getInventoryName() {
        return "container.mindrobot.helmet";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return SLOT_LIMIT;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer playerIn) {
        return getHelmet() != null;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return index == 0 && stack != null && stack.getItem() instanceof ItemFrequencyChip;
    }

    /** Reads the chip stored in the helmet, ignoring corrupted or foreign tags. */
    private static ItemStack readChip(ItemStack helmet) {
        if (helmet == null || !helmet.hasTagCompound()) {
            return null;
        }
        NBTTagCompound tag = helmet.getTagCompound();
        if (!tag.hasKey(CHIP_TAG, NBT_COMPOUND)) {
            return null;
        }
        ItemStack chip = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(CHIP_TAG));
        return chip != null && chip.getItem() instanceof ItemFrequencyChip ? chip : null;
    }

    /** Stores (or removes, when {@code chip} is null) the chip stack inside the helmet, keeping other tags. */
    private static void writeChip(ItemStack helmet, ItemStack chip) {
        NBTTagCompound tag = helmet.getTagCompound();
        if (chip == null) {
            if (tag != null) {
                tag.removeTag(CHIP_TAG);
            }
            return;
        }
        if (tag == null) {
            tag = new NBTTagCompound();
            helmet.setTagCompound(tag);
        }
        tag.setTag(CHIP_TAG, chip.writeToNBT(new NBTTagCompound()));
    }
}
