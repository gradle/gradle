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

taskX { finalizedBy(taskY) }
