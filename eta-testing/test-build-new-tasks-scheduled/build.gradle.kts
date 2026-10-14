// Each normal task runs a nested build of 2 more tasks. The nested build's tasks are only
// scheduled once the normal task executes, so the total task count grows during execution.

val intro = tasks.register("intro") {
    doLast {
        println("12 normal tasks each run a nested build of 2 more tasks, which are only scheduled once the normal task runs. Then a slow task (12-15s) runs.")
        println("Shows the ETA rising when the nested builds add work during execution.")
    }
}

// Base sleep of each normal task in ms, picked at random once (1-5s) so it is the same on every run
val baseSleepMillis = listOf(
    4888, 3636, 3484, 4281, 3047, 4015, 4069, 4849, 2044, 4031, 4395, 3256
)

(1..12).forEach { i ->
    val base = baseSleepMillis[i - 1].toLong()
    tasks.register<GradleBuild>("normal$i") {
        dir = file("extra-tasks")
        buildName = "extra$i"
        tasks = listOf("extraA", "extraB")
        dependsOn(intro)
        // Varies by 0-3s on each execution
        doFirst { Thread.sleep(base + (0L..3000L).random()) }
    }
}
// Runs after all the normal tasks, taking 12-15s, picked at random on each execution
tasks.register("slow") {
    dependsOn(intro)
    dependsOn((1..12).map { "normal$it" })
    doLast { Thread.sleep((12000L..15000L).random()) }
}
tasks.register("all") { dependsOn(intro, "slow") }
