val intro = tasks.register("intro") {
    doLast {
        println("4 tasks take 1s each, and are all finalized by a cleanup task that takes 6s. The all task only depends on the 4 tasks.")
        println("Shows the ETA including finalizers, which run after the tasks they finalize even though nothing depends on them.")
    }
}

val cleanup = tasks.register("cleanup") {
    dependsOn(intro)
    doLast { Thread.sleep(6000) }
}
(1..4).forEach { i ->
    tasks.register("quick$i") {
        dependsOn(intro)
        finalizedBy(cleanup)
        doLast { Thread.sleep(1000) }
    }
}
tasks.register("all") { dependsOn(intro, (1..4).map { "quick$it" }) }
