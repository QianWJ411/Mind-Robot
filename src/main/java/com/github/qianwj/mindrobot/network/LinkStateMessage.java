package com.github.qianwj.mindrobot.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Server to client snapshot of the current connection: whether a link is open, which robot entity the
 * camera should follow and which radial menu entries are allowed right now (D6).
 */
public class LinkStateMessage implements IMessage {

    private boolean linked;
    private int robotEntityId;
    private byte[] optionIds;

    public LinkStateMessage() {
        this(false, -1, new byte[0]);
    }

    public LinkStateMessage(boolean linked, int robotEntityId, byte[] optionIds) {
        this.linked = linked;
        this.robotEntityId = robotEntityId;
        this.optionIds = optionIds == null ? new byte[0] : optionIds;
    }

    public boolean isLinked() {
        return linked;
    }

    public int getRobotEntityId() {
        return robotEntityId;
    }

    public byte[] getOptionIds() {
        return optionIds;
    }

    /** Compares the visible state, used by the server to avoid sending identical snapshots every tick. */
    public boolean matches(LinkStateMessage other) {
        if (other == null || linked != other.linked || robotEntityId != other.robotEntityId) {
            return false;
        }
        if (optionIds.length != other.optionIds.length) {
            return false;
        }
        for (int i = 0; i < optionIds.length; i++) {
            if (optionIds[i] != other.optionIds[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        linked = buf.readBoolean();
        robotEntityId = buf.readInt();
        int size = buf.readUnsignedByte();
        optionIds = new byte[size];
        buf.readBytes(optionIds);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(linked);
        buf.writeInt(robotEntityId);
        buf.writeByte(optionIds.length);
        buf.writeBytes(optionIds);
    }
}
