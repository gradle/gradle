val taskX by tasks.registering {
    doLast {
        println("taskX")
    }
}

val taskY by tasks.registering {
    doLast {
        println("taskY")
    }
}

taskX {
    dependsOn(taskY)
}
