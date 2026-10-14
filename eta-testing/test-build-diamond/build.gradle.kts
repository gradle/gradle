val intro = tasks.register("intro") {
    doLast {
        println("Stages alternate between one task and 8 parallel tasks: a (2s), then b1-b8 (3s), then c (4s), then d1-d8 (3s), then e (2s).")
        println("Shows the ETA following the longest chain of dependent tasks through each stage.")
    }
}

// Sleeps for about the given time, varying by up to 0.5s per run
fun Task.sleeps(millis: Long) {
    dependsOn(intro)
    doLast { Thread.sleep(millis + (0L..500L).random()) }
}

tasks.register("a") { sleeps(2000) }
(1..8).forEach { i -> tasks.register("b$i") { sleeps(3000); dependsOn("a") } }
tasks.register("c") { sleeps(4000); dependsOn((1..8).map { "b$it" }) }
(1..8).forEach { i -> tasks.register("d$i") { sleeps(3000); dependsOn("c") } }
tasks.register("e") { sleeps(2000); dependsOn((1..8).map { "d$it" }) }
tasks.register("all") { dependsOn(intro, "e") }
