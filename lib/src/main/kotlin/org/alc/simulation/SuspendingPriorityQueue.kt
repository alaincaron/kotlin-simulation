package org.alc.simulation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.PriorityQueue

class SuspendingPriorityQueue<T>(
    comparator: Comparator<T>? = null,
    channelCapacity: Int = Channel.UNLIMITED
) {
    private val queue = if (comparator != null) {
        PriorityQueue(comparator)
    } else {
        PriorityQueue()
    }

    private val signal = Channel<Unit>(channelCapacity)
    @Volatile
    private var isClosed = false
    private var shutdownHook: ((List<T>) -> Unit)? = null

    fun add(item: T) {
        val shouldSignal: Boolean
        synchronized(this) {
            if (isClosed) throw IllegalStateException("Queue is closed")
            shouldSignal= queue.isEmpty()
            queue.add(item)
        }
        if (shouldSignal) {
            signal.trySend(Unit)
        }
    }

    private fun takeIfNotEmpty():T? {
        synchronized (this) {
            return if (queue.isEmpty()) null else queue.remove()
        }
    }

    suspend fun take(): T {
        while (true) {
            synchronized(this) {
                val t = takeIfNotEmpty()
                if (t != null) return t
                if (isClosed) throw ClosedReceiveChannelException("Queue closed and empty")
            }

            try {
                signal.receive()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ClosedReceiveChannelException) {
                val t = takeIfNotEmpty()
                if (t != null) return t
                throw e
            }
        }
    }

    /**
     * Register a callback that is invoked when `close()` is called.
     * The callback receives the remaining items in the queue.
     */
    fun onShutdown(callback: (List<T>) -> Unit) {
        synchronized(this) {
            if (isClosed) {
                throw IllegalStateException("Queue is already closed")
            }
            shutdownHook = callback
        }
    }

    /**
     * Close the queue and signal all waiting coroutines.
     * Invokes the shutdown hook with remaining elements.
     */
    fun close() {
        val drained: List<T>
        val hook: ((List<T>) -> Unit)?
        synchronized(this) {
            if (isClosed) return
            isClosed = true
            drained = mutableListOf<T>().apply {
                while (queue.isNotEmpty()) {
                    add(queue.remove())
                }
            }
            hook = shutdownHook
        }
        signal.close()
        hook?.invoke(drained)
    }

    fun drain(): List<T> = synchronized(this) {
        if (!isClosed) {
            throw IllegalStateException("Cannot drain a live queue. Call close() first.")
        }
        val result = mutableListOf<T>()
        while (queue.isNotEmpty()) {
            result.add(queue.remove())
        }
        result
    }

    fun isEmpty(): Boolean = synchronized(this) { queue.isEmpty() }
    fun size(): Int = synchronized(this) { queue.size }
}
