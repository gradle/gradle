project("projectA") {
    tasks.register("taskX") {
        dependsOn(":projectB:taskY")
        doLast {
            println("taskX")
        }
    }
}

project("projectB") {
    tasks.register("taskY") {
        doLast {
            println("taskY")
        }
    }
}
