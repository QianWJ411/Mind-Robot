package com.github.qianwj.mindrobot.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.link.ChannelData;
import com.github.qianwj.mindrobot.link.ChannelMode;

/**
 * Frequency chip: the physical carrier of a channel number (R13, R21, R22).
 * <p>
 * The chip owns the NBT triple {@code channel} / {@code mode} / {@code owner} (D5). It is configured through
 * the client screen {@code ChipConfigGui} (the screen only sends a request, the server writes the NBT) or
 * through {@code /mindrobot chip &lt;channel&gt; [private|public]} for headless testing.
 * <p>
 * Installation: shift + right click while wearing a remote helmet copies the snapshot into the helmet and
 * consumes one chip. A helmet that already has a channel is overwritten on purpose; the old channel is only
 * reported in chat and no replacement chip is refunded (the chip was consumed by the installer).
 */
public class ItemFrequencyChip extends Item {

    public static final String ITEM_NAME = "frequency_chip";

    public ItemFrequencyChip() {
        setUnlocalizedName(ITEM_NAME);
        setTextureName(MindRobot.MODID + ":" + ITEM_NAME);
        setMaxStackSize(1);
    }

    /**
     * Plain right click in the air opens the configuration screen (client side only, the server never needs
     * it). Shift + right click installs the chip into the worn helmet (server side only).
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player == null) {
            return stack;
        }
        if (player.isSneaking()) {
            if (!world.isRemote) {
                installOnWornHelmet(stack, player);
            }
            return stack;
        }
        if (world.isRemote) {
            MindRobot.proxy.openChipConfigGui(player, stack);
        }
        return stack;
    }

    /**
     * Right click on a block. {@code onItemUseFirst} runs before the block itself, so a chest or a crafting
     * table cannot swallow the click and the chip still opens its screen. The sneak gesture is left alone
     * here: the install has to run on the server, so that click must travel as a normal block use.
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (player == null || player.isSneaking()) {
            return false;
        }
        if (world.isRemote) {
            MindRobot.proxy.openChipConfigGui(player, stack);
        }
        return true;
    }

    /**
     * Server side block variant of the install gesture. A sneak + right click in the air never reaches this
     * method (it goes through {@link #onItemRightClick}), so both paths exist on purpose.
     */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (player == null || !player.isSneaking() || world.isRemote) {
            return false;
        }
        installOnWornHelmet(stack, player);
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        ChannelData channel = getChannelData(stack);
        if (channel == null) {
            tooltip.add(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.chip.unconfigured"));
        } else {
            tooltip.add(
                EnumChatFormatting.DARK_GRAY
                    + StatCollector.translateToLocalFormatted("tooltip.mindrobot.chip.channel", channel.getChannel()));
            tooltip.add(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(
                    channel.isPrivate() ? "tooltip.mindrobot.mode.private" : "tooltip.mindrobot.mode.public"));
        }
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.chip.hint"));
    }

    /**
     * Applies a configuration request coming from the client screen or a command.
     * <p>
     * The item is looked up again on the server, so a forged packet cannot configure an item the player is
     * not holding, and the owner is always the server side player UUID.
     *
     * @param player  the player that requested the change
     * @param channel 0..{@link ChannelData#MAX_CHANNEL}
     * @param mode    private or public
     * @return true when the chip was configured
     */
    public static boolean applyConfiguration(EntityPlayer player, int channel, ChannelMode mode) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        if (!ChannelData.isValidChannel(channel)) {
            player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.invalid"));
            return false;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemFrequencyChip)) {
            player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.not_held"));
            return false;
        }
        setChannelData(held, new ChannelData(channel, mode, player.getUniqueID()));
        syncHeldItem(player);
        player.addChatMessage(
            new ChatComponentTranslation("chat.mindrobot.chip.configured", channel, modeTranslation(mode)));
        return true;
    }

    /** Copies this chip into the helmet currently worn by the player and consumes the chip. */
    private static void installOnWornHelmet(ItemStack chipStack, EntityPlayer player) {
        ChannelData chip = getChannelData(chipStack);
        if (chip == null) {
            player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.unconfigured"));
            return;
        }
        ItemStack helmet = player.getCurrentArmor(ItemRemoteHelmet.HEAD_ARMOR_SLOT);
        if (helmet == null || !(helmet.getItem() instanceof ItemRemoteHelmet)) {
            player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.no_helmet"));
            return;
        }
        ChannelData previous = ItemRemoteHelmet.getChannelData(helmet);
        ItemRemoteHelmet.setChannelData(helmet, chip);
        player.inventory.decrStackSize(player.inventory.currentItem, 1);
        syncHeldItem(player);
        if (previous != null) {
            player
                .addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.overwritten", previous.getChannel()));
        } else {
            player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.installed", chip.getChannel()));
        }
    }

    /** Reads the channel snapshot stored on this chip. */
    public static ChannelData getChannelData(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        return ChannelData.readFromNBT(stack.getTagCompound());
    }

    /** Writes (or clears, when {@code channel} is null) the channel snapshot of this chip. */
    public static void setChannelData(ItemStack stack, ChannelData channel) {
        if (stack == null) {
            return;
        }
        if (channel == null) {
            if (stack.hasTagCompound()) {
                ChannelData.removeFromNBT(stack.getTagCompound());
            }
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        channel.writeToNBT(tag);
    }

    /** Pushes the changed held stack to the owning client so the tooltip updates immediately. */
    private static void syncHeldItem(EntityPlayer player) {
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
        }
    }

    private static IChatComponent modeTranslation(ChannelMode mode) {
        return new ChatComponentTranslation(
            mode == ChannelMode.PUBLIC ? "tooltip.mindrobot.mode.public" : "tooltip.mindrobot.mode.private");
    }
}
