tasks.register("taskX") {
    dependsOn(":project-b:taskY")
    doLast {
        println("taskX")
    }
}
