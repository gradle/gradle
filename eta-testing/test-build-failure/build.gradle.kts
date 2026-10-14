val intro = tasks.register("intro") {
    doLast {
        println("6 tasks take 5s each. A breaker task takes 2s and fails when run with -Pfail. 3 tasks that depend on the breaker take 8s each.")
        println("Shows what the ETA does when a task fails and the work depending on it never runs. Use --continue so the other tasks still run.")
    }
}

(1..6).forEach { i ->
    tasks.register("work$i") {
        dependsOn(intro)
        doLast { Thread.sleep(5000) }
    }
}
tasks.register("breaker") {
    dependsOn(intro)
    val fail = providers.gradleProperty("fail").isPresent
    doLast {
        Thread.sleep(2000)
        if (fail) {
            throw GradleException("Failed on purpose")
        }
    }
}
(1..3).forEach { i ->
    tasks.register("dependent$i") {
        dependsOn(intro, "breaker")
        doLast { Thread.sleep(8000) }
    }
}
tasks.register("all") { dependsOn(intro, (1..6).map { "work$it" }, (1..3).map { "dependent$it" }) }
