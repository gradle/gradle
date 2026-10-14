val intro = tasks.register("intro") {
    doLast {
        println("48 independent tasks take 1.5s each. Run with --max-workers=4 so only 4 run at a time.")
        println("Shows the ETA spreading the remaining work across the 4 parallel workers.")
    }
}

(1..48).forEach { i ->
    tasks.register("task$i") {
        dependsOn(intro)
        doLast { Thread.sleep(1500) }
    }
}
tasks.register("all") { dependsOn(intro, (1..48).map { "task$it" }) }
