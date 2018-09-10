val taskX by tasks.creating {
    doLast {
        println("taskX")
    }
}
val taskY by tasks.creating {
    doLast {
        println("taskY")
    }
}
val taskZ by tasks.creating {
    doLast {
        println("taskZ")
    }
}
taskX.dependsOn(taskY)
taskY.dependsOn(taskZ)
taskZ.shouldRunAfter(taskX)
