package com.github.qianwj.mindrobot.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Client to server request produced by the radial menu. It carries an intent only; the server decides
 * whether the connection is actually opened or closed (D2/D6).
 */
public class LinkRequestMessage implements IMessage {

    public static final byte ACTION_ENTER = 0;
    public static final byte ACTION_EXIT = 1;

    private byte action;

    public LinkRequestMessage() {}

    public LinkRequestMessage(byte action) {
        this.action = action;
    }

    public byte getAction() {
        return action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
    }
}
