package com.github.qianwj.mindrobot.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.github.qianwj.mindrobot.MindRobot;
import com.github.qianwj.mindrobot.link.ChannelData;
import com.github.qianwj.mindrobot.link.ChannelMode;

/**
 * Frequency chip: the physical carrier of a channel (R13, R21, R22).
 * <p>
 * The chip owns the channel snapshot (D5): {@code mode} plus either a public number or a private name and
 * password (plain text, see {@link ChannelData}). It is configured through the client screen
 * {@code ChipConfigGui} (the screen only sends a request, the server writes the NBT) or through
 * {@code /mindrobot chip &lt;number&gt;} and {@code /mindrobot chip private &lt;name&gt; &lt;password&gt;}
 * for headless testing.
 * <p>
 * Installation (R32, D13): the chip is inserted into the helmet through the one slot container the helmet
 * opens with sneak + right click, so this item only carries and configures its own snapshot. The snapshot is
 * copied into the helmet verbatim - there is no player identity field that could be refreshed to the
 * installing player (R22).
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
     * it).
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            MindRobot.proxy.openChipConfigGui(player, stack);
        }
        return stack;
    }

    /**
     * Right click on a block. {@code onItemUseFirst} runs before the block itself, so a chest or a crafting
     * table cannot swallow the click and the chip still opens its screen.
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (player == null) {
            return false;
        }
        if (world.isRemote) {
            MindRobot.proxy.openChipConfigGui(player, stack);
        }
        return true;
    }

    /** Shows the public number or the private name; the private password is never rendered (D5). */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        ChannelData channel = getChannelData(stack);
        if (channel == null) {
            tooltip.add(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.mindrobot.chip.unconfigured"));
        } else {
            tooltip.add(EnumChatFormatting.DARK_GRAY + channelLabel(channel));
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
     * not holding. A private name or password is validated here (blank or too long is R26 "not configured"),
     * and the password never leaves this method in a chat line.
     *
     * @param mode     which kind of channel the chip should carry
     * @param channel  0..{@link ChannelData#MAX_CHANNEL}, used for {@link ChannelMode#PUBLIC}
     * @param name     private channel name, used for {@link ChannelMode#PRIVATE}
     * @param password private channel password in plain text, used for {@link ChannelMode#PRIVATE}
     * @return true when the chip was configured
     */
    public static boolean applyConfiguration(EntityPlayer player, ChannelMode mode, int channel, String name,
        String password) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        ChannelData data;
        if (mode == ChannelMode.PUBLIC) {
            if (!ChannelData.isValidChannel(channel)) {
                player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.invalid"));
                return false;
            }
            data = ChannelData.publicChannel(channel);
        } else {
            data = ChannelData.privateChannel(name, password);
            if (data == null) {
                player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.invalid_private"));
                return false;
            }
        }
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemFrequencyChip)) {
            player.addChatMessage(new ChatComponentTranslation("chat.mindrobot.chip.not_held"));
            return false;
        }
        setChannelData(held, data);
        syncHeldItem(player);
        player.addChatMessage(configuredMessage(data));
        return true;
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

    /** Localized label of a snapshot: the number for a public channel, the name for a private one. */
    private static String channelLabel(ChannelData channel) {
        if (channel.isPrivate()) {
            return StatCollector.translateToLocalFormatted("tooltip.mindrobot.chip.name", channel.getName());
        }
        return StatCollector.translateToLocalFormatted("tooltip.mindrobot.chip.channel", channel.getChannel());
    }

    private static ChatComponentTranslation configuredMessage(ChannelData data) {
        if (data.isPrivate()) {
            return new ChatComponentTranslation("chat.mindrobot.chip.configured_private", data.getName());
        }
        return new ChatComponentTranslation("chat.mindrobot.chip.configured_public", data.getChannel());
    }
}
