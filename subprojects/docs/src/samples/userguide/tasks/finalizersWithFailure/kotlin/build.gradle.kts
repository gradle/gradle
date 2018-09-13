val taskX by tasks.creating {
    doLast {
        println("taskX")
        throw RuntimeException()
    }
}
val taskY by tasks.creating {
    doLast {
        println("taskY")
    }
}

taskX.finalizedBy(taskY)
