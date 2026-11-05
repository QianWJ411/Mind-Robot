package com.github.qianwj.mindrobot.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.qianwj.mindrobot.item.ItemFrequencyChip;
import com.github.qianwj.mindrobot.link.ChannelMode;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Server side handler for {@link ChipConfigMessage}. Packets are decoded on the netty thread and the
 * inventory/NBT write happens on the server thread, so the work is queued and drained at the start of the
 * next server tick, exactly like the radial menu requests.
 */
public class ChipConfigHandler implements IMessageHandler<ChipConfigMessage, IMessage> {

    private static final Queue<Runnable> PENDING_REQUESTS = new ConcurrentLinkedQueue<Runnable>();

    @Override
    public IMessage onMessage(ChipConfigMessage message, MessageContext ctx) {
        final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (player == null) {
            return null;
        }
        final int channel = message.getChannel();
        final ChannelMode mode = message.getMode();
        PENDING_REQUESTS.add(new Runnable() {

            @Override
            public void run() {
                // The player may have logged out between decoding and the server tick.
                if (player.playerNetServerHandler != null) {
                    ItemFrequencyChip.applyConfiguration(player, channel, mode);
                }
            }
        });
        return null;
    }

    /** Drains queued chip configuration requests. Must run on the server thread. */
    public static void processPendingRequests() {
        Runnable request;
        while ((request = PENDING_REQUESTS.poll()) != null) {
            request.run();
        }
    }
}
