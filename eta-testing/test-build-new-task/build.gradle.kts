val intro = tasks.register("intro") {
    doLast {
        println("6 known tasks (2s each) run in parallel. Passing -PnewTask=<name> adds a task with that name (2s) that has never run before.")
        println("Shows a single task without a recorded duration making the ETA unavailable for the whole build.")
    }
}

(1..6).forEach { i ->
    tasks.register("known$i") {
        dependsOn(intro)
        doLast { Thread.sleep(2000) }
    }
}

val newTaskName = providers.gradleProperty("newTask").orNull
if (newTaskName != null) {
    tasks.register(newTaskName) {
        dependsOn(intro)
        doLast { Thread.sleep(2000) }
    }
}

tasks.register("all") {
    dependsOn(intro, (1..6).map { "known$it" })
    if (newTaskName != null) {
        dependsOn(newTaskName)
    }
}
