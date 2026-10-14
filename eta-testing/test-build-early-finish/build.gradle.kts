val intro = tasks.register("intro") {
    doLast {
        println("A chain of 8 tasks, each depending on the previous one. Each task takes 3s, or finishes early after 0.3s, picked at random on every run.")
        println("Shows the ETA dropping in jumps when tasks finish early, and holding when they take longer than their average.")
    }
}

(1..8).forEach { i ->
    tasks.register("step$i") {
        dependsOn(intro)
        if (i > 1) {
            dependsOn("step${i - 1}")
        }
        doLast { Thread.sleep(if ((1..2).random() == 1) 300L else 3000L) }
    }
}
tasks.register("all") { dependsOn(intro, "step8") }
