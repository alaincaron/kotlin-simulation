package org.alc.simulation

import kotlinx.coroutines.runBlocking


fun main() {
    val sim = Simulator(true)
    var x = 0
    runBlocking {
        sim.process("Task1") {
            waitUntil { x > 0 }
            println("$name is terminating with x = $x at time $now")
        }
        sim.process("Task2") {
            println("$name is running event at time $now")
            wait(3)
            println("$name returned from wait at time $now")
            x = 1
            wait(4)
            println("$name terminated at time $now")
        }

        sim.process("Task3") {
            println("$name is running event at time $now")
            wait(1)
            println("$name returned from wait at time $now")
            wait(10)
            println("$name x = $x at time $now")
            println("$name terminated at time $now")
        }
        sim.run()
    }
}

fun main2() = runBlocking {

    val sim = Simulator()
    sim.process("Dummy") {
        println("[${sim.now()}] Before wait")
        wait(5)
        println("[${sim.now()}] After wait")
    }
    sim.run()
}
