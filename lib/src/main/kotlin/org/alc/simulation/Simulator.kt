package org.alc.simulation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import kotlin.coroutines.resume

class Simulator(private val verbose: Boolean = false) {

    private data class Event(val time: Long, val name: String, val action: () -> Unit) : Comparable<Event> {
        val id = ++Event.id

        companion object {
            var id: Long = 0L
        }

        override fun compareTo(other: Event): Int = time.compareTo(other.time)
    }

    private data class PredicateWaiter(
        val name: String,
        val predicate: () -> Boolean,
        val continuation: CancellableContinuation<Unit>
    )

    var currentTime: Long = 0
        private set

    private val eventQueue = PriorityQueue<Event>()
    private val job = SupervisorJob()

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private val singleThreadDispatcher = newSingleThreadContext("SimulatorThread")
    private val scope = CoroutineScope(singleThreadDispatcher + job)
    private val eventSignal = Channel<Unit>(Channel.UNLIMITED)
    private val predicateWaiters = mutableListOf<PredicateWaiter>()

    fun now(): Long = currentTime

    // Private: schedules a plain block at simulated time
    private fun schedule(delay: Long, name: String, block: () -> Unit) {
        val eventTime = currentTime + delay
        val event = Event(eventTime, name, block)
        if (verbose) println("At time $currentTime, adding event ${event.name}-${event.id} for execution at ${event.time}")
        eventQueue.add(event)
        if (verbose) println("schedule $name: sending signal")
        eventSignal.trySend(Unit)
    }

    // Public: start a coroutine immediately
    fun process(name: String, delay: Long = 0, block: suspend SimulationContext.() -> Unit) {
        schedule(delay, "${name}-process") {
            scope.launch { SimulationContext(name).block() }
        }
    }


    fun run() = runBlocking {
        while (true) {
            verifyPredicateWaiters()

            if (verbose) println("Polling queue at time $currentTime")
            val event = eventQueue.poll()
            if (event != null) {
                if (verbose) println("Executing event ${event.name}-${event.id} at ${event.time}")
                currentTime = event.time
                event.action()
            } else {
                val active = scope.coroutineContext[Job]?.children?.any() ?: false
                if (!active && predicateWaiters.isEmpty()) break
                if (verbose) println("Waiting for signal at time $currentTime")
                eventSignal.receive()
                //println("Got signal")
                //delay(1)
            }
        }
    }

    fun shutdown() {
        singleThreadDispatcher.close()
    }


    private fun addPredicateWaiter(name: String, predicate: () -> Boolean, continuation: CancellableContinuation<Unit>) {
        if (verbose) println("$name: adding PredicateWaiter at time $currentTime")
        predicateWaiters.add(PredicateWaiter(name, predicate, continuation))
    }

    private fun verifyPredicateWaiters() {
        val toResume = mutableListOf<PredicateWaiter>()
        val nbPredicates = predicateWaiters.size
        val iterator = predicateWaiters.iterator()
        while (iterator.hasNext()) {
            val waiter = iterator.next()
            if (waiter.predicate()) {
                iterator.remove()
                toResume.add(waiter)
            }
        }

        if (verbose) println("There are ${toResume.size} waiters out of $nbPredicates to resume at time $currentTime")

        toResume.forEach {
            schedule(0, "${it.name}-waitUntil-resume") { it.continuation.resume(Unit) }
        }

    }

    // Inner class for simulation coroutine context
    inner class SimulationContext(val name: String) {

        suspend fun wait(delay: Long) {
            require(delay >= 0L) { "Invalid delay: $delay" }
            //verifyPredicateWaiters()
            suspendCancellableCoroutine { cont ->
                if (delay == 0L) {
                    cont.resume(Unit)
                } else {
                    schedule(delay, "${name}-wait") { cont.resume(Unit) }
                }
            }
        }

        suspend fun waitUntil(predicate: () -> Boolean) =
            suspendCancellableCoroutine { cont ->
                // verifyPredicateWaiters()
                addPredicateWaiter(name, predicate, cont)
                if (verbose) println("$name - waitUntil sending signal at time $now")
                eventSignal.trySend(Unit)
            }

        val now: Long
            get() = currentTime
    }
}




