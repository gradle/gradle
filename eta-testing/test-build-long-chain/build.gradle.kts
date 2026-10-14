val intro = tasks.register("intro") {
    doLast {
        println("A chain of 20 tasks, each taking 1s and depending on the previous one, runs alongside 40 independent tasks that also take 1s each.")
        println("Shows the ETA following the 20s chain, even though there is twice as much independent work.")
    }
}

(1..20).forEach { i ->
    tasks.register("chain$i") {
        dependsOn(intro)
        if (i > 1) {
            dependsOn("chain${i - 1}")
        }
        doLast { Thread.sleep(1000) }
    }
}
(1..40).forEach { i ->
    tasks.register("wide$i") {
        dependsOn(intro)
        doLast { Thread.sleep(1000) }
    }
}
tasks.register("all") { dependsOn(intro, "chain20", (1..40).map { "wide$it" }) }
