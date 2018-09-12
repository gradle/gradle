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
taskY.mustRunAfter(taskX)
