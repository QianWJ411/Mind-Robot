package com.github.qianwj.mindrobot.network;

import com.github.qianwj.mindrobot.link.ChannelMode;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Client to server request carrying the channel configuration typed into {@code ChipConfigGui}.
 * <p>
 * It is a request only: the server re-reads the held item and writes the NBT itself (D2), so a modified
 * client can at most configure the chip it is actually holding.
 */
public class ChipConfigMessage implements IMessage {

    private int channel;
    private byte mode;

    public ChipConfigMessage() {}

    public ChipConfigMessage(int channel, ChannelMode mode) {
        this.channel = channel;
        this.mode = (byte) (mode == null ? ChannelMode.PRIVATE : mode).ordinal();
    }

    public int getChannel() {
        return channel;
    }

    public ChannelMode getMode() {
        return ChannelMode.byId(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        channel = buf.readInt();
        mode = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(channel);
        buf.writeByte(mode);
    }
}
