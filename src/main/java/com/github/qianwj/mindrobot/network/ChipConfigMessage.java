package com.github.qianwj.mindrobot.network;

import com.github.qianwj.mindrobot.link.ChannelMode;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Client to server request carrying the channel configuration typed into {@code ChipConfigGui} (R22 / D5).
 * <p>
 * A public request fills {@code channel} and leaves the credentials empty; a private request fills
 * {@code name} and {@code password} and leaves the number at -1. The password travels in plain text because
 * the model stores it in plain text (see {@code ChannelData}); it is never echoed back in chat.
 * <p>
 * It is a request only: the server re-reads the held item and writes the NBT itself (D2), so a modified
 * client can at most configure the chip it is actually holding.
 */
public class ChipConfigMessage implements IMessage {

    private byte mode;
    private int channel = -1;
    private String name = "";
    private String password = "";

    public ChipConfigMessage() {}

    public ChipConfigMessage(ChannelMode mode, int channel, String name, String password) {
        this.mode = (byte) (mode == null ? ChannelMode.PRIVATE : mode).ordinal();
        this.channel = channel;
        this.name = name == null ? "" : name;
        this.password = password == null ? "" : password;
    }

    public ChannelMode getMode() {
        return ChannelMode.byId(mode);
    }

    /** The public channel number, or -1 for a private request. */
    public int getChannel() {
        return channel;
    }

    /** The private channel name, or an empty string for a public request. */
    public String getName() {
        return name;
    }

    /** The private channel password in plain text, or an empty string for a public request. */
    public String getPassword() {
        return password;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
        channel = buf.readInt();
        name = ByteBufUtils.readUTF8String(buf);
        password = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
        buf.writeInt(channel);
        ByteBufUtils.writeUTF8String(buf, name);
        ByteBufUtils.writeUTF8String(buf, password);
    }
}
