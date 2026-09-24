val taskX = tasks.register("taskX") {
    doLast {
        println("taskX")
    }
}
val taskY = tasks.register("taskY") {
    doLast {
        println("taskY")
    }
}
val taskZ = tasks.register("taskZ") {
    doLast {
        println("taskZ")
    }
}
taskX { dependsOn(taskY) }
taskY { dependsOn(taskZ) }
taskZ { shouldRunAfter(taskX) }
