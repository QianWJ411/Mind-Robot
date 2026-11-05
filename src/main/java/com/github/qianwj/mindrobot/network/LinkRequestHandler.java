package com.github.qianwj.mindrobot.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.qianwj.mindrobot.link.RobotLinkManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Server side handler for radial menu requests. Packets are decoded on the netty thread, so the actual
 * work is queued and executed at the start of the next server tick.
 */
public class LinkRequestHandler implements IMessageHandler<LinkRequestMessage, IMessage> {

    @Override
    public IMessage onMessage(LinkRequestMessage message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (player == null) {
            return null;
        }
        if (message.getAction() == LinkRequestMessage.ACTION_ENTER) {
            RobotLinkManager.queueEnterRequest(player);
        } else if (message.getAction() == LinkRequestMessage.ACTION_EXIT) {
            RobotLinkManager.queueExitRequest(player);
        }
        return null;
    }
}
