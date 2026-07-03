package io.wifi.starrailexpress.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes a Watchdog deadlock between the Server thread and Netty Epoll IO thread.
 *
 * <h3>Deadlock Chain</h3>
 * <ol>
 *   <li>Server thread acquires the {@code SynchronizedRandomAccessList} lock
 *       (the {@code connections} list inside {@code ServerConnectionListener})</li>
 *   <li>During player join/disconnect processing, it calls
 *       {@code Connection.handleDisconnection} which invokes
 *       {@code method_60924} → {@code ChannelFuture.awaitUninterruptibly()}
 *       — a synchronous wait for the Netty IO thread to complete a write.</li>
 *   <li>Meanwhile, a new player connects; the Netty IO thread tries to add
 *       the new channel to the same {@code connections} list via
 *       {@code ServerConnectionListener$1.initChannel}</li>
 *   <li>The Netty IO thread is BLOCKED waiting for the list lock, held by
 *       the Server thread; the Server thread is WAITING for the Netty IO
 *       thread to process its write → classic deadlock</li>
 *   <li>The Watchdog detects the Server thread is stuck for 60s and kills
 *       the server</li>
 * </ol>
 *
 * <h3>Fix</h3>
 * Replace the blocking {@code awaitUninterruptibly()} call with a non-blocking
 * approach. The disconnect packet is already queued in the Netty pipeline;
 * we don't need to wait synchronously for it to be flushed. The channel will
 * be properly cleaned up by Netty's event loop.
 *
 * <p>This is the same fix approach used by Krypton mod, applied to the
 * specific 1.21.1 code path ({@code method_60924}) that Krypton 0.2.8
 * doesn't cover.</p>
 */
@Mixin(Connection.class)
public class ConnectionDeadlockFixMixin {

    @WrapOperation(
        method = "method_60924",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/channel/ChannelFuture;awaitUninterruptibly()Lio/netty/channel/ChannelFuture;"
        )
    )
    private ChannelFuture fixDisconnectDeadlock(ChannelFuture future, Operation<ChannelFuture> original) {
        // Do NOT block synchronously — this awaitUninterruptibly is the
        // direct cause of the Server thread ↔ Netty IO deadlock.
        // The disconnect has already been written to the channel;
        // returning the future unawaited allows Netty to process it
        // asynchronously without blocking the server thread.
        return future;
    }
}
