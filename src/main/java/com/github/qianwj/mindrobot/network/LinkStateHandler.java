package com.github.qianwj.mindrobot.network;

import com.github.qianwj.mindrobot.client.ClientLinkState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Client side handler for connection snapshots. It only stores the state; the camera and the radial
 * menu read it on the client thread.
 */
public class LinkStateHandler implements IMessageHandler<LinkStateMessage, IMessage> {

    @Override
    public IMessage onMessage(LinkStateMessage message, MessageContext ctx) {
        ClientLinkState.update(message.isLinked(), message.getRobotEntityId(), message.getOptionIds());
        return null;
    }
}
