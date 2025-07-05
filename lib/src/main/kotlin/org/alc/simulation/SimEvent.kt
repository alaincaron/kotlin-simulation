package org.alc.simulation

import kotlin.coroutines.Continuation
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

// Base simulation event
sealed class SimEvent(open val time: Long) : Comparable<SimEvent> {
    val id = eventId++
    abstract suspend fun run()
    override fun compareTo(other: SimEvent): Int = time.compareTo(other.time)

    companion object {
        internal var eventId: Long = 0L
    }
}

data class ActionEvent(override val time: Long, val action: suspend () -> Unit) : SimEvent(time) {
    override suspend fun run() = suspendCoroutine { cont ->
        action.startCoroutine(Continuation(cont.context) { cont.resumeWith(it) })
    }
}

class SimulationHalt(message: String) : Exception(message)


data class PoisonPillEvent(override val time: Long, val reason: String) : SimEvent(time) {
    override suspend fun run() {
        throw SimulationHalt(reason)
    }
}

